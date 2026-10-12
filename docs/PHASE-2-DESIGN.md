# Phase 2 — Design: LeadOrbit (Java / Spring Boot)

> Target repo: `/home/user/Documents/java-projects/LeadOrbit` · Package: `com.leadorbit`
> This document is the design contract for Phase 3. It is written to be portfolio-safe
> (no client data, credentials, or production identifiers).

---

## 0. Decisions locked in Phase 1

| # | Decision |
|---|---|
| D1 | **Maven** build, Java 21 (LTS), Spring Boot 3.3.x |
| D2 | Scope = **CRM + billing focus**; projects/tasks/EOD are optional later modules (deferred to a documented backlog). |
| D3 | **Subscriber = Client + plan/service** from the catalog; a client may hold multiple subscriptions. |
| D4 | **File storage = pluggable `FileStorage` port with a local-disk adapter** (non-public paths, authorized streaming). |
| D5 | **Legacy migration = documented mapping + synthetic seed**; no runtime auto-migration of client data. |
| D6 | **GST fully configurable + assumptions documented**; items needing a tax professional are flagged. |
| D7 | **API redesign allowed**; all breaking changes documented in the migration report. |

Build/verification environment: no local JDK; use Dockerized `maven:3.9-eclipse-temurin-21`
for build/test and `postgres:16` for integration tests (Testcontainers or a compose service).

---

## 1. Architecture overview

**Modular monolith, package-by-feature, strict layering.**

```
api            controllers, request/response DTOs, mappers, OpenAPI docs
application    services / use-cases, transaction boundaries, orchestration
domain         entities, value objects, enums, state machines, policies, domain events
infrastructure adapters: JPA repositories, mail, PDF, file storage, audit, clock
```

**Dependency rule:** `api → application → domain`; `infrastructure` implements ports declared
by `domain`/`application`. No `api` type leaks into `domain`; `domain` depends on no framework
except JPA annotations on entities (kept pragmatic for this project).

**Packages**

```
com.leadorbit
├── common        errors, ApiResponse envelope, PageResult, Money, Clock, Ids, correlation
├── config        SecurityConfig, OpenApiConfig, JpaConfig, SchedulingConfig, AsyncConfig
├── security      JwtService, RealUserDetailsService, PermissionEvaluator, bootstrap, CurrentUser
├── identity      users, roles, permissions, assignments, refresh tokens
├── audit         AuditEvent, AuditService, AuditAspect, audit query API
├── catalog       services (HSN/SAC, tax class), tax-rate config
├── crm           companies, contacts, leads, sources, pipeline stages, activities, consent
├── sales         proposals, meetings, meeting notes
├── delivery      (deferred) projects, tasks, time logs
├── billing       invoices, line items, numbering, GST engine, credit/debit notes, PDF
├── subscription  plans, subscriptions, billing periods, scheduler, job runs
├── payment       submissions, proofs, verification, allocation
├── notification  templates, outbox, dispatcher, retry, delivery log
├── marketing     (phase 4) campaigns, attribution, segments, email templates
└── reporting     dashboards, exports (authz-scoped)
```

**Conventions**
- IDs: `Long` (`bigint`) internally, serialized as numbers (drop the legacy string-id artifact);
  audit event ids are `UUID`.
- Money: `BigDecimal`, scale 2 for INR; a `Money` value object wraps currency + rounding mode.
  Never use `double`.
- Time: `Instant`/`OffsetDateTime`; DB `timestamptz`; all audit/most storage in UTC.
- Response envelope: `{ success, timestamp, message, data, errors? }` (matches legacy so the
  frontend story is easy to tell) and a separate `PageResult<T>` for lists.
- Errors: typed `ApiException` hierarchy (`NotFound`, `BadRequest`, `Conflict`, `Unauthorized`,
  `Forbidden`, `Validation`); a `@RestControllerAdvice` maps them; **never** leak stack traces
  or raw DB messages; validation failures return field-level details.
- Validation: Bean Validation on DTOs (`@Valid`), strict unknown-field rejection where possible.
- Transactions: `@Transactional` on application services; financial service methods declare
  propagation/isolation; optimistic locking (`@Version`) on `Invoice`, `Subscription`, `Payment`.

---

## 2. Security architecture

### 2.1 Authentication
- **Password hashing:** BCrypt (cost 12) via `PasswordEncoder`; password never returned/logged.
- **Tokens:** short-lived access JWT (15 m) + persisted, **rotating** refresh token (7 d).
  - Access token carries `sub`, `email`, `perm` (resolved permission codes) and `ver`.
  - Refresh tokens are stored **hashed** in `refresh_tokens`; rotation invalidates the old row
    and records `replaced_by`; reuse of a rotated token revokes the family (theft detection).
  - Logout revokes the refresh token; a `ver` claim invalidates all access tokens after
    credential/permission changes.
- **Login safety:** rate limiting + temporary lockout per identity/IP on repeated failures.
- **Password reset:** single-use, short-TTL token (same pattern as source), email only.

### 2.2 Authorization — dynamic RBAC
- Permission resolution for a user = `union(user_roles → role_permissions)` **+**
  explicit `user_permissions` (grant overrides) **−** explicit revokes.
- Enforcement in two places:
  1. URL/method rules in `SecurityFilterChain` (coarse, e.g. `/api/v1/users/**` needs a perm),
  2. `@PreAuthorize("hasPermission(#id,'invoice','issue')")` at controllers/services (fine).
- **Super-admin boundary:** roles carry a `rank`. A grant may not exceed the actor's rank
  unless the actor holds `user.manage_permissions` **and** a `system` super-admin. A user may
  not grant a permission they do not themselves hold (no escalation), and may not edit their
  own roles beyond allowed scope.
- **Bootstrap:** first super-admin created once via a guarded startup routine (only when no
  super-admin exists) from env; role/permission catalog seeded by Flyway; every subsequent
  change is audited.
- **Cache:** permission sets cached per user with explicit eviction on any role/permission/
  assignment change; cache key includes `ver`.
- **IDOR:** records are scoped by owner/company where the legacy app did (EOD/report) and by
  permission everywhere else; ids are opaque and always re-checked server-side.

### 2.3 Other controls
- CORS restricted to configured origins; Helmet-equivalent security headers (Spring Security).
- Rate limiting on auth/upload endpoints; upload MIME + size + content sniffing.
- Secrets via env/secret store only; `.env.example` ships placeholders.
- Structured JSON logs with a correlation/request id; a redaction filter strips tokens,
  passwords, and PII from logs and exception messages.

---

## 3. Role-Based Access Control model

**Tables:** `roles`, `permissions`, `role_permissions`, `user_roles`, `user_permissions`.

- `permissions(code, module, action, description)` — code convention `module.action`, e.g.
  `invoice.read`, `invoice.create`, `invoice.issue`, `invoice.cancel`, `payment.verify`,
  `payment.record`, `subscription.manage`, `user.manage_permissions`, `role.manage`,
  `audit.read`, `catalog.manage`, `crm.manage`, `report.export`.
- `roles(code, name, rank, system, description)` — seeded `SUPER_ADMIN` (system, highest rank),
  `ADMIN`, `ACCOUNTANT`, `SALES`, `VIEWER` (examples).
- `user_permissions(user_id, permission_id, effect ∈ {GRANT, REVOKE})` — explicit overrides.

**Resolution algorithm**
```
perms(u) = ( ⋃_{r ∈ roles(u)} perms(r) ∪ {granted direct} ) − {revoked direct}
```
Effective-permission caching with versioning; all mutations write an audit event and bump the
user's `ver`.

**Admin APIs (all `@PreAuthorize` guarded, all audited):** create/edit roles, assign perms to
roles, assign roles to users, grant/revoke direct perms, list effective permissions.

---

## 4. GST tax engine

### 4.1 Configuration (not hard-coded)
- `seller_profiles`: legal name, GSTIN, state code, registered address, logo, default flag.
- `services`: `hsn_sac`, `tax_class` enum `TAXABLE | EXEMPT | NIL_RATED | ZERO_RATED | NON_GST`,
  `default_gst_rate`, `unit`, `default_rate`, `tax_inclusive`.
- `gst_rates`: configurable rate rows (exact rates and effective dates), seeded with the
  common Indian slabs as placeholders clearly marked "verify with a tax professional".

### 4.2 Calculation algorithm
Inputs per line: `qty`, `rate`, `discount`, `taxClass`, `gstRate`, `taxInclusive`,
`placeOfSupplyState`, `sellerState`.
```
1. gross      = qty × rate
2. discount   = eligible discount (line- or invoice-level, allocated pro-rata)
3. taxable    = gross − discount                       (exclusive case)
   inclusive : taxable = round( gross − discount ) × 100 / (100 + rate)
4. tax split:
   if supplier state == place of supply → intra-state → CGST = rate/2, SGST = rate/2
   else                                  → inter-state → IGST = rate
   taxClass EXEMPT/NIL/NON_GST → tax = 0
5. line tax   = taxable × (rate/100)  (+ cess if configured)
6. invoice totals = Σ taxable, Σ CGST, Σ SGST, Σ IGST, Σ cess
7. roundOff   = configured rounding (e.g. nearest rupee) applied once at invoice level
8. grandTotal = Σ taxable + Σ CGST + Σ SGST + Σ IGST + cess + roundOff
```
- **Place of supply** is captured on the invoice (state code) and defaulted from the customer;
  intra/inter is derived by comparing with the seller's state from the frozen seller profile.
- **Immutability:** on issue, the invoice stores a JSONB snapshot of seller, customer, and
  line/tax details, plus concrete `cgst/sgst/igst/cess` columns and totals. Later catalog or
  config edits cannot alter an issued invoice.

### 4.3 Numbering
- `invoice_number_sequences(prefix, financial_year, next_number, padding, seller_id)`.
- Assignment on **issue** inside the same transaction using `SELECT … FOR UPDATE` (row lock),
  so numbers are gapless-per-sequence, unique, and financial-year aware (Indian FY Apr–Mar).
- Format is configurable (e.g. `INV/<FY>/000123`); uniqueness enforced by a DB constraint.
- Document types: `TAX_INVOICE`, `CREDIT_NOTE`, `DEBIT_NOTE`, `BILL_OF_SUPPLY`, `PROFORMA`.

### 4.4 Flagged for tax professional review
Exact HSN/SAC codes, applicable rate per service, whether any supply is exempt/composition,
reverse-charge applicability, TCS/TDS, e-invoice/IRN requirements, and rounding policy.

---

## 5. Billing domain

### 5.1 Entities
- `Invoice` (aggregate root): number, type, status, client, seller profile, place of supply,
  currency, totals columns, `round_off`, `amount_paid`, snapshot JSONB, `issued_at`, `due_date`,
  `version`.
- `InvoiceLineItem`: service/description, `hsn_sac`, qty, unit, rate, discount, taxable value,
  tax class, gst rate, cgst/sgst/igst/cess, line total, order.
- `InvoiceEvent`: append-only status/transition history with actor and reason.
- `CreditDebitNote` modelled as an `Invoice` with `invoice_type` + `original_invoice_id`.

### 5.2 Invoice state machine
```
DRAFT ──issue──▶ ISSUED ──(allocated payment)-─▶ PARTIALLY_PAID ──▶ PAID
                    │                                   │
                    ├── due date passes ──▶ OVERDUE ◀───┘   (OVERDUE ⇄ PARTIALLY_PAID)
                    └── cancel ──▶ CANCELLED
DRAFT ──delete──▶ (hard/soft delete permitted)
ISSUED/…: NOT editable, NOT deletable. Corrections require CREDIT_NOTE; void via CANCELLED.
```
- `DRAFT` is the only editable/deletable state.
- `ISSUED` freezes the snapshot and assigns the number.
- Status is **derived** from allocations (never client-writable); `OVERDUE` is a scheduled
  derivation from `due_date` + outstanding.
- Allowed transitions are implemented as a domain transition table and enforced in one place.

### 5.3 Payment allocation
- A verified payment allocates to a single invoice (MVP); allocation recomputes
  `amount_paid` and derived status atomically under a row lock.
- Overpayment is rejected unless an explicit policy allows credit balance (configurable);
  partial payments supported.

### 5.4 PDF & history
- Server-side PDF (OpenPDF/Flybox/iText-OSS) generated from the frozen snapshot; downloadable
  via an authorized endpoint; email attachment or secure link (expiring, signed token).
- `InvoiceEvent` preserves full status history and traceability.

---

## 6. Subscription domain

### 6.1 Entities
- `Plan`: name, linked catalog service(s), amount, `billing_interval=MONTHLY`, `tax_inclusive`,
  active.
- `Subscription`: client, plan, status, `start_date`, `end_date`, `anchor_day`, `zone_id`,
  `next_billing_date`, `price_override`, `version`.
- `SubscriptionBillingPeriod`: subscription, `period_start`, `period_end`, amount, status
  `PENDING | INVOICED | PAID | SKIPPED`, `invoice_id`; **unique(subscription_id, period_start)**.
- `BillingJobRun`: job name, `scheduled_for`, timestamps, status, counts, error;
  **unique(job_name, scheduled_for)** for restart-safe idempotency.
- `BillingJobItem`: run, subscription, period_start, outcome, message;
  **unique(subscription_id, period_start)**.

### 6.2 Subscription state machine
```
DRAFT ──activate──▶ ACTIVE ⇄ PAUSED
ACTIVE ──cancel──▶ CANCELLED      ACTIVE ──end date──▶ EXPIRED
ACTIVE ──change plan──▶ ACTIVE (prorated adjustment policy)
```
- **Proration** on plan change/mid-cycle cancel: configurable policy
  (`NONE` default | `PRORATE_BY_DAYS`), documented.
- Month-end/leap handling: billing anchor day clamped to the target month's last day
  (same rule the legacy generator implemented, but made explicit and unit-tested).

### 6.3 Scheduler & idempotency
- Spring `@Scheduled` (or Quartz) runs a **billing job** that, inside a transaction per
  subscription: ensures the due period row exists (unique key), creates the invoice (ISSUED or
  DRAFT per policy), links `period → invoice`, and enqueues notifications.
- **Idempotency:** the unique period key + `BillingJobRun` unique key mean retries after a
  crash/restart cannot double-invoice.
- **Distributed safety:** Postgres advisory lock (mirrors the proven legacy approach) so
  multiple instances don't double-run.
- **Failure recovery:** each item records outcome; failed items are retryable via a
  “reprocess run/period” admin endpoint; job runs are queryable.

### 6.4 Notifications
- On invoice generated, before due (configurable reminder), overdue, payment received,
  payment rejected, subscription changed/cancelled. All go through the outbox (§9).

---

## 7. Payment domain (manual verification)

### 7.1 Entities
- `Payment`: invoice, client, amount, method (`UPI, NEFT, RTGS, IMPS, CHEQUE, CASH, OTHER`),
  `reference_no`, `paid_on`, status, notes, `submitted_by`, `verified_by`, `verified_at`,
  rejection reason, `idempotency_key`.
- `PaymentProof`: payment, `storage_key`, original filename, content type, size, `sha256`,
  uploaded_by/at.
- `PaymentEvent`: append-only submission/decision history.

### 7.2 State machine
```
SUBMITTED ──▶ UNDER_REVIEW ──▶ VERIFIED
                     │
                     ├──▶ REJECTED
                     └──▶ NEEDS_INFO ──(resubmit)──▶ UNDER_REVIEW
```
- A screenshot/reference is **not** proof until an authorized admin (permission
  `payment.verify`) verifies it.
- Only `VERIFIED` affects invoice balance/status; done in one transaction with the audit event.
- Admins may also **record a payment directly** (`payment.record`) which enters `VERIFIED`
  with `verified_by = actor` (audited) or `UNDER_REVIEW` per policy.

### 7.3 Validation & integrity
- `amount > 0`; amount ≤ outstanding unless credit-balance policy enabled.
- Duplicate guards: unique `(invoice_id, reference_no)` (partial, non-null) and unique
  `idempotency_key`; concurrent verification serialized by invoice row lock.
- **Proof files** stored via `FileStorage` (local adapter now); never public — served only via
  an authorized controller that streams from a private path; validate type/size/extension and
  compute `sha256`.
- Rejections/NEEDS_INFO send an explanation email; VERIFIED sends a confirmation.

---

## 8. Audit logging

- `audit_events` append-only: `id UUID`, `occurred_at timestamptz (UTC)`, `actor_user_id`,
  `actor_label`, `action`, `entity_type`, `entity_id`, `outcome`, `correlation_id`,
  `request_id`, `ip`, `user_agent`, `summary`, `before_json`, `after_json`, `schema_version`.
- Captured via a Spring AOP aspect on annotated service methods + explicit calls for domain
  events (auth success/failure, invoice issue/cancel/credit-note, payment submit/verify/reject,
  role/permission changes, GST/numbering config changes, audit access).
- **Not editable/deletable via any API;** the DB grants for `audit_events` are `INSERT/SELECT`
  only (revoke `UPDATE/DELETE` from the app role).
- **Fail-closed:** critical financial operations run in a transaction that also writes the
  audit event; if the audit insert fails, the business transaction rolls back.
- **Redaction:** never store passwords, tokens, keys, or full PII; before/after diffs are
  minimal and field-allowlisted.
- Retention/archival policy configurable; access to audit data requires `audit.read` and is
  itself audited.

---

## 9. Notification / email architecture

- **Transactional vs marketing separated**: distinct template namespaces and a consent gate on
  marketing only; invoice recipients are **never** auto-enrolled in campaigns.
- `email_templates(code, version, subject, html, text, is_marketing)`; reusable HTML with
  plain-text alternative.
- `notification_outbox(idempotency_key unique, type, recipient, template, payload jsonb,
  status PENDING/SENT/FAILED, attempts, next_attempt_at, last_error, created/sent_at)`.
- Async dispatcher polls the outbox with bounded attempts + exponential backoff; delivery
  result recorded in `email_delivery_log` (provider message id where available).
- Idempotency keys derive from business identity (e.g. `invoice-issued:<invoiceId>`,
  `payment-verified:<paymentId>`) so job retries don't duplicate mail.
- **Never send inside a long financial transaction** — the transaction writes the outbox row;
  the worker sends.
- SMTP/provider configured via env; external mail **mocked** in tests (e.g. a capturing sender).

---

## 10. CRM domain

- `companies`, `contacts` (with `marketing_opt_in`, `consent_at`, `unsubscribed_at`).
- `leads`: contact/company, `source_id`, `stage_id`, `owner_id`, status, contact fields.
- `lead_sources(code, name, channel ∈ {WEBSITE, CAMPAIGN, REFERRAL, SOCIAL, EVENT, OTHER})`.
- `pipeline_stages(code, name, sort_order, is_won, is_lost)` — configurable pipeline.
- `activities`: tasks/reminders/notes/interactions with due/complete times and assignee → the
  lead/customer timeline.
- Preserved rules from Phase 1: invoiceable lead statuses, lead-with-invoices delete guard,
  lead→client conversion on payment beyond advance, one meeting per lead/client.
- **Phase 4 marketing**: `campaigns`, `campaign_leads` attribution, `email_templates`,
  segmentation, dashboards, exportable (authz-scoped) reports.

---

## 11. Database schema (Flyway V1) — table map

Managed by Flyway; PostgreSQL 16; `timestamptz`; `numeric` money; real enums/checks; soft
delete (`deleted_at`) only where history matters.

| Group | Tables |
|---|---|
| Identity | `users`, `roles`, `permissions`, `role_permissions`, `user_roles`, `user_permissions`, `refresh_tokens` |
| Audit | `audit_events` (append-only) |
| Catalog | `services`, `gst_rates` |
| CRM | `companies`, `contacts`, `lead_sources`, `pipeline_stages`, `leads`, `activities`, `consents` |
| Sales | `proposals`, `meetings`, `meeting_notes` |
| Billing | `seller_profiles`, `invoices`, `invoice_line_items`, `invoice_number_sequences`, `invoice_events` |
| Subscription | `plans`, `subscriptions`, `subscription_billing_periods`, `billing_job_runs`, `billing_job_items` |
| Payment | `payments`, `payment_proofs`, `payment_events` |
| Notification | `email_templates`, `notification_outbox`, `email_delivery_log` |
| Config | `app_settings` |

**Representative constraints (full DDL written in Phase 3 as `V1__baseline.sql`):**
```sql
ALTER TABLE invoices ADD CONSTRAINT uq_invoice_number UNIQUE (invoice_number);
ALTER TABLE subscription_billing_periods
  ADD CONSTRAINT uq_period UNIQUE (subscription_id, period_start);
ALTER TABLE billing_job_runs ADD CONSTRAINT uq_job_run UNIQUE (job_name, scheduled_for);
ALTER TABLE payments ADD CONSTRAINT ck_payment_amount CHECK (amount > 0);
CREATE UNIQUE INDEX uq_payment_ref ON payments (invoice_id, reference_no)
  WHERE reference_no IS NOT NULL;
CREATE UNIQUE INDEX uq_payment_idem ON payments (idempotency_key)
  WHERE idempotency_key IS NOT NULL;
-- Invoices/line items: CHECK non-negative values; @Version columns for optimistic locking.
-- audit_events: REVOKE UPDATE, DELETE FROM leadorbit_app;  (append-only)
```

---

## 12. Legacy → new mapping (documented, not auto-run)

| Legacy (TypeORM) | New (LeadOrbit) | Notes |
|---|---|---|
| `users` (ADMIN/USER enum) | `users` + `roles`/`user_roles` | Map ADMIN→ADMIN role, USER→VIEWER; then manage dynamically |
| `user_services` | `services` | + `hsn_sac`, `tax_class`, `gst_rate` (tax professional to confirm) |
| `leads` + `lead_services` | `leads` + service links | map free-text status → `pipeline_stages` |
| `clients` + `client_services` | `companies`/`contacts` + service links | contact model normalized |
| `proposals` | `proposals` | keep `proposal_no`, date, link |
| `meetings` + `meeting_notes` | `meetings` + `meeting_notes` | preserve one-per-lead/client rule |
| `invoices` (+ `invoice_services`) | `invoices` + `invoice_line_items` | synthesize line items from service catalog; totals recomputed; **no GST back-fill** (historical) |
| `invoice_receipts` | `payments` | import as `VERIFIED` historical rows with `verified_by=SYSTEM/migration` |
| `project_invoices` / recurring | `subscriptions` + periods | map project-linked recurring invoices → subscriptions where a plan exists |
| `projects`,`tasks`,`task_*` | (deferred `delivery`) | out of MVP scope (Decision D2) |

Portfolio seed data (synthetic): a seller profile, a few services with tax classes, a handful
of fictional clients/contacts, plans, active subscriptions, invoices across states, payments
in each verification state, and a demo campaign — **no real data**.

---

## 13. Testing strategy (Phase 4 preview)

- **Unit:** GST engine (intra/inter-state, inclusive/exclusive, exempt, rounding, discounts),
  numbering + financial-year rollover, state machines, proration, permission resolution,
  numbering uniqueness, allocation math.
- **Integration (Testcontainers Postgres):** repositories, transactions, unique constraints,
  concurrency (parallel payment verify / double billing prevented by unique keys + locks),
  audit fail-closed.
- **API (MockMvc):** auth + RBAC allow/deny matrix, privilege-escalation attempts, IDOR,
  validation errors, invoice immutability, payment workflow, subscription transitions.
- **Scheduler:** idempotent re-runs, restart recovery, period uniqueness.
- **Email:** outbox idempotency, retry/backoff, delivery logging — **external sender mocked**;
  **no real credentials/data**.
- Coverage target on `billing`, `subscription`, `payment`, `security`, `audit`.

---

## 14. Build & CI plan

- Maven multi-module is unnecessary; a single module with package-by-feature keeps it simple.
- `pom.xml`: Spring Boot parent 3.3.x, starters (web, security, data-jpa, validation, mail,
  actuator), Flyway, springdoc, PostgreSQL driver, OpenPDF/Flybox, MapStruct (DTO mapping),
  Testcontainers, JUnit 5, Mockito, AssertJ.
- Local verification via Docker:
  `docker run --rm -v "$PWD":/app -w /app maven:3.9-eclipse-temurin-21 mvn -B verify`
  with a `postgres:16` service (compose) or Testcontainers.
- `docker-compose.yml` for app + Postgres (+ Mailpit) for local dev; health via Actuator.

---

## 15. Phase 3 implementation order (each step runnable + tested)

1. **Foundation** — Maven skeleton, config, error envelope, pagination, OpenAPI, Flyway V1
   (identity + audit tables first), Docker/compose, Testcontainers baseline.
2. **Identity + Security + Audit core** — users, login/JWT/refresh/logout, dynamic RBAC, secure
   bootstrap, audit events + fail-closed, admin APIs. *(Closes S1, S6–S12.)*
3. **Catalog + CRM** — services/tax config, companies/contacts/leads/sources/stages/activities,
   consent; preserve Phase 1 rules. *(Foundation on which D2 scope sits.)*
4. **Billing** — GST engine, numbering, invoices + line items, issue/cancel/credit-note,
   immutability, PDF, payments + verification + proofs, allocation. *(Closes S2–S5.)*
5. **Subscriptions** — plans, periods, scheduler, job runs, proration, notifications on
   subscription events.
6. **Notifications** — outbox, templates, dispatcher, retries, delivery log.
7. **(Phase 4 features) Marketing/CRM enhancements** — campaigns, attribution, segments,
   dashboards, exports.
8. **(Backlog) Delivery modules** — projects/tasks/EOD, if desired.

---

## 16. Open items / risks

- **GST specifics** (rates, HSN/SAC, place-of-supply defaults) require a tax professional;
  everything is configurable and flagged.
- **Delivery modules deferred** (D2) — confirm the backlog is acceptable.
- **PDF library** (iText-OSS vs OpenPDF vs Flybox) licensing — default OpenPDF/Flybox (LGPL/OSS).
- **Scheduler choice** (`@Scheduled` vs Quartz) — default `@Scheduled` + advisory lock for the
  portfolio; Quartz only if clustering requirements grow.
- **Email provider** for the portfolio demo — default Mailpit/SMTP; provider-agnostic behind a
  `MailSender` port.

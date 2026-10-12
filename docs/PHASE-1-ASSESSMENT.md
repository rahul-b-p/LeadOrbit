# Phase 1 — Discovery: Architecture Assessment & Feature Inventory

> **Source project (confidential, read-only):** an internal agency CRM/billing backend.
> **Portfolio target (this repo):** `LeadOrbit` — a Java/Spring Boot reimplementation.
> This document is written to be safe to publish: no client names, credentials, production
> URLs, or customer data are reproduced. Environment values are examples/redacted.

---

## 1. Executive summary

The existing backend is a **Node.js + TypeScript REST API** (Express + TypeORM + PostgreSQL,
~17,500 LOC) that manages an agency's internal operations: leads, proposals, meetings,
clients, projects, tasks/time-tracking, a service catalog, and **invoicing with a cron-based
recurring invoice generator** and **manual payment receipts**.

It is materially more than a CRUD app, but it is **not** yet the product described in the
modernization brief. The most important gaps:

| Requirement area | Current state |
|---|---|
| GST-compliant invoicing (CGST/SGST/IGST, GSTIN, HSN/SAC, place of supply, credit/debit notes, PDF) | **Absent.** Invoices carry a free-text number and a single `total_amount`. |
| Subscriptions with billing periods | **Absent as a model.** "Recurring" is inferred from invoice frequency + project link. |
| Manual payment verification workflow (proof upload, verify/reject) | **Absent.** Receipts are written directly; no verification, attachment, or decision state. |
| Dynamic, DB-driven RBAC | **Absent.** Hard-coded `ADMIN`/`USER` enum; authorization checks were largely removed. |
| Comprehensive audit logging | **Minimal.** Only `created_by/updated_by/deleted_by` columns; no event log. |
| Email notifications (invoice/payment) | **Absent.** Only password-reset email exists. |
| CRM / digital marketing | **Partial.** Leads/proposals/meetings exist; no campaigns, lead sources, pipelines, attribution, consent. |
| Automated tests | **None** (`npm test` is a placeholder). |

Several features are also implemented in a way that is unsafe to carry forward (no
authorization on user/payment/invoice endpoints, mutable issued invoices, non-atomic
automation). These are catalogued in §7 and drive the redesign in §9–§11.

**Recommendation:** treat this as a **functional rewrite in Java**, not a port. Preserve the
domain concepts and genuinely useful business rules (§5), redesign the persistence model to
fit GST/subscription/payment semantics, and re-implement authorization/audit/email from
first principles.

---

## 2. Existing stack, tooling and runtime

| Concern | Technology |
|---|---|
| Language / runtime | TypeScript 5.8 on Node.js |
| HTTP framework | Express 4 |
| ORM / DB | TypeORM 0.3, PostgreSQL, `synchronize: false` |
| Migrations | TypeORM migrations (25 files) |
| Validation | Zod 4 (schemas + `validateRequest` middleware) |
| Auth | JWT (`jsonwebtoken`): separate access/refresh/password-reset secrets & token types |
| Password hashing | bcrypt (`BCRYPT_SALT_ROUNDS`, default 12) |
| Mapping | AutoMapper (`@automapper/classes`) entity → DTO |
| Docs | OpenAPI via JSDoc annotations + `swagger-ui-express` |
| Email | Nodemailer (SMTP/Mailpit) — password reset only |
| Scheduling | `node-cron` in-process, pg advisory lock for cross-instance safety |
| Logging | Winston + Morgan |
| Security | Helmet, CORS (currently `origin: '*'`) |
| Container | Dockerfile + docker-compose (dev/prod) |
| Tests | **none** |

**Config/env:** validated at startup by Zod. Secrets come from `.env` (git-ignored);
only `.env.example` is tracked. No credentials are committed. Defaults include an admin
bootstrap account seeded on startup from env.

**App wiring:** `server.ts` → init DataSource → ensure default admin → start cron →
listen → graceful shutdown. `app.ts` → Helmet, permissive CORS, JSON body (1 MB), Morgan,
audit-context middleware, router under `API_PREFIX` (default `/api/v1`), Swagger at
`/api-docs`, error handler.

---

## 3. Module & endpoint inventory

Router mounts everything under `/api/v1`; all modules except `/auth` and `/health` require a
valid access token (no further authorization in most cases — see §7).

### Auth (`/auth`)
- `POST /sign-in`, `POST /refresh`, `POST /forgot-password`, `POST /reset-password`
- Access (15 m) + refresh (7 d) JWTs; refresh is not persisted/rotated/revocable; no logout.

### Users (`/users`)
- `POST /` create (accepts `role: ADMIN|USER`), `GET /` list, `GET /me`, `GET /:id`,
  `PATCH /:id`, `DELETE /:id` (soft delete)
- Fields: name, email (unique), password hash, phone, `employee_salary`, status, role,
  `primary_service_id`.

### Service catalog (`/user-services`, `/user-work-details`)
- CRUD for billable service catalog (`service_name`, `service_cost`, `description`, `is_active`)
  and per-service work details (`work_name`, `work_cost`, FK to service).

### CRM core
- **Leads** (`/leads`) CRUD + `lead_services` junction. Status list (string):
  `New Leads`, `Followup One/Two/Three`, `Relevant`, `Irrelevant`, `Workable`, `Not Workable`.
- **Proposals** (`/proposals`) CRUD; `proposal_no` unique, `proposal_date`, `link_path`,
  FK to lead.
- **Meetings** (`/meetings`) CRUD + `meeting_notes`; status `PENDING/SCHEDULED/COMPLETED/
  CANCELLED/RESCHEDULED`; **at most one meeting per lead and one per client**.

### Billing
- **Invoices** (`/invoices`) CRUD; `invoice_services` junction to catalog; `invoice_number`
  free text; `invoice_frequency_type` ∈ {ONE_TIME, MONTHLY, QUARTERTLY, YEARLY};
  `total_amount`, `advance_amount_to_be_paid`, `total_paid_amount`, generated stored column
  `outstanding_amount = total_amount - total_paid_amount`; `payment_status` string
  `PENDING/PARTIAL/COMPLETED`; optional `lead_id` **xor** `client_id`; optional `proposal_id`.
- **Invoice receipts** (`/invoice-receipts`) CRUD: `paid_amount`, `particulars`,
  `payment_mode`, `cheque_or_transac_no`, `cheque_or_transac_date`.
- **Project invoices** (`project_invoices`) junction linking projects to invoices — the basis
  for the recurring generator.

### Delivery / ops
- **Clients** (`/clients`) CRUD + `client_services`; clients can be auto-created from a lead.
- **Projects** (`/projects`) CRUD: name, dates, priority, total hours, status
  (`Not Started/In Progress/On Hold/Completed/Cancelled/Pending Approval/Closed`).
- **Tasks** (`/tasks`) CRUD: assignee, dates, priority, status, estimated/elapsed hours.
- **Task comments** (`/task-comments`), **EOD reports** (`/eod-report` = task activity +
  activity logs with start/end time and worked hours).

### Reporting
- **Dashboard** (`/dashboard`): totals for leads/active leads/revenue and filtered metric
  endpoints.
- **Reports** (`/reports`): `GET /view` (paginated) and `GET /download` (Excel via a util).
  Non-admin (`USER`) requests are scoped to their own `requestedById`.

---

## 4. Domain model (logical ERD)

```
users ──< primary_service >── user_services ──< user_work_details
                                   │
                                   ├──< lead_services >── leads ──< proposals
                                   ├──< client_services >── clients
                                   └──< invoice_services >── invoices

leads ──< meetings >── meeting_notes      clients ──< meetings
invoices ──< invoice_receipts
projects ──< project_invoices >── invoices
projects ──< tasks ──< task_comments
projects / tasks ──< task_activity_logs >── task_activity
```

**Conventions:** every `AppBaseEntity` has a `bigint` id exposed as a **string**, `created_at`,
`updated_at`, `created_by`, `updated_by`, `deleted_by`, `deleted_at` (soft delete). Junction
tables are composite-PK with hard deletes. Money is `numeric(12,2)` stored as **string**.
Enums are either Postgres enums or free-text `varchar` (inconsistent).

Notable modelling quirks to reconsider:
- `bigint` IDs returned as strings everywhere (serialization artifact).
- Free-text statuses (`leads.status`, `projects.project_status`, `tasks.task_statuses`,
  `meetings.status`) with no DB enum or transition guard.
- `invoice.outstanding_amount` is a **DB generated column**, which fights against
  application-controlled tax/rounding and partial-credit logic.
- Timestamps are `timestamp` (no timezone); automation computes in UTC manually.
- No `updated_at` trigger for junction tables; audit metadata is app-supplied.

---

## 5. Business rules & workflow restrictions worth preserving

These were discovered in the service layer and should be **retained (and corrected)** in Java:

1. **Invoice party rule** — an invoice must reference exactly one of lead **xor** client.
2. **Invoiceable lead status** — creating/updating an invoice against a lead requires status
   `Workable` or `Relevant`.
3. **Referential validation** — service IDs, proposal, client, lead must exist before write.
4. **Lead deletion guard** — a lead referenced by active invoices cannot be deleted
   (`409`).
5. **Meeting uniqueness** — one meeting per lead and one per client.
6. **Lead → client conversion** — once payments exceed the agreed advance, the lead is
   promoted to a client (copying contact data and services) exactly once, under a
   pessimistic write lock; existing invoices are re-pointed to the new client.
7. **Invoice payment status derivation** — `COMPLETED` when cumulative paid ≥ total, else
   `PARTIAL`.
8. **Recurring invoice generation** — for `In Progress` projects, generate catch-up invoices
   at MONTHLY (1 mo) / QUARTERLY (3 mo) / YEARLY (12 mo) intervals from the latest invoice,
   with month-end clamping and UTC day-normalized comparisons; `ONE_TIME` never recurs.
   Distributed safety via `pg_try_advisory_lock`.
9. **Reporting scope** — non-admin users only see their own EOD/activity data.
10. **Soft-delete-first** — deletions are soft; junction tables are hard-deleted; some
    dependent rows (receipts, notes) are soft-deleted to preserve history.

---

## 6. Gap analysis vs. the modernization brief

### 6.1 GST-compliant invoicing — **build new**
Nothing exists for: seller/customer GSTIN, HSN/SAC, tax classification per item, CGST/SGST
vs IGST by place of supply, tax-inclusive/exclusive pricing, discounts, rounding, credit/debit
notes, tax-invoice vs other document types, immutable issued snapshots, PDF generation,
cancellation/credit-note flows, payment allocation/outstanding/overdue. Item-level data
(quantity, rate, tax) does not exist — invoices only link a service catalog entry.

### 6.2 Monthly subscriptions & automated invoicing — **build new**
No subscription, subscriber, plan, billing-period, or renewal entity. Recurrence is a side
effect of project + invoice frequency. No per-period idempotency, no job-execution record,
no notifications, no pause/cancel/plan-change/proration.

### 6.3 Manual payment recording & verification — **build new**
Receipts exist but there is no submission/approval workflow, no proof upload, no verify/
reject state, no submitter identity, no email notifications, no overpayment/duplicate guards
(and `updateInvoiceReciept` does not even recompute the invoice balance).

### 6.4 Dynamic RBAC — **build new**
Two hard-coded roles; most authorization checks were removed. Any authenticated user can
create an `ADMIN` user, issue/cancel invoices, or record payments.

### 6.5 Audit logging — **build new**
Per-row actor columns only. No event table, no auth/failed-login events, no before/after
diffs, no correlation id, no access control, no retention.

### 6.6 Email — **extend**
Only password reset. Needs templating, invoices/PDF or secure links, payment/subscription
notifications, retries/backoff, delivery tracking, idempotency, and an outbox.

### 6.7 CRM / digital marketing — **mostly new**
No lead sources, pipelines/stages beyond the ad-hoc status list, follow-up tasks, interaction
timeline, campaigns, attribution, segmentation, email templates, consent/opt-out, or
marketing dashboards. Existing leads/proposals/meetings provide the nucleus.

---

## 7. Security & data-integrity findings

| # | Severity | Finding | Evidence |
|---|---|---|---|
| S1 | **Critical** | **Broken access control / privilege escalation.** Any authenticated user can `POST /users` with `role: ADMIN`. No role checks on invoice, receipt, user, service, project or task endpoints. | `user.routes.ts`, removal commit "refactor(rbac): remove role authorize"; only `report.controller`/`taskActivity.controller` check role. |
| S2 | **Critical** | **Issued invoices are fully mutable/deletable.** `PATCH /invoices/:id` can change amounts, party, paid amount; `DELETE` soft-deletes. No immutability or adjustment trail. | `invoice.service.ts` `updateInvoice`, `deleteInvoice`; schema allows `totalPaidAmount`. |
| S3 | **High** | **Direct balance manipulation.** `totalPaidAmount` is client-writable via invoice update, decoupled from receipts. | `updateInvoiceSchema` + `updateInvoice`. |
| S4 | **High** | **No idempotency / duplicate protection** in payment recording or recurring generation; no DB uniqueness on (subscription, period). | `invoiceReciept.service.ts`; automation inserts without a period key. |
| S5 | **High** | **Unaudited financial mutations** — payment/invoice changes leave no event trail; automation writes invoices via raw repo bypassing validation. | `invoiceAutomation.service.ts`, `invoiceReciept.service.ts`. |
| S6 | **Medium** | **Information disclosure.** Stack traces returned outside production; unique-constraint `detail` (may echo values) returned to clients. | `errorHandler.middleware.ts`. |
| S7 | **Medium** | **Permissive CORS** `origin: '*'` with `Authorization` allowed. | `app.ts`. |
| S8 | **Medium** | **No rate limiting** on sign-in / password-reset / sensitive endpoints; no account lockout. | no middleware present. |
| S9 | **Medium** | **No ownership checks (IDOR)** on invoices/receipts/leads/etc.; any authenticated user can read/modify any record by id. | services fetch by id without actor scoping. |
| S10 | **Medium** | **Mass-assignment surface** via `Object.assign(entity, dto)` in several services (bounded only by Zod schemas). | `user.service.ts`, `leads.service.ts`, `meeting.service.ts`. |
| S11 | **Low** | DB-level sort columns are not re-validated in the repository (allowlist enforced only at the Zod layer). | `query.repository.ts` vs `query.schema.ts`. |
| S12 | **Low** | No token revocation/rotation store; stolen refresh tokens remain valid until expiry; no logout. | `utils/jwt.ts`. |
| S13 | **Low** | Free-text `payment_mode`/status; no file-upload validation (feature absent). | entities/schemas. |

**Mitigating positives:** parameterised queries via TypeORM (no raw SQL interpolation of user
input in the query builder), bcrypt(12), separate JWT secrets with `tokenType` assertion,
Zod `.strict()` request schemas, soft deletes, migrations not `synchronize`, secrets not
committed.

---

## 8. Technical limitations & code-quality observations

- **No tests** and no CI; `npm test` is a stub.
- **Inconsistent enums:** some Postgres enums, some free-text `varchar` with app-level checks.
- **Timestamps lack timezone**; automation does manual UTC math.
- **String money**: arithmetic via `Number()` in the app, risking precision drift and
  bypassing DB semantics.
- **`outstanding_amount` generated column** prevents tax/rounding/credit adjustments from
  being the single source of truth.
- **Naming inconsistencies**: `invoiceReciept` (typo), `QUARTERTLY` (typo), `asigned_to_id`.
- **`metadata.tableName` mutation** in `invoice.service.ts` is a fragile runtime hack.
- **Single-process scheduling** plus advisory-lock workaround; the in-memory `isRunning`
  flag works only within one instance.
- **DTO/entity mapping** via AutoMapper adds indirection without type safety guarantees.
- **Duplicate/parallel concepts**: leads vs clients duplicated contact fields; service shown
  both as free-text `invoice.service` and as catalog links.
- **Bootstrap admin** determined by "any ADMIN row exists", not a secure bootstrap.

---

## 9. Target Java architecture

**Stack (proposed):** Java 21 (LTS) · Spring Boot 3.3.x · Spring Security 6 ·
Spring Data JPA / Hibernate 6 · PostgreSQL 16 · Flyway · Bean Validation · springdoc-openapi ·
JUnit 5 + Mockito + Testcontainers · **Maven** (no build tool exists in the source; Maven is
the recommended default for this style of app).

**Style:** modular monolith, package-by-feature, with strict layering
`api (controller + DTO) → application (service, use cases) → domain (entities, value objects,
state machines, policies) → infrastructure (JPA repositories, mail, PDF, storage, audit)`.

**Proposed package layout**

```
com.leadorbit
├── common        (errors, ApiResponse, pagination, clock, ids, money)
├── config        (security, openapi, jpa, scheduling, async)
├── security      (jwt, users/roles/permissions, rbac enforcement, bootstrap)
├── audit         (events, aspect/interceptor, access rules, retention)
├── identity      (users, roles, permissions, assignments)
├── catalog       (services, work details / HSN-SAC + tax class)
├── crm           (leads, sources, stages, companies/contacts, activities, tasks)
├── sales         (proposals, meetings)
├── delivery      (projects, tasks, time/eod) — optional, see §12/Q)
├── billing       (invoices, line items, taxes, numbering, credit/debit notes, PDF)
├── subscription  (plans, subscriptions, billing periods, scheduler, job runs)
├── payment       (submissions, proof storage, verification, allocation)
├── notification  (templates, outbox, retry, delivery status)
├── marketing     (campaigns, templates, attribution, consent) — phase 4
└── reporting     (dashboards, exports)
```

**Database strategy**
- New, normalized schema managed by **Flyway** (V1…); PostgreSQL; `timestamptz`; `numeric`
  for money; real enums or check-constrained lookups; UUID or bigint ids promoted to `Long`.
- **Compatibility:** the old schema is *not* copied 1:1. Where feasible, provide a
  documented, optional **data-migration path** from the legacy tables into the new model
  (leads→crm, user_services→catalog, invoices→billing with line items synthesized, receipts→
  payments marked `VERIFIED` for historical rows). Migration scripts live outside the
  runtime app and are documented, not auto-run.
- Key constraints: unique `(subscription_id, period_start)`; unique `(invoice_id, reference_no)`
  for payments; `CHECK (amount > 0)`; optimistic locking (`@Version`) on invoice/balance.

---

## 10. Workflow design (state machines)

### 10.1 Invoice
`DRAFT → ISSUED → (PARTIALLY_PAID → PAID) | OVERDUE | CANCELLED`
- Only `DRAFT` is editable. `ISSUED` is **immutable** (frozen seller/customer/item/tax
  snapshot). Corrections via **credit note**; void via **cancellation** (with reason + audit).
- Numbering assigned atomically on issue (DB sequence/row lock), never editable.
- Payment allocation updates derived status via domain methods; no client-writable balances.

### 10.2 Subscription
`DRAFT → ACTIVE → (PAUSED → ACTIVE) | CANCELLED ; ACTIVE → EXPIRED`
- Monthly billing anchored to an explicit `billingAnchorDay` + `zoneId`.
- Pause/cancel/plan-change define proration rules; every period is unique → idempotent.
- Scheduler creates invoices in `DRAFT`/`ISSUED` per policy and enqueues notifications.

### 10.3 Payment
`SUBMITTED → UNDER_REVIEW → (VERIFIED | REJECTED | NEEDS_INFO)`
- Only `VERIFIED` payments affect the invoice balance, inside one transaction with the
  audit event. Screenshots are stored privately; access is authorized and never public.
- Overpayment/duplicate rules enforced in the domain and by DB constraints.

### 10.4 Authorization
Database-driven `users ↔ user_roles ↔ roles ↔ role_permissions ↔ permissions`, plus
`user_permissions` for explicit grant/revoke. Permission keys (`invoice.read`, `invoice.issue`,
`payment.verify`, `subscription.manage`, `user.manage_permissions`, …) enforced by method-level
checks **and** URL authorization. A **super-admin boundary** prevents granting privileges the
actor does not hold; self-escalation is blocked; all changes are audited and caches evicted on
change. Secure one-time bootstrap for the first super-admin.

### 10.5 GST
- Configurable seller registrations (GSTIN, state) and per-item tax config (HSN/SAC, rate,
  inclusive/exclusive, treatment).
- Determine **place of supply**; intra-state → CGST+SGST (split), inter-state → IGST;
  non-GST/exempt/zero-rated handled explicitly; rounding at invoice level (configurable).
- Immutable snapshot on issue; credit/debit notes reference the original.

### 10.6 Audit
Append-only `audit_events` (id, actor, action, entity type/id, timestamp UTC, outcome,
correlation/request id, source IP, minimal before/after diff, redaction). No update/delete
APIs. Critical financial writes fail (transaction rollback) if the audit event cannot persist.
Retention/archival policy configurable.

### 10.7 Email
Transactional vs marketing separated. Reusable HTML + plain-text templates (versioned),
invoice **PDF attachment or secure link**, `notification_outbox` + async worker with bounded
retry/backoff and idempotency keys; delivery status recorded; never send inside a long
financial transaction. SMTP/provider config via environment. External mail **mocked in tests**.

---

## 11. Implementation plan (phased)

**Phase 1 — Discovery ✅ (this document).** No source changes.

**Phase 2 — Design.** Java skeleton (Maven, Spring Boot 3.3, Flyway, springdoc, Testcontainers
baseline); finalize schema + Flyway V1; define state machines, DTO/error conventions, security
model, GST assumptions, legacy-data migration mapping; ADRs; open questions resolved (§13).

**Phase 3 — Implementation (incremental, each step runnable & tested).**
1. Foundation: config, error handling, response envelope, pagination, OpenAPI, Flyway, Docker.
2. Identity + JWT auth + secure bootstrap + **dynamic RBAC** + audit core.
3. Catalog + CRM core (leads, companies/contacts, sources, stages, activities) preserving
   §5 rules; then proposals/meetings.
4. Billing: GST tax engine, numbering, line items, issue/cancel/credit-note, immutability,
   PDF, payments + manual verification workflow.
5. Subscriptions: plans, billing periods, scheduler with idempotency + job-run records,
   proration/pause/cancel.
6. Notifications: outbox, templates, retries, delivery status, idempotency.
7. CRM/marketing enhancements (campaigns, templates, attribution, consent, dashboards).
8. Delivery/ops modules (projects/tasks/EOD) — **scope decision required (§12)**.

**Phase 4 — Verification.** Unit/integration/API tests for every critical workflow (auth+RBAC,
GST intra/inter-state, numbering + financial-year rollover + rounding, monthly generation +
duplicate prevention, subscription transitions, payment verification/partials/duplicates,
email retries, audit protection, concurrency), Flyway on a clean DB, end-to-end happy paths,
negative authorization tests, static analysis/security checks.

**Phase 5 — Documentation & delivery.** README, architecture, schema/ERD, API docs (OpenAPI UI),
RBAC + GST + subscription + payment + audit + email docs, env examples, local/deploy/migration/
rollback/test instructions, ADRs, known limitations, migration report (retained / redesigned /
removed / deferred), and a confidentiality-safe portfolio description.

---

## 12. Confidentiality handling

- Do not copy real client/customer/employee data, GSTINs, production URLs, SMTP credentials,
  or the client's brand name into the portfolio repo. Use the neutral name **LeadOrbit** and
  fictional seed data.
- The seed migration found in the source contains only synthetic `CRON_TEST_*` data (no PII).
- `.env`/`.env.production` are git-ignored upstream; the portfolio repo will ship only
  `.env.example` with placeholder values and secret placeholders such as
  `${DB_PASSWORD}` / `${JWT_SECRET}`.

---

## 13. Assumptions & open questions (need confirmation before/within Phase 2)

1. **Target repo/name/package** — build in `LeadOrbit` under package `com.leadorbit`?
2. **Build tool** — Maven (recommended) vs Gradle.
3. **Delivery/ops modules scope** — keep projects/tasks/EOD/time-tracking and the dashboard/
   reports as-is conceptually, or focus the portfolio on CRM + billing + subscriptions?
4. **GST specifics** — seller's home state & GST registration status, whether all supplies are
   taxable, typical HSN/SAC and rates, and whether tax-inclusive pricing is needed. Exact tax
   parameters require a **qualified tax professional**; we will make them configurable.
5. **Invoice numbering** — desired format (prefix / financial year / reset rules).
6. **Subscriber definition** — treat a **client** as the subscriber, with subscription anchored
   to a client + service/plan? Confirm.
7. **Payment proof storage** — local disk volume vs S3-compatible object storage for the
   portfolio (default: pluggable `FileStorage` with a local adapter).
8. **Legacy data migration** — is an actual data migration required, or is a documented
   mapping + synthetic seed sufficient for the portfolio?
9. **Auth tokens** — keep stateless JWT (short-lived access + persisted/rotating refresh) or
   adopt server sessions? Default: JWT with refresh-token persistence + revocation + logout.
10. **Backward-compatible API surface** — none of the old endpoints need to remain; confirm we
    may redesign the API (we will document breaking changes).

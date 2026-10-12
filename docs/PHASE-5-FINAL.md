# Phase 5 — Final: Run, Configure, Map

> LeadOrbit — Java 21 / Spring Boot 3 backend for CRM, GST invoicing, subscriptions and
> verified payments. This is the operator's handbook and the migration report.

---

## 1. Run

### Build & test (no local JDK required)

```bash
# Full test run against real Postgres 16 via Testcontainers
docker run --rm --network host \
  -e TESTCONTAINERS_HOST_OVERRIDE=localhost -e TESTCONTAINERS_RYUK_DISABLED=true \
  -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD":/app -w /app \
  -v leadorbit_m2:/root/.m2 maven:3.9-eclipse-temurin-21 mvn -B test

# Package only
docker run --rm --network host -e TESTCONTAINERS_HOST_OVERRIDE=localhost \
  -e TESTCONTAINERS_RYUK_DISABLED=true -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$PWD":/app -w /app -v leadorbit_m2:/root/.m2 \
  maven:3.9-eclipse-temurin-21 mvn -B -DskipTests package
```

### Run the app (Postgres 16)

```bash
docker run -d --name leadorbit-pg -e POSTGRES_DB=leadorbit -e POSTGRES_USER=leadorbit \
  -e POSTGRES_PASSWORD=leadorbit -p 5432:5432 postgres:16-alpine

docker run --rm -p 8080:8080 -v "$PWD":/app -w /app \
  -e DB_HOST=localhost -e SPRING_MAIL_HOST=localhost \
  -e BOOTSTRAP_ADMIN_EMAIL=admin@example.com -e BOOTSTRAP_ADMIN_PASSWORD='Change-me-1234567890' \
  maven:3.9-eclipse-temurin-21 java -jar target/leadorbit-0.1.0-SNAPSHOT.jar
```

- Migrations run automatically (Flyway). Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`.
- The app boots with an empty product (no client data) unless `DEMO_ENABLED=true` (see §2).
- For local email, run MailHog on :1025 and keep `MAIL_ENABLED=true`.

## 2. Configuration reference

| Env var | Default | Meaning |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | `localhost:5432/leadorbit` | PostgreSQL |
| `API_PREFIX` | `/api/v1` | Context path |
| `JWT_ACCESS_SECRET` / `JWT_REFRESH_SECRET` | dev-only placeholders | HMAC secrets — **override in any real environment** |
| `JWT_ACCESS_EXPIRY` / `JWT_REFRESH_EXPIRY` | `PT15M` / `P7D` | Token lifetimes |
| `RESET_LINK_BASE` | `http://localhost:5173` | Base URL used in password-reset emails |
| `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` | `admin@example.com` / empty | First-run super-admin (password empty ⇒ auto-generated to logs) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Allowed origins |
| `BILLING_ZONE_ID` | `Asia/Kolkata` | Billing day boundary (invoice/overdue dates) |
| `BILLING_DEFAULT_GST_RATE` | `18.00` | GST rate for ad-hoc (service-less) lines |
| `BILLING_DEFAULT_DUE_DAYS` | `15` | Invoice due period when not supplied |
| `BILLING_ALLOW_CREDIT_BALANCE` | `false` | Allow over-verified payments as credit |
| `BILLING_PRORATION_POLICY` | `NONE` | Only `NONE` is implemented (prorated plan changes are rejected) |
| `SCHEDULING_ENABLED` | `true` | Turns billing cron + outbox dispatcher on/off |
| `BILLING_CRON` | `0 0 2 * * *` | Daily billing run |
| `MAIL_ENABLED` | `true` | `false` ⇒ outbox dispatch is simulated (also used by tests) |
| `MAIL_HOST` / `MAIL_PORT` | `localhost:1025` | SMTP (e.g. MailHog) |
| `MAX_FILE_SIZE` | `10MB` | Proof upload size cap |
| `STORAGE_BASE_PATH` | `./uploads` | Proof blob root (local adapter) |
| `DEMO_ENABLED` | `false` | Seed an idempotent synthetic demo dataset at boot |

## 3. Endpoint map

All under `API_PREFIX`; every mutation and most reads are permission-gated (`@PreAuthorize`).

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/sign-in`, `POST /auth/refresh`, `POST /auth/sign-out`, `POST /auth/forgot-password` (public), `POST /auth/reset-password` (public, single-use token) |
| Users & RBAC | `/users`, `/roles`, `/permissions`, `/audit-events` |
| Catalog | `/catalog/services`, `/catalog/gst-rate-slabs` |
| CRM | `/crm/companies`, `/crm/contacts`, `/crm/leads` (convert/pipeline), `/crm/...` config |
| Billing | `/billing/seller-profiles`, `/billing/invoices` (create/list/get/events), `POST .../issue`, `POST .../cancel`, `DELETE ...` (drafts only), **`GET .../{id}/pdf`** (`invoice.download`) |
| Payments | `POST /payments/submit`, `POST /payments/record` (internal), `PATCH /payments/{id}/verify`, `POST /payments/{id}/proofs`, `GET /payments/{id}/proofs/{proofId}` |
| Subscriptions | `/subscriptions/plans`, `/subscriptions`, `{id}/pause|resume|cancel`, `{id}/change-plan`, `{id}/periods`, `/subscriptions/billing-jobs`, `POST /subscriptions/billing-jobs/run` |
| Notifications | `GET /notifications/outbox`, `GET /notifications/outbox/{id}/deliveries`, `POST /notifications/dispatch` |
| Reports | `GET /reports/revenue-summary?from&to`, `GET /reports/accounts-receivable`, `GET /reports/mrr` |

**Permission codes seeded** (V2/V6): `user.*`, `role.manage`, `audit.read`, `catalog.read/manage`,
`crm.read/manage`, `invoice.read/create/issue/cancel/download`, `payment.read/record/verify`,
`subscription.read/manage`, `notification.manage`, `report.read/export`.

## 4. Legacy → LeadOrbit mapping (documented, no data import)

| Legacy concept | LeadOrbit counterpart |
|---|---|
| Node/TS REST + TypeORM + PostgreSQL | Java 21 + Spring Boot 3 + Spring Data JPA + Flyway |
| Hard-coded `ADMIN`/`USER` | DB-driven roles/grants/revokes → JWT `perms` |
| Free-text invoice number | FY-gapless `INV/26-27/000001` allocation |
| `total_amount` invoice | GST invoice: lines, CGST/SGST/IGST/cess, snapshots, PDF |
| "Recurring" via frequency strings | `plans` + `subscriptions` + period-based billing job |
| Direct receipt write | submit → verify workflow with proofs, reason, audit |
| `created_by/updated_by` only | Append-only trigger-enforced audit log |
| Password-reset-only email | Transactional outbox: invoice-issued / payment-verified templates |
| `/invoices/print` ad-hoc report | PDF endpoint + revenue / AR aging / MRR reports |

## 5. Deferred backlog (documented, not implemented)

- Lead `proposals` / `meetings` / `meeting_notes` tables (Phase-1 inventory) — CRM surface is
  otherwise complete; the schema can be added via a new Flyway migration without touching billing.
- Mid-cycle proration (`PRORATE_BY_DAYS`) — explicitly rejected today; safe failure is a documented 400.
- S3 storage adapter (port exists: `FileStorage`).
- Credit-note issuance from a paid invoice (debit note/bill of supply variants of the numbering).
- Scheduled report exports (`report.export` permission is seeded, no endpoint yet).

## 6. Security notes

- `JWT_ACCESS_SECRET`/`JWT_REFRESH_SECRET` must be replaced outside development; secrets never logged.
- Proof uploads are type- and size-allowlisted; storage keys are server-generated (no traversal).
- Financial rows are written only under row locks or REQUIRES_NEW units; client input cannot set
  balances, invoice numbers, or statuses.
- The audit trigger is `REQUIRED` (fail-closed): a violating write fails loudly rather than
  silently skipping the audit trail.
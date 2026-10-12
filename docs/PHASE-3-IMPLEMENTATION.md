# Phase 3 — Implementation Log

> Package: `com.leadorbit` · Java 21 · Spring Boot 3.3.x · Apache Maven
> Runbook: see `PHASE-5-FINAL.md`. Verification matrix: see `PHASE-4-VERIFICATION.md`.

---

## 1. Foundation (commit `382c1ee`)

- Spring Boot 3 skeleton, `common` module (`ApiResponse`, `ApiException`, `GlobalExceptionHandler`,
  mapping **409 Conflict** for `DataIntegrityViolationException` so DB unique constraints surface as
  idempotency-safe conflicts instead of 500s).
- OpenAPI (springdoc), actuator health/info, `application.yml` with env-driven placeholders.
- SQL migrations (Flyway, `src/main/resources/db/migration`) — shipped after Lab exercises by us, execute
  before every integration test via Testcontainers `postgres:16-alpine`.

## 2. Identity, security & audit (commit `0df809e`)

- **JWT access tokens** (jjwt 0.12.6, HMAC) + **opaque rotating refresh tokens** in the DB,
  rotated on every use with **reuse detection** (a replayed refresh token is revoked).
- **Dynamic DB-driven RBAC.** `Permission` codes (e.g. `invoice.issue`, `payment.verify`) are rows;
  `Role` is a set of permissions; a user's effective permissions =
  **union(roles) ∪ explicit grants − explicit revokes**, **baked into the JWT `perms` claim** so
  authorization is purely declarative (`@PreAuthorize("hasAuthority('payment.verify')")`) and offline.
- **Append-only audit log**: every audit write goes through a DB trigger that makes the table
  **insert-only** (REQUIRED, fail-closed); security-failure audits funnel through a
  REQUIRES_NEW transaction so recording survives the failed operation.
- Bootstrap super-admin from env config for first-run (used by tests as `admin@test.local`).

## 3. Catalog (commit `476492a`)

- `services` catalogue with GST attributes (HSN/SAC, tax class, default GST rate, unit, default rate),
  plus configurable **GST rate slabs** (19%/24% bands, as of 2025-26).

## 4. CRM (commit `1d2cc2d`)

- Companies, contacts, leads, pipeline stages, activities, **consent ledger** (opt-in/opt-out with
  timestamps), lead → client **conversion**, and config endpoints. No client data seeds.

## 5. Billing core — GST engine (commit `e652f5a`)

- **GST engine** `GstCalculationService`: CGST + SGST for intra-state, IGST for inter-state
  (seller state vs place of supply), optional cess, rounding policy, tax-inclusive pricing.
- **Seller profiles** (GSTIN, state code, address, default flag).
- **Gapless financial-year invoice numbering**: `invoice_number_sequences` rows keyed by
  `(seller_id, doc_type)` with per-FY `(fy, last_used_sequence)`; allocation is serialized with a
  row lock, so numbers are contiguous within a FY (`INV/26-27/000001`).
- Invoice **lifecycle**: draft → issued → (partially paid / paid / overdue) → cancelled; snapshots of
  seller/customer/lines frozen **at issue time**; append-only `invoice_events`; drafts only deletable,
  issued/cancelled invoices immutable-deletable (**fixes legacy bug S2**).
- **Client-supplied monetary fields ignored** — `amountPaid` and totals are server-computed
  (**fixes bug S3**; verified by a tamper test).

## 6. Subscriptions & payments (commit `c32785d`)

### Recurring billing
- `plans` (code, service link, amount, billing interval, tax-inclusive), `subscriptions`
  (customer = company *or* contact, plan, start/end, anchor day, zone, price override).
- `BillingPeriodCalculator`: explicit month-end clamping — an anchor on the 31st yields
  28/29 Feb, then re-anchors to the 31st; period = `[periodStart, nextBillingDate)`.
- `SubscriptionBillingProcessor`: per-period **REQUIRES_NEW** unit that renders the invoice through
  the real draft→issue pipeline (so GST, gapless numbering, snapshots and audit all apply).
- `BillingJobService`: **Postgres session advisory lock on a pinned connection** (acquire and release
  on the same pooled connection), find-or-create run keyed by `(job_name, scheduled_for)`, crash
  recovery of `RUNNING` rows, per-period upsert keyed by `(subscription_id, period_start)` — a restart
  can never double-invoice (**S4/S5 for recurring billing**).
- `BillingScheduler`: cron at 02:00, gated by `app.scheduling.enabled` (off in tests; tests drive the
  job directly with an explicit `asOf` date).
- Pause/resume/cancel/change-plan; **paused periods are never back-billed** (resume clamps
  `nextBillingDate` to today); proration is deliberately **NONE** (mid-cycle change-plan with
  `prorated=true` returns 400).
- `OverdueDerivationService`: bulk marks due invoices overdue.

### Payment verification
- Submit → `SUBMITTED` (no balance effect) → verify/reject/needs-info (reason required on reject).
- Allocation is **server-side under an invoice row lock with total recompute** — never incremental —
  so concurrent verifications cannot corrupt balances (**fixes S3**); payment amounts are immutable in
  the DB (update is not possible via API).
- **Idempotency**: client idempotency key + unique `(invoice_id, reference_no)`; a retried request
  returns the original result (**fixes S4**).
- Overpayment → 400 unless `app.billing.allow-credit-balance`; proofs (image/PDF) stored via the
  `FileStorage` port with **server-generated keys** (no path traversal), SHA-256 recorded for integrity.
- Autopilot rule preserved: when cumulative VERIFIED payments for a lead invoiced beyond its
  **advance amount**, the lead converts to client automatically.
- A linked `subscription_billing_period` moves to `PAID` exactly when its invoice is settled.

## 7. Notifications, PDF & reports (this step)

- **Transactional outbox**: `email_templates` (seeded) + `notification_outbox` + `email_delivery_log`.
  Business transactions enqueue rows (idempotency keys `invoice-issued:<id>`, `payment-verified:<id>`);
  `OutboxScheduler` (gated by the same scheduling switch) drains the queue with retry/backoff and a
  delivery log. With `app.mail.enabled=false` (tests) dispatch is **simulated** — no SMTP needed.
- **Invoice PDF** (`GET /billing/invoices/{id}/pdf`, permission `invoice.download`): OpenPDF render of
  a pure `InvoicePdfData` snapshot (unit-testable), generated **from the stored snapshots**, not
  live data.
- **Reports** (`report.read`): revenue summary by month, AR aging buckets (current/1-30/31-60/61-90/90+),
  MRR + annual run rate from ACTIVE subscriptions (monthly-equivalent of quarterly/yearly plans).
- **Demo dataset** (`app.demo.enabled=true`): idempotent synthetic seller/customer/plan/subscription/
  invoice (with the invoice-issued outbox notification) to explore APIs without importing anything real.
- **Password self-service reset**: `POST /auth/forgot-password` + `POST /auth/reset-password` (public).
  Tokens are opaque, single-use, SHA-256-hashed at rest, expire in 60 minutes; a successful reset
  revokes every existing refresh session. The email goes through the outbox (`password-reset`
  template). Unknown emails get an identical response (no user enumeration).

---

## Key invariants enforced (with tests)

| Rule | Where |
|---|---|
| Only VERIFIED payments allocate; allocations recompute from the DB sum minus self | `PaymentService.allocateAndFinalize` |
| Delivered emails can never duplicate (outbox idempotency key) | `NotificationService.enqueue` |
| Job restarts never double-bill (unique run + period keys) | `BillingJobService` / `BillingJobItemRepository` |
| Invoice numbers are FY-gapless and row-lock serialized | `InvoiceNumberAllocator` |
| Issued invoices are immutable and undeletable; only drafts delete | `InvoiceService` |
| Permission checks are `@PreAuthorize` on authority codes from the JWT | every `api` class |
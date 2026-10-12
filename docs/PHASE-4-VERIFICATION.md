# Phase 4 — Verification

> Every number below comes from an **actually executed** run: `mvn -B test` (Dockerized
> `maven:3.9-eclipse-temurin-21`, Postgres 16 via Testcontainers, Ryuk disabled, host-networked).

---

## 1. Test matrix — **46 tests, all green** (last full run: `Tests run: 46, Failures: 0, Errors: 0`)

| Suite | Count | Covers |
|---|---|---|
| `identity.AuthIntegrationTest` | 6 | sign-in / refresh rotation + reuse detection, DB-driven permission enforcement on endpoints, user CRUD + role grants/revokes |
| `security.JwtServiceTest` | 3 | token issue/parse/expiry (unit) |
| `catalog.CatalogIntegrationTest` | 3 | CRUD, GST slabs, active-only listing |
| `crm.CrmIntegrationTest` | 2 | companies/contacts/leads, pipeline moves, conversion, consent ledger |
| `billing.BillingIntegrationTest` | 3 | GST intra/inter-state, client amount tamper ignored, lifecycle (issue/cancel/delete rules, viewer 403) |
| `billing.gst.GstCalculationServiceTest` | 6 | CGST/SGST/IGST/cess/rounding/tax-inclusive cases (unit) |
| `billing.pdf.InvoicePdfGeneratorTest` | 2 | valid `%PDF-` bytes from snapshot, null/empty-tolerant render (unit) |
| `payment.PaymentIntegrationTest` | 6 | submit-does-not-allocate, verify allocation & overpayment rules, duplicate/idempotency 409s, proof upload/download SHA-256 integrity, lead auto-conversion, viewer 403 |
| `subscription.BillingPeriodCalculatorTest` | 6 | month-end clamp incl. Feb/leap, quarterly/yearly anchor math (unit) |
| `subscription.SubscriptionBillingIntegrationTest` | 4 | recurring invoice once + restart-safe, GST totals on generated invoice, period→PAID on settlement, state transitions + proration 400, viewer read-only |
| `notification.NotificationAndReportIntegrationTest` | 4 | outbox enqueue on issue + simulated dispatch + delivery log, idempotent double-enqueue, PDF download `%PDF`, revenue/AR/MRR reports, viewer 403 on pdf/outbox |
| `LeadOrbitApplicationTests` | 1 | context boots against Postgres with migrations |

## 2. How to run

```bash
docker run --rm --network host \
  -e TESTCONTAINERS_HOST_OVERRIDE=localhost -e TESTCONTAINERS_RYUK_DISABLED=true \
  -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD":/app -w /app \
  -v leadorbit_m2:/root/.m2 maven:3.9-eclipse-temurin-21 mvn -B test
```

Integration tests spin a real `postgres:16-alpine` per class; **nothing is mocked at the data layer**.

## 3. Legacy bug fixes — verification

| Bug | Fix | Evidence |
|---|---|---|
| S1 ADMIN escalation | Dynamic RBAC; each endpoint gated by permission code | `AuthIntegrationTest.dbDrivenPermissionsAreEnforcedOnEndpoints`; viewer-403 checks in every suite |
| S2 invoices mutable/deletable | Draft-only delete; issued/cancelled immutable; `invoice_events` append-only | `BillingIntegrationTest` lifecycle assertions |
| S3 client-writable `totalPaidAmount` | Server-computed allocation under row lock; client `amountPaid` ignored | `BillingIntegrationTest` tamper test; `PaymentIntegrationTest` |
| S4 no idempotency on financial ops | Idempotency key + unique DB keys; duplicate request returns original | `PaymentIntegrationTest` duplicate-guard + 409; outbox idempotency test |
| S5 no audit on financial ops | Append-only audit (trigger, REQUIRED, fail-closed) on billing/payment/subscription actions | audit assertions in Billing/Payment suites; DB trigger enforced |

## 4. GST / money invariants verified

- 10 000.00 @ 18% intra-state → taxable 10 000, CGST 900, SGST 900, **total 11 800**.
- Paid-in-full 11 800 invoice → `PAID`; partial → `PARTIALLY_PAID` with exact `amountPaid`.
- Recurring invoice for 12 000 @ 18% → 14 160 total; re-running the billing job for the same day
  produces **zero** additional invoices (`ALREADY_COMPLETED` / upsert no-op).
- Payment proofs round-trip byte-for-byte (SHA-256 checked) through `FileStorage`.

## 5. Side-channel verification

- Schema is validated by Hibernate (`ddl-auto: validate`) against the Flyway migrations — every table
  matches its entity, so the tests double as schema conformance checks.
- Scheduling is disabled inside integration tests (`app.scheduling.enabled=false`) so cron jobs never
  race test fixtures; job behaviour is exercised by calling the service directly with a fixed date.
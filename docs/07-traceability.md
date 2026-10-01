# LendHub — requirements traceability & implementation report

**Version:** 0.1.0 · **Date:** 2026-10-01 · **Build:** 77 API tests + 6 web tests green, smoke test passed on the Compose stack

This closes the loop from [02-requirements.md](02-requirements.md) to code and tests. Status: ✅ done and tested · 🟡 done with a documented simplification · ⏳ deferred (with the reason and the plan).

## 1. Functional requirements

| ID | Requirement | Implementation | Verified by | Status |
|---|---|---|---|---|
| LH-01 | Register borrower with KYC | `borrowers.BorrowerService.register`, `KycRules` (ID `63-123456A78`, MSISDN `2637…`), AES-GCM national ID + keyed hash for duplicates, consent timestamp/version | `RulesTest` (ID, MSISDN, mask), `LoanLifecycleIntegrationTest.origination_…` (bad ID 422, duplicate 409) | ✅ |
| LH-02 | Solidarity groups 5–10, no double membership | `BorrowerService.formGroup`, `LoanGroup` | code review; covered by group-loan capture guard | 🟡 no dedicated test yet |
| LH-10 | Capture + affordability ≤ 40% | `ApplicationService.capture/submit`, `ProductCatalog.checkLimits` | integration (limits 422, affordable flag) | ✅ |
| LH-11 | Scorecard A–E with reasons; adverse → E | `scoring.Scorecard`, `ScoringService`, simulated bureau (`OutboundAdapters.creditBureau`) | `RulesTest` (100 pts → A, adverse → E, unaffordable → E, trader → D) | ✅ |
| LH-12 | Maker ≠ checker, manager ≤ US$1,000, committee above, decline reason, audit | `LoanApplication.approve/decline`, `ApplicationService.decide`, append-only `ApprovalDecision` | integration (officer 403, `approval-limit` 403, reason required 422, decision history) | ✅ |
| LH-13 | Offer with total cost + EIR, 7-day validity, OTP | `ApplicationService.issueOffer/accept`, `ScheduleCalculator.effectiveAnnualRate` | `ScheduleCalculatorTest` (EIR 79.59% golden), integration (wrong OTP 422) | 🟡 OTP shown on screen in demo mode instead of a real SMS |
| LH-20 | Credit life before disbursement, idempotent | `LoanServicing.on(OfferAccepted)` → `LoanPorts.CreditLifeProvider`; `LoanAccount.disburse` guard | `LoanAccountTest.cannot_disburse_without_credit_life_cover` | 🟡 InsureHub's partner endpoint is not built yet; LendHub uses the simulated adapter until `INSUREHUB_BASE_URL` is set |
| LH-21 | Disburse to EcoCash, journals, `loan.disbursed` | `LoanServicing.disburse`, `LedgerService.on(LoanDisbursed)`, `EventExternalizer` | integration (net 576.00, DISBURSEMENT journal, outbound event) | 🟡 B2C is simulated (Payments hub has no B2C yet) |
| LH-30 | Flat schedule, residue to last | `ScheduleCalculator.flat` | golden 216.67×5 / 216.65; `ScheduleProperties` | ✅ |
| LH-31 | Reducing balance annuity | `ScheduleCalculator.reducing` | golden 197.02; properties | ✅ |
| LH-32 | Weekly/monthly, business-day roll incl. ZW holidays | `Frequency`, `HolidayCalendar` (+ V2 seed 2026–2027) | `due_dates_on_weekends_and_holidays…`, property "due dates on weekdays" | ✅ |
| LH-33 | EIR from cash flows | `ScheduleCalculator.effectiveAnnualRate` (Newton–Raphson IRR) | `fees_and_credit_life_are_disclosed…` | ✅ |
| LH-40 | EcoCash repayment, idempotent on provider ref | `PaymentCallbackController` (HMAC, Integrations-compatible), `AmqpConfig.PaymentSucceededListener`, `repayment.provider_reference UNIQUE` | integration (401 bad signature, replay → duplicate) | ✅ |
| LH-41 | Payroll CSV with unmatched list | `RepaymentService.importPayroll` | integration (1 posted, 2 unmatched, re-upload posts nothing) | ✅ |
| LH-42 | Allocation order | `loans.domain.AllocationWaterfall` | golden 5/10/2/30/53; `AllocationProperties` | ✅ |
| LH-43 | Overpayment credit; settlement quote | `LoanAccount.receive/applyCredit/settlementQuote/settle` | `LoanAccountTest` (credit applied on due date, settlement closes) + integration | ✅ |
| LH-44 | Reversals via compensating entries | `RepaymentService.reverse`, `LoanAccount.reverse`, ledger `REVERSAL` | `reversal_reopens_a_closed_loan`, integration (double reversal 422) | ✅ |
| LH-50 | Daily accrual = period interest | `InterestRules.dailyAccrual`, EOD `accrueInterest` step | property "accruals equal scheduled interest", `LoanAccountTest` | ✅ |
| LH-51 | Penalties + in duplum cap | `InterestRules.dailyPenalty/inDuplumAllowance`, `LoanAccount.chargePenalty` | golden 200/195/10 → 5; `penalties_stop_at_the_in_duplum_cap` | ✅ |
| LH-52 | DPD + buckets, `loan.arrears-changed` | `LoanAccount.age`, `ArrearsBucket` | `RulesTest`, 45-day fast-forward (37 DPD, DPD_31_60) | ✅ |
| LH-53 | Reminders 3 days before + day 1 overdue | `LoanServicing.sendReminders` → `loan.instalment-due` | covered by the EOD run; no dedicated assertion | 🟡 |
| LH-54 | Restartable EOD, one per date, date advances on success | `eod.EodJobConfig` (Spring Batch, chunked paged reader), `EodService`, `eod_run.business_date UNIQUE` | `EodIntegrationTest` (injected failure → restart, no double accrual; 409 while running) | ✅ |
| LH-60 | Worklist at 7/30/60/90 DPD, priority amount × DPD | `CollectionsService.onAgeing` | fast-forward test (CALL + VISIT) | ✅ |
| LH-61 | Promise to pay; broken → follow-up | `CollectionsService.promise/reviewPromises` | code review | 🟡 no dedicated test yet |
| LH-62 | Restructure with committee approval, 3-month cure | `LoanChangeRequest`, `LoanAccount.restructure/age` | `LoanAccountTest`, integration (requester can't approve, committee can) | ✅ |
| LH-63 | Write-off + recoveries | `LoanAccount.writeOff`, `LedgerService` WRITE_OFF / RECOVERIES | `write_off_returns_the_amounts_to_derecognise` | 🟡 recovery posting not integration-tested |
| LH-70 | IFRS 9 staging | `provisioning.domain.Ifrs9.stage` | `RulesTest` (30/31/90/91, restructured) + integration (Stage 2 at 37 DPD) | ✅ |
| LH-71 | ECL = PD × LGD × EAD, month-end journal | `ProvisioningService.run`, EOD `monthEndProvisioning` step, PROVISION journal | `RulesTest` (112.50), integration | ✅ |
| LH-72 | Balanced double-entry ledger, append-only | `LedgerService` (sync listeners in the business tx), `JournalEntry` checks balance, `@Immutable`, `UNIQUE(source_type, source_ref)`, DB `CHECK` | trial balance asserted balanced in every journey and as an EOD step | ✅ |
| LH-80 | Portfolio dashboard, PAR by branch/product/officer | `ReportingService.par/portfolio`, web `DashboardComponent` | integration (PAR30 > 0), manual UI check | ✅ |
| LH-81 | Loan statement | `GET /loans/{id}/statement` (schedule, repayments + allocations, journals) | code review | 🟡 JSON only; PDF export deferred to v1.1 |
| LH-82 | Regulatory-style return CSV | `ReportingService.regulatoryReturnCsv` | integration (header + SUBSTANDARD row) | ✅ (illustrative layout) |
| LH-90 | Channel loan tools | `GET /customers/{ref}/loans`, `GET /customers?msisdn=`, `POST /loans/{id}/repayment-requests` (Idempotency-Key) | integration (channel summary, channel 403 on staff list, simulated EcoCash completes) | ✅ |
| LH-91 | `loan.*` events via outbox | `integration.EventExternalizer` (`@ApplicationModuleListener` + publication registry) | integration (outbound events), Compose run: 15 messages on `insurehub.events` | ✅ |

## 2. Non-functional requirements

| NFR | Evidence | Status |
|---|---|---|
| Correctness to the cent | jqwik properties over schedules, allocations, accruals (1,000 tries locally, 10,000 in CI) | ✅ |
| EOD 1M instalments < 15 min | not measured yet | ⏳ benchmark planned with a seeded book on the k3s node; results go to `docs/perf/` |
| API p95 < 300/800 ms | not measured yet | ⏳ Gatling simulation planned with the benchmark |
| Security: JWT, RBAC, maker-checker, audit | `SecurityConfig`, `@PreAuthorize` on every endpoint, domain maker-checker, Envers with user (`RevisionInfo`) | ✅ (HS256 demo issuer; Keycloak in platform phase 3) |
| Auditability | Envers on borrower, application, loan; append-only decisions and journals; `/audit/loans/{id}` | ✅ |
| Observability | Actuator health probes, Prometheus metrics, OTel traces (Micrometer bridge, `OTEL_ENABLED`) | ✅ |
| Maintainability | Modulith `verify()`, ArchUnit (no wall clock, controllers are leaves, no floating point in entities); module diagrams in `docs/modules/` | ✅ |
| Portability arm64/amd64 | Jib multi-platform config; web image via buildx | ✅ (built for amd64 locally; arm64 in CI) |
| Mutation score ≥ 70% | PIT configured (`./gradlew :api:pitest`) and run in CI | 🟡 not yet run locally |

## 3. Deviations from the planning pack

| Planned | Built | Why |
|---|---|---|
| Events `@Externalized` on domain events | Registry-backed `@ApplicationModuleListener` in `integration` builds the group envelope and sends it | The bus contract is the Integrations `MessageEnvelope` (`messageId, type, payload, occurredAt`); a listener keeps domain events clean and is still a transactional outbox (ADR-0006) |
| `LoanApproved` event | `OfferAccepted` | the loan is booked only after the borrower accepts the offer |
| Ledger listens asynchronously | synchronous listeners inside the business transaction | journal and business change commit together; nothing to reconcile (ADR-0006) |
| OAuth2 resource server with an external IdP | same resource server, tokens issued by `/api/v1/auth/login` for demo users | no IdP until platform phase 3; only the decoder bean changes (ADR-0006) |
| Testcontainers PostgreSQL + RabbitMQ | PostgreSQL via Testcontainers; RabbitMQ verified on the Compose stack | bus is off in tests (log mode); smoke test covers the broker |
| Playwright e2e | Angular unit tests + `scripts/smoke_test.py` + manual UI check | ⏳ Playwright suite in v1.1 |
| Reporting through module APIs only | `ReportingService` also runs one read-only SQL aggregate over `loans.instalment` | documented read model; keeps the dashboard to one query |

## 4. Known limitations (v1.1 backlog)

1. PDF statement export (LH-81).
2. EOD benchmark at 1M instalments + `EXPLAIN ANALYZE` notes; Gatling API run.
3. Playwright journey (application → approval → disbursement → repayment).
4. Dedicated tests for groups (LH-02), reminders (LH-53), broken promises (LH-61) and recoveries (LH-63).
5. Real InsureHub credit life endpoint and Payments B2C, then switch off the simulated adapters.
6. OTP by SMS through Notifications instead of demo display.
7. OpenAPI snapshot + breaking-change diff in CI.

# LendHub: test cases

**Version:** 0.1.0 · **Date:** 2026-10-01 · **Run:** 77 API tests + 6 web tests passed (`./gradlew test`, `npm test`), smoke test passed on the Compose stack

This is the test-case catalogue behind [05-test-strategy.md](05-test-strategy.md). Each case has an ID, the requirement it proves ([02-requirements.md](02-requirements.md)), the scenario, the expected result, the level and the automated test that runs it. [07-traceability.md](07-traceability.md) gives the same mapping from the requirement side.

**Levels:** U = unit · P = property-based (jqwik, 1,000 tries locally, 10,000 in CI) · I = integration (Spring Boot + Testcontainers Postgres) · A = architecture · W = web unit (Karma) · E = end-to-end (smoke script / manual browser).
**Result:** ✅ passed on 2026-10-01 · ⏳ not automated yet (reason given).

Test classes live in `api/src/test/java/zw/insurehub/lendhub/` and `web/src/app/**/*.spec.ts`.

## 1. Loan schedules (products)

| ID | Req | Scenario | Expected | Level | Automated by | Result |
|---|---|---|---|---|---|---|
| TC-SCH-01 | LH-30 | Flat loan: P 1,000, 5%/month, 6 months | Total interest 300.00; 5 instalments of 216.67 and a last one of 216.65 | U | `ScheduleCalculatorTest.flat_1000_at_5pc_for_6_months_is_216_67_times_5_and_216_65` | ✅ |
| TC-SCH-02 | LH-31 | Reducing-balance loan, same inputs | Annuity 197.02; month-1 interest 50.00; principal sums to 1,000.00 | U | `ScheduleCalculatorTest.reducing_1000_at_5pc_for_6_months_has_annuity_197_02_and_first_interest_50` | ✅ |
| TC-SCH-03 | LH-32 | Weekly product priced monthly | Weekly rate = monthly × 12 / 52 | U | `ScheduleCalculatorTest.weekly_rate_is_monthly_times_12_over_52` | ✅ |
| TC-SCH-04 | LH-32 | Due date falls on a Saturday, Sunday or ZW public holiday | Moves to the next business day | U | `ScheduleCalculatorTest.due_dates_on_weekends_and_holidays_move_to_the_next_business_day` | ✅ |
| TC-SCH-05 | LH-33 | Admin fee and credit-life premium are charged | Both are disclosed on the offer and the effective annual rate is higher than the nominal rate (golden EIR 79.59%) | U | `ScheduleCalculatorTest.fees_and_credit_life_are_disclosed_and_raise_the_effective_rate` | ✅ |
| TC-SCH-06 | LH-30 | Zero-rate loan | Repays principal only, interest 0.00 | U | `ScheduleCalculatorTest.zero_rate_loan_repays_principal_only` | ✅ |
| TC-SCH-07 | LH-30 | Invalid term, negative principal or rate | Rejected with a validation error | U | `ScheduleCalculatorTest.rejects_invalid_terms` | ✅ |
| TC-SCH-08 | LH-30/31 | Any principal, rate, term, frequency | Principal parts sum to the principal exactly (to the cent) | P | `ScheduleProperties.principal_parts_sum_exactly_to_the_principal` | ✅ |
| TC-SCH-09 | LH-30/31 | Any inputs | No principal, interest or fee component is ever negative | P | `ScheduleProperties.no_component_is_ever_negative` | ✅ |
| TC-SCH-10 | LH-30/31 | Any inputs | Schedule totals equal the sum of the lines | P | `ScheduleProperties.totals_equal_the_sum_of_the_lines` | ✅ |
| TC-SCH-11 | LH-30 | Any flat loan | Total interest = principal × rate × term exactly | P | `ScheduleProperties.flat_interest_is_exactly_principal_times_rate_times_term` | ✅ |
| TC-SCH-12 | LH-32 | 1–60 instalments, weekly or monthly | Due dates strictly increase and never fall on a weekend | P | `ScheduleProperties.due_dates_strictly_increase_and_fall_on_weekdays` | ✅ |

## 2. Borrowers, KYC and scoring

| ID | Req | Scenario | Expected | Level | Automated by | Result |
|---|---|---|---|---|---|---|
| TC-KYC-01 | LH-01 | National IDs typed with spaces, lower case or no dashes | Normalised to `63-123456A78` | U | `RulesTest.national_ids_are_normalised` (parameterised) | ✅ |
| TC-KYC-02 | LH-01 | Malformed national ID | Rejected | U | `RulesTest.bad_national_ids_are_rejected` | ✅ |
| TC-KYC-03 | LH-01 | Mobile numbers as `0772…`, `+263 772…`, `263772…` | Normalised to `2637XXXXXXXX` | U | `RulesTest.mobile_numbers_are_normalised` | ✅ |
| TC-KYC-04 | LH-01 | Landline or foreign number | Rejected | U | `RulesTest.landlines_and_foreign_numbers_are_rejected` | ✅ |
| TC-KYC-05 | LH-01, NFR privacy | National ID in list views | Masked (only the last characters shown) | U | `RulesTest.national_id_is_masked_in_lists` | ✅ |
| TC-KYC-06 | LH-01 | Register with a bad ID, then the same ID twice | 422, then 409 duplicate (found by keyed hash, ID stored AES-GCM encrypted) | I | `LoanLifecycleIntegrationTest.origination_enforces_kyc_affordability_scoring_and_maker_checker` | ✅ |
| TC-SCO-01 | LH-11 | Adverse bureau record or unaffordable instalment | Grade E whatever the points, with reasons listed | U | `RulesTest.scorecard_gives_reasons_and_forces_grade_e_for_adverse_bureau_or_unaffordable` | ✅ |
| TC-SCO-02 | LH-11 | Market trader, short tenure, thin credit file | Mid grade (D) with reasons | U | `RulesTest.trader_with_short_tenure_and_thin_file_is_a_mid_grade` | ✅ |
| TC-GRP-01 | LH-02 | Solidarity group of 5–10; a member joins two groups | Second membership refused | – | – | ⏳ covered by code review only; test planned for 0.2 |

## 3. Origination and approval

| ID | Req | Scenario | Expected | Level | Automated by | Result |
|---|---|---|---|---|---|---|
| TC-ORG-01 | LH-10 | Amount or term outside the product limits | 422 with the limit in the problem detail | I | `LoanLifecycleIntegrationTest.origination_…` | ✅ |
| TC-ORG-02 | LH-10 | Instalment above 40% of net income | Application flagged unaffordable | I | same | ✅ |
| TC-ORG-03 | LH-12 | Loan officer tries to approve | 403 | I | same | ✅ |
| TC-ORG-04 | LH-12 | Branch manager approves above US$1,000 | 403 `approval-limit`; committee can approve | I | same | ✅ |
| TC-ORG-05 | LH-12 | Decline without a reason | 422; with a reason the decision is stored append-only | I | same | ✅ |
| TC-ORG-06 | LH-13 | Customer accepts the offer with a wrong OTP | 422, offer stays open | I | same | ✅ |

## 4. Loan account lifecycle

| ID | Req | Scenario | Expected | Level | Automated by | Result |
|---|---|---|---|---|---|---|
| TC-LN-01 | LH-20 | Disburse before credit-life cover is attached | Refused | U | `LoanAccountTest.cannot_disburse_without_credit_life_cover` | ✅ |
| TC-LN-02 | LH-12 | Same user captured and approved; tries to release funds | Refused (four-eyes) | U | `LoanAccountTest.capturer_and_approver_cannot_release_the_disbursement` | ✅ |
| TC-LN-03 | LH-21 | Disburse | Schedule fixed from the disbursement date, status ACTIVE | U | `LoanAccountTest.disbursement_fixes_the_schedule_and_activates_the_loan` | ✅ |
| TC-LN-04 | LH-43 | Pay before the instalment is due | Held as credit, applied on the due date | U | `LoanAccountTest.early_payment_is_held_as_credit_and_applied_when_the_instalment_falls_due` | ✅ |
| TC-LN-05 | LH-52 | Miss an instalment, then pay it | Ages into arrears (DPD, bucket), returns to current when paid | U | `LoanAccountTest.missed_instalment_ages_into_arrears_and_back_when_paid` | ✅ |
| TC-LN-06 | LH-50 | Accrue daily over an instalment period | Sum of accruals = scheduled interest | U | `LoanAccountTest.accruals_for_a_period_equal_the_scheduled_interest` | ✅ |
| TC-LN-07 | LH-51 | Penalties keep accruing | Stop at the in duplum cap | U | `LoanAccountTest.penalties_stop_at_the_in_duplum_cap` | ✅ |
| TC-LN-08 | LH-43 | Early settlement | Future interest waived, loan CLOSED | U | `LoanAccountTest.early_settlement_waives_future_interest_and_closes_the_loan` | ✅ |
| TC-LN-09 | LH-44 | Reverse the settling payment | Loan re-opens with the balances restored | U | `LoanAccountTest.reversal_reopens_a_closed_loan` | ✅ |
| TC-LN-10 | LH-62 | Restructure a loan in arrears | Outstanding principal rescheduled, arrears carried, restructured flag set | U | `LoanAccountTest.restructure_reschedules_outstanding_principal_and_carries_arrears` | ✅ |
| TC-LN-11 | LH-63 | Write off | Returns principal, interest and fees to derecognise; status WRITTEN_OFF | U | `LoanAccountTest.write_off_returns_the_amounts_to_derecognise` | ✅ |
| TC-LN-12 | NFR money | Pay a USD loan in ZWG | Rejected (currency mismatch) | U | `LoanAccountTest.currency_mismatch_is_rejected` | ✅ |

## 5. Interest, penalties and allocation

| ID | Req | Scenario | Expected | Level | Automated by | Result |
|---|---|---|---|---|---|---|
| TC-INT-01 | LH-51 | Outstanding principal 200, arrear charges 195, daily penalty 10 | Only 5 accrued, then accrual stops | U | `InterestRulesTest.in_duplum_caps_arrear_charges_at_the_outstanding_principal` | ✅ |
| TC-INT-02 | LH-50 | Accrual on the due date | Takes the remainder so the period total is exact | U | `InterestRulesTest.accrual_on_the_due_date_takes_the_remainder` | ✅ |
| TC-INT-03 | LH-50 | Any period length and interest amount | Daily accruals sum to the scheduled interest | P | `InterestRulesTest.accruals_over_a_period_equal_the_scheduled_interest` | ✅ |
| TC-ALC-01 | LH-42 | Pay 100 against penalty 5, fee 10, credit life 2, interest 30, principal 150 | 5 / 10 / 2 / 30 / 53 | U | `AllocationWaterfallTest.pays_penalty_fee_credit_life_interest_then_principal` | ✅ |
| TC-ALC-02 | LH-42 | Several instalments due | Oldest cleared first; extra becomes a remainder | U | `AllocationWaterfallTest.oldest_instalment_is_cleared_first_and_the_rest_is_a_remainder` | ✅ |
| TC-ALC-03 | LH-42 | Product configured principal-first | Custom order respected | U | `AllocationWaterfallTest.custom_order_puts_principal_first` | ✅ |
| TC-ALC-04 | LH-42 | Payment of 0.00 | Nothing allocated | U | `AllocationWaterfallTest.zero_payment_allocates_nothing` | ✅ |
| TC-ALC-05 | LH-42 | Any payment against any dues | Allocations + remainder = payment | P | `AllocationProperties.allocations_plus_remainder_equal_the_payment` | ✅ |
| TC-ALC-06 | LH-42 | Any payment | Never allocates more than is owed on any component | P | `AllocationProperties.never_allocates_more_than_is_owed` | ✅ |
| TC-ALC-07 | LH-43 | Any payment | A remainder exists only when every due is fully paid | P | `AllocationProperties.a_remainder_only_exists_when_everything_is_paid` | ✅ |

## 6. Repayments, ledger and integration

| ID | Req | Scenario | Expected | Level | Automated by | Result |
|---|---|---|---|---|---|---|
| TC-REP-01 | LH-21, LH-72 | Disburse 600 with fees | Net 576.00 to EcoCash; DISBURSEMENT journal; `loan.disbursed` published | I | `LoanLifecycleIntegrationTest.disbursement_repayment_callback_and_ledger_balance` | ✅ |
| TC-REP-02 | LH-40 | Payment callback with a bad HMAC signature | 401 | I | same | ✅ |
| TC-REP-03 | LH-40 | Same provider reference posted twice | Second is a duplicate; posted once | I | same | ✅ |
| TC-REP-04 | LH-72 | After every journey | Trial balance balances (debits = credits) | I | same + every integration test | ✅ |
| TC-REP-05 | LH-44 | Reverse a repayment twice | Second reversal 422; compensating REVERSAL journal posted once | I | `LoanLifecycleIntegrationTest.reversal_and_early_settlement` | ✅ |
| TC-REP-06 | LH-43 | Early settlement through the API | Quote matches; loan CLOSED | I | same | ✅ |
| TC-REP-07 | LH-41 | Payroll CSV with 3 lines (1 known national ID, 2 unknown) | 1 posted, 2 listed unmatched; re-uploading posts nothing | I | `LoanLifecycleIntegrationTest.payroll_file_matches_by_national_id_and_lists_unmatched_lines` | ✅ |
| TC-REP-08 | NFR integration | Webhook signature compatible with InsureHub Integrations (`t=,v1=`), old timestamp | Valid signature accepted; replay outside the window rejected | U | `RulesTest.webhook_signature_matches_the_integrations_format_and_rejects_replays` | ✅ |

## 7. End of day, arrears and provisioning

| ID | Req | Scenario | Expected | Level | Automated by | Result |
|---|---|---|---|---|---|---|
| TC-EOD-01 | LH-54 | Fault injected mid-run, then restart | Business date unchanged after the failure; restart resumes and posts no accrual twice | I | `EodIntegrationTest.a_failed_run_keeps_the_date_and_a_restart_resumes_without_double_posting` | ✅ |
| TC-EOD-02 | LH-54 | Start EOD while one is running for the date | 409 | I | `EodIntegrationTest.a_run_already_in_progress_for_the_date_is_refused` | ✅ |
| TC-EOD-03 | NFR security | Loan officer starts EOD | 403; finance role only | I | `EodIntegrationTest.only_finance_can_run_eod` | ✅ |
| TC-ARR-01 | LH-52 | DPD 0, 1, 30, 31, 60, 61, 90, 91 | CURRENT, DPD_1_30, DPD_31_60, DPD_61_90, DPD_90_PLUS at the exact edges | U | `RulesTest.arrears_buckets` (parameterised) | ✅ |
| TC-ARR-02 | LH-52, LH-60, LH-70 | Fast-forward 45 business days without payment | 37 DPD, bucket DPD_31_60, CALL and VISIT tasks, IFRS 9 Stage 2 | E | `scripts/smoke_test.py` | ✅ |
| TC-PRV-01 | LH-70 | DPD 30/31/90/91 and a restructured loan | Stage 1 to 30 DPD, Stage 2 from 31, Stage 3 from 91; restructured is Stage 2; written off is Stage 3 | U | `RulesTest.ifrs9_staging_uses_the_30_and_90_day_presumptions` | ✅ |
| TC-PRV-02 | LH-71 | PD 0.25 × LGD 0.45 × EAD 1,000 | ECL 112.50; PROVISION journal posted at month end | U + I | `RulesTest.written_off_loans_are_stage_3_and_ecl_is_pd_times_lgd_times_ead`, EOD month-end step in the smoke run | ✅ |
| TC-COL-01 | LH-61 | Promise to pay is broken | Follow-up task created | – | – | ⏳ code review only; test planned for 0.2 |
| TC-REM-01 | LH-53 | Instalment due in 3 days, and 1 day overdue | `loan.instalment-due` reminder published | – | runs inside EOD; no dedicated assertion | ⏳ planned for 0.2 |

## 8. Architecture rules

| ID | Req | Scenario | Expected | Level | Automated by | Result |
|---|---|---|---|---|---|---|
| TC-ARC-01 | NFR maintainability | Module dependencies | No cycles; modules use only each other's public API | A | `ArchitectureTest.modules_have_no_cycles_and_only_use_each_others_public_api` | ✅ |
| TC-ARC-02 | LH-54 | Business code reads the date | Never `LocalDate.now()`; always the business date | A | `ArchitectureTest.business_code_never_reads_the_wall_clock_for_dates` | ✅ |
| TC-ARC-03 | NFR money | Entity fields for money | No `float`/`double` | A | `ArchitectureTest.money_maths_never_uses_floating_point_types_in_entities` | ✅ |
| TC-ARC-04 | NFR maintainability | Controllers | Not used by other classes | A | `ArchitectureTest.controllers_are_not_used_by_other_classes` | ✅ |
| TC-ARC-05 | docs | Module documentation | PlantUML diagrams regenerated in `docs/modules/` | A | `ArchitectureTest.writes_module_documentation` | ✅ |

## 9. Web back office

All in `web/src/app/app.component.spec.ts`.

| ID | Req | Scenario | Expected | Level | Automated by | Result |
|---|---|---|---|---|---|---|
| TC-WEB-01 | NFR security | Sign in | Session kept in memory only; roles exposed to the UI | W | `app.component.spec.ts` "signs in, keeps the session in memory and exposes roles" | ✅ |
| TC-WEB-02 | NFR security | Token expired | Treated as signed out | W | "treats an expired session as signed out" | ✅ |
| TC-WEB-03 | LH-12 | Officer opens the committee page | Route guard blocks it | W | "guards routes by role" | ✅ |
| TC-WEB-04 | LH-40 | Request a repayment from the UI | Request carries an `Idempotency-Key` header | W | "sends repayment requests with an Idempotency-Key" | ✅ |
| TC-WEB-05 | LH-80 | Money and status display | Currency shown; statuses mapped to colours | W | "formats money with its currency and maps statuses to colours" | ✅ |
| TC-WEB-06 | NFR usability | API returns ProblemDetails | Readable message shown | W | "turns ProblemDetails into readable messages" | ✅ |
| TC-WEB-07 | LH-80 | Log in as each demo user; dashboard, loan detail, collections, finance pages | Pages load, PAR and loan schedule shown, role menus differ | E (manual, browser) | screenshot `docs/img/loan-detail.jpg` | ✅ |

## 10. End-to-end smoke (Compose stack)

`python scripts/smoke_test.py` against `docker compose up` (Postgres, RabbitMQ, API, web):

| ID | Step | Expected | Result |
|---|---|---|---|
| TC-E2E-01 | Health | `/actuator/health` UP | ✅ |
| TC-E2E-02 | Register borrower, capture and score application | Customer ref issued, ID masked, grade and affordability returned | ✅ |
| TC-E2E-03 | Approve, issue offer, accept with OTP | Offer shows EIR and total repayable | ✅ |
| TC-E2E-04 | Credit life and disbursement | Loan COVERED with a policy number, then ACTIVE; weekly schedule | ✅ |
| TC-E2E-05 | EcoCash repayment | Requested and posted via the simulated callback | ✅ |
| TC-E2E-06 | 45 EOD runs | All COMPLETED; business date advanced | ✅ |
| TC-E2E-07 | Arrears, collections, PAR, trial balance | Loan in arrears, tasks created, PAR30 > 0, trial balance balanced | ✅ |
| TC-E2E-08 | Events | 15 messages delivered to the `lendhub` RabbitMQ queue | ✅ |

## 11. Not yet automated

Performance (Gatling, EOD over 1M instalments), Playwright browser journeys, PIT mutation thresholds in CI and OpenAPI diff checks are in the strategy but not built in 0.1.0. They are tracked in [07-traceability.md](07-traceability.md) §4.

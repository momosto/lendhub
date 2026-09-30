# LendHub — test strategy

## Principles
1. **Money maths is proven, not sampled** — property-based tests over thousands of generated loans.
2. **Hand-checked examples** for every formula, written in the test name (like InsureHub's `20,000 × 4.5% × 1.05 = 945`).
3. Real infrastructure in integration tests (Testcontainers PostgreSQL + RabbitMQ), no H2.
4. Architecture rules are tests (Spring Modulith `verify()`, ArchUnit).

## Test pyramid

| Level | Tooling | What | Target |
|---|---|---|---|
| Unit (domain) | JUnit 5, AssertJ | state machine guards, allocation waterfall, scorecard, staging rules, in duplum cap | ≥ 80% line, ≥ 70% mutation score (PIT) in `loans`, `repayments`, `provisioning` |
| Property-based | **jqwik** | for any P, rate, term, frequency: Σ principal = P exactly; every instalment ≥ 0; outstanding never negative; allocations sum to payment; journals balance | 10,000 tries per property in CI |
| Module | Spring Modulith `@ApplicationModuleTest`, Scenario API | each module in isolation with its events (e.g. `LoanApproved` → schedule created) | every published event |
| Integration | `@SpringBootTest` + Testcontainers | REST flows, Flyway migrations, Envers audit, RabbitMQ externalisation, HMAC callbacks | happy + key failure paths |
| Batch | Spring Batch `JobLauncherTestUtils` | EOD on a seeded book; restart after injected failure resumes correctly; EOD twice for a date is refused | all EOD steps |
| Contract | OpenAPI diff in CI; consumer checks against Payments callback schema | no breaking API changes without a version bump | every PR |
| Architecture | Modulith `verify()`, ArchUnit | no cycles; no access to another module's internals; no `LocalDate.now()` in domain | every build |
| Front end | Vitest/Jest + Angular Testing Library; **Playwright** e2e | application → approval → disbursement → repayment in the browser | critical journeys |
| Performance | **Gatling** (API), batch benchmark | API p95; EOD over 1M instalments < 15 min | before each release, results in `docs/perf/` |

## Golden test cases (hand-calculated)

| Case | Input | Expected |
|---|---|---|
| Flat, monthly | P = 1,000, 5%/month, 6 months | total interest 300.00; instalment 216.67 × 5, last 216.65 |
| Reducing, monthly | P = 1,000, 5%/month, 6 months | instalment 197.02 (annuity); interest month 1 = 50.00; Σ principal 1,000.00 |
| Allocation | pay 100 against penalty 5, fee 10, CL 2, interest 30, principal 150 | 5 / 10 / 2 / 30 / 53 |
| Staging | DPD 31 | Stage 2; DPD 91 → Stage 3 |
| In duplum | outstanding principal 200, arrear interest 195, daily penalty 10 | only 5 accrued; accrual stops |

(Expected values are recomputed in a spreadsheet and committed as `docs/golden-cases.xlsx` for reviewers.)

## Quality gates (CI)
Build fails on: any failing test, mutation score below target in core modules, Modulith/ArchUnit violation, OpenAPI breaking change, CodeQL high finding, Trivy critical with a fix.

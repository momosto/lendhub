# Changelog

All notable changes to LendHub. Format: [Keep a Changelog](https://keepachangelog.com/), versions follow SemVer.

## 0.1.1 (2026-10-01)

### Security
- Spring Boot 3.3.4 → 3.5.16 and Spring Modulith 1.2.4 → 1.4.13. The CI Trivy gate found 9 fixable vulnerabilities in the API image (7 in embedded Tomcat 10.1.30, including critical CVE-2025-24813, and 2 in Spring Security 6.3.3). All 77 API tests pass on the new versions.

## [0.1.0] — 2026-10-01

### Added
- Spring Boot 3.3 / Java 21 modular monolith with 12 Spring Modulith modules (boundaries verified in tests).
- Schedule engine: flat and reducing balance, weekly/monthly, Zimbabwe holiday calendar, effective annual rate (IRR).
- Origination: KYC with encrypted national ID, affordability, scorecard with reasons, simulated bureau, maker-checker approvals with role limits, offers with OTP acceptance.
- Loans: credit life cover before disbursement, disbursement, allocation waterfall, credit balances, early settlement, reversals, restructure and write-off through the credit committee.
- Repayments: HMAC-signed Payments callbacks (Integrations-compatible), RabbitMQ `PaymentSucceeded` consumer, payroll deduction CSV, channel repayment requests with Idempotency-Key.
- Spring Batch end-of-day: accrual, penalties with in duplum cap, arrears ageing, collections tasks, reminders, month-end IFRS 9 ECL, trial-balance check, business-date advance; restartable.
- Double-entry ledger with a 12-account chart, trial balance, PAR / portfolio dashboard, regulatory-style CSV return, Envers audit trail with users.
- Group bus events (`loan.disbursed`, `loan.instalment-due`, `loan.arrears-changed`) through the Modulith publication registry.
- Angular 18 back office (Material) for all six staff personas.
- Tests: 77 API (golden cases, jqwik properties, ArchUnit/Modulith, Testcontainers journeys incl. a 45-day fast-forward and EOD restart), 6 web; post-deploy smoke test.
- Delivery: Dockerfiles, Docker Compose, Jib multi-arch config, Kustomize base + demo overlay, GitHub Actions CI with PIT and Trivy.
- Docs: traceability report, operations runbook, ADR-0006, generated module diagrams.

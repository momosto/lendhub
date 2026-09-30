# LendHub — delivery plan

**Window:** 2026-10-08 → 2026-11-04 (4 weeks × ~15 h) · **Method:** one-person Scrum: weekly sprint, Friday demo video + LinkedIn post

## Milestones

### M1 — Foundation, products & schedules (week 1)
- [ ] Gradle multi-module skeleton, Spring Boot 3 + Modulith, Flyway, Testcontainers, CI (reusable platform workflow), Jib
- [ ] `shared`: `Money`, `Currency` (`USD`, `ZWG`), `BusinessCalendar`, ProblemDetails
- [ ] `products`: 3 seeded products, holiday calendar
- [ ] Schedule engine: flat + reducing, weekly/monthly, business-day adjustment, EIR
- [ ] jqwik properties + golden cases green
- [ ] ADRs 0001–0003 final
**Demo:** `GET /api/v1/products/{id}/quote?amount=1000&term=6` returns a schedule that balances.

### M2 — Origination, scoring & approval (week 2)
- [ ] borrowers + KYC + groups; consent capture
- [ ] applications, affordability, scorecard with reasons, simulated bureau
- [ ] maker-checker approvals with role limits; offers; OTP acceptance (simulated SMS)
- [ ] Angular shell: login, applications list/detail, approval screen
**Demo:** officer captures → manager approves → offer accepted.

### M3 — Credit life, disbursement & repayments (week 3)
- [ ] InsureHub credit life endpoint (small addition to InsureHub — tracked in `insurehub/NEXT_STEPS.md`)
- [ ] disbursement via Payments (B2C simulated), ledger journals
- [ ] `payment.succeeded` consumer, allocation waterfall, reversals, payroll CSV
- [ ] events externalised (`loan.disbursed`, …) via Modulith outbox
- [ ] Angular: loan detail, schedule vs actual, repayment history, statement PDF
**Demo:** end-to-end: loan disbursed to EcoCash, customer repays from phone, receipt on WhatsApp (Notifications).

### M4 — EOD, arrears, provisioning, reports & deploy (week 4)
- [ ] Spring Batch EOD (accrual, penalties + in duplum, ageing, tasks, reminders, month-end staging/ECL)
- [ ] collections worklist + promises; restructure; write-off
- [ ] reports: PAR dashboard, trial balance, regulatory-style return
- [ ] 1M-instalment benchmark + `EXPLAIN ANALYZE` notes
- [ ] deploy via ArgoCD; Angular on Cloudflare Pages; README with live link, badges, demo accounts, 2-minute video
**Demo:** fast-forward the business date 45 days → loan moves to PAR 30 → collections task appears → IFRS 9 Stage 2 → provision journal.

## Definition of done
Tests green incl. properties · docs updated (requirements traceability ID in PR title) · ADR for any significant decision · deployed to demo · README updated.

## Risks
| Risk | Mitigation |
|---|---|
| Java on 2 OCPU/12 GB free VM is slow to start | Jib + CDS, SerialGC, one replica; measure |
| Scope too big for 4 weeks | M4 stretch items (write-off, regulatory return) can slip to a v1.1 without harming the demo |
| Maths mistakes | properties + golden cases before any UI work |

## 2-minute video script
1. (0:00) Problem: MFIs on spreadsheets find arrears 30 days late.
2. (0:15) Officer captures a trader loan; scorecard reasons shown.
3. (0:35) Manager approves (maker-checker), offer with effective rate.
4. (0:50) Disbursement → credit life policy appears in InsureHub.
5. (1:05) Customer repays via EcoCash simulator → allocation waterfall.
6. (1:20) Run EOD 45 days later → PAR 30, collections task, IFRS 9 Stage 2, provision journal.
7. (1:45) Architecture slide: Modulith modules, outbox, Spring Batch, tests (properties), live link.

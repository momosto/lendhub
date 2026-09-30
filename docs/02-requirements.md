# LendHub — requirements specification

## 1. Scope

**In scope:** borrower onboarding (KYC), loan products, applications, credit scoring and affordability, approval workflow with limits, credit life cover, disbursement, schedules, repayments (mobile money, payroll deduction, cash/bank), end-of-day processing, arrears and collections, restructuring, write-off, IFRS 9 staging and provisions, double-entry ledger, portfolio and regulatory-style reports, events to the group bus.
**Out of scope:** deposits/savings, cheque processing, full general ledger (LendHub produces journals; a group GL/ERP would consume them — see backlog `sap-finance-integration`).

## 2. Loan products (illustrative)

| Product | Customers | Amount | Term | Method | Repayment | Collateral / security |
|---|---|---|---|---|---|---|
| **Salary Advance Loan** | civil servants & salaried staff | US$100 – 3,000 | 3 – 24 months | **flat** rate, monthly | payroll deduction file (employer/payroll-bureau style) | payroll deduction authority + credit life |
| **Trader Loan** | informal/SME traders | US$50 – 1,500 | 1 – 6 months | **reducing balance** (declining), weekly or monthly | EcoCash / OneMoney | guarantor + credit life |
| **Group (solidarity) Loan** | groups of 5–10 traders | US$50 – 500 per member | 1 – 6 months | reducing balance, weekly | EcoCash, group collection | joint liability + credit life |

Common pricing parameters per product: nominal interest rate (per month), establishment fee (% of principal, deducted at disbursement or capitalised), credit life premium (per month, collected with the instalment and remitted to InsureHub), penalty rate on overdue amounts, grace period (days), minimum/maximum amount and term, rounding rule.

## 3. Personas

| Persona | Goal |
|---|---|
| Loan officer (Rudo, Mbare branch) | capture applications fast, in the field, with few errors |
| Branch manager | approve good loans quickly within limit; see branch PAR |
| Credit committee | decide on large or exception loans with full information |
| Collections officer | know exactly whom to call today and record promises |
| Finance officer | trust that the ledger balances; run EOD; produce month-end reports |
| Borrower (Mai Chipo, vegetable trader) | get money on EcoCash quickly; know what she owes; pay by phone |
| Auditor / compliance | trace every decision and change to a person and time |

## 4. Functional requirements

### 4.1 Borrowers & KYC
| ID | Story | Acceptance criteria |
|---|---|---|
| LH-01 | As a loan officer, I want to register a borrower with KYC details | national ID format validated (`63-123456A78` style), mobile number normalised to `2637XXXXXXXX`, duplicate ID rejected, consent for bureau check captured with timestamp |
| LH-02 | As a loan officer, I want to form a solidarity group | 5–10 members, each a registered borrower, one chairperson, no member in two active groups |

### 4.2 Origination, scoring, approval
| ID | Story | Acceptance criteria |
|---|---|---|
| LH-10 | As a loan officer, I want to capture an application with income and expenses | draft saved; product limits enforced; affordability computed: instalment ≤ 40% of net disposable income (configurable) |
| LH-11 | As the system, I want to score each application | scorecard points (income stability, tenure, bureau result, previous repayment history, group performance) → grade A–E with **reasons**; bureau (simulated) adverse listing → grade E |
| LH-12 | As a branch manager, I want to approve within my limit | maker ≠ checker enforced; manager limit US$1,000, committee above; decline needs a reason code; every decision audited |
| LH-13 | As a borrower, I want an offer showing total cost | offer shows schedule, total interest, fees, credit life premium and **effective annual rate**; offer valid 7 days; acceptance recorded (OTP via SMS in the demo) |

### 4.3 Credit life & disbursement
| ID | Story | Acceptance criteria |
|---|---|---|
| LH-20 | As the system, I want every loan covered by credit life before disbursement | call InsureHub `POST /api/partners/credit-life/policies` (sum insured = principal, term = loan term); policy number stored; disbursement blocked until cover is confirmed; idempotent on loan ID |
| LH-21 | As finance, I want to disburse to EcoCash or bank | net amount = principal − deducted fees; disbursement via Payments (B2C, simulated); on success loan → `ACTIVE`, schedule fixed, journals posted, `loan.disbursed` published |

### 4.4 Schedules & calculations
| ID | Story | Acceptance criteria |
|---|---|---|
| LH-30 | Flat-rate schedule | interest = principal × monthly rate × months; spread evenly; rounding difference goes to the **last** instalment; Σ principal = principal exactly |
| LH-31 | Reducing-balance schedule | equal instalment (annuity) formula; interest per period on outstanding principal; last instalment adjusted; Σ principal = principal exactly |
| LH-32 | Weekly and monthly frequencies | due dates skip to the next business day for weekends and Zimbabwe public holidays (holiday table) |
| LH-33 | Effective rate disclosure | APR/EIR computed from actual cash flows (IRR), shown on the offer |

### 4.5 Repayments
| ID | Story | Acceptance criteria |
|---|---|---|
| LH-40 | As a borrower, I want to repay by EcoCash | Payments `payment.succeeded` with reference `LN-…` → repayment posted within 5 min; idempotent on provider reference |
| LH-41 | As finance, I want payroll deduction files processed | CSV upload (employer, ID, amount, period) → matched to loans; unmatched lines listed for review |
| LH-42 | Allocation order | **penalties → fees → credit life premium → interest → principal**, oldest instalment first (configurable per product) |
| LH-43 | Overpayment & early settlement | overpayment held as credit and applied to the next instalment; early settlement quote = outstanding principal + accrued interest to date + settlement fee (if any) |
| LH-44 | Reversals | a reversed payment re-opens allocations via compensating entries; nothing is deleted |

### 4.6 End-of-day (EOD)
| ID | Story | Acceptance criteria |
|---|---|---|
| LH-50 | Daily interest accrual (reducing balance) | accrual journal per loan per day; month-end accruals equal the schedule's interest for the period (± rounding rule) |
| LH-51 | Penalties | penalty interest on overdue amounts after the grace period; **in duplum cap**: total arrears interest may not exceed the outstanding principal (illustrative application of the common-law rule; configurable) |
| LH-52 | Arrears ageing | days past due (DPD) per loan; buckets Current, 1–30, 31–60, 61–90, 90+; `loan.arrears-changed` on bucket change |
| LH-53 | Reminders | `loan.instalment-due` 3 days before due date; reminder on day 1 overdue |
| LH-54 | Restartable batch | a failed EOD resumes from the failed chunk; EOD for a date cannot run twice; business date advances only after success |

### 4.7 Collections
| ID | Story | Acceptance criteria |
|---|---|---|
| LH-60 | As a collections officer, I want a daily worklist | tasks auto-created at DPD 7 (call), 30 (visit), 60 (demand letter), 90 (legal/write-off review); prioritised by amount × DPD |
| LH-61 | Promise to pay | record date and amount; broken promise creates a follow-up task |
| LH-62 | Restructure | reschedule with committee approval; restructured flag kept for staging (no automatic cure for 3 months) |
| LH-63 | Write-off | committee approval; loan → `WRITTEN_OFF`; recoveries after write-off posted to a recoveries account |

### 4.8 Provisioning & ledger
| ID | Story | Acceptance criteria |
|---|---|---|
| LH-70 | IFRS 9 staging | Stage 1: DPD ≤ 30; Stage 2: DPD 31–90 or restructured; Stage 3: DPD > 90 or written off (rebuttable presumptions as defaults) |
| LH-71 | Expected credit loss | ECL = PD × LGD × EAD per stage (12-month PD for Stage 1, lifetime for 2–3) with **illustrative** configurable parameters; provision movement journal at month-end |
| LH-72 | Double-entry ledger | every financial event posts balanced journals (Σ debits = Σ credits); trial balance report; no updates to posted journals (reversals only) |

### 4.9 Reporting
| ID | Story | Acceptance criteria |
|---|---|---|
| LH-80 | Portfolio dashboard | gross loan portfolio, active loans, disbursements MTD, collections efficiency, **PAR 30/60/90** (value and %), by branch/product/officer |
| LH-81 | Loan statement | per loan: schedule vs actual, allocations, balance; PDF export |
| LH-82 | Regulatory-style return (illustrative) | month-end portfolio classification and provisions in a fixed CSV layout |

### 4.10 Integration & channels
| ID | Story | Acceptance criteria |
|---|---|---|
| LH-90 | Loan tools for InsureAssist and USSD | `GET /api/v1/customers/{id}/loans` summary, next instalment, balance; `POST /api/v1/loans/{id}/repayment-requests` (starts an EcoCash payment via Payments) |
| LH-91 | Events to the group bus | `loan.disbursed`, `loan.instalment-due`, `loan.arrears-changed` published via the outbox (Modulith event publication registry) |

## 5. Business rules summary

- Money: `BigDecimal`, scale 2 for customer amounts, scale 6 for rates, `RoundingMode.HALF_EVEN` for accruals and HALF_UP for instalments (documented in ADR-0002).
- Currency per loan is fixed at disbursement (USD or ZWG); no mixed-currency allocation.
- Business date is explicit (`BusinessCalendar`), never `LocalDate.now()` in domain code.
- Maker-checker for approval, disbursement release, restructure, write-off and product changes.

## 6. Non-functional requirements

| Category | Requirement |
|---|---|
| Correctness | schedules and ledgers balance to the cent in 100% of property-based test cases |
| Performance | EOD for **1,000,000 instalment rows** completes in < 15 min on the demo node (Gatling/batch benchmark published) |
| API latency | p95 < 300 ms for reads, < 800 ms for writes |
| Availability (demo) | 99% |
| Security | OAuth2/JWT, RBAC, maker-checker, audit trail (Hibernate Envers), OWASP ASVS L2 checklist |
| Auditability | every state change: who, when, before/after |
| Observability | OTel traces incl. batch steps; metrics: EOD duration, loans by bucket, repayments posted |
| Maintainability | Spring Modulith module boundaries verified in tests; ArchUnit rules; ≥ 80% line coverage in domain modules |
| Portability | runs on arm64 and amd64; Postgres only dependency besides RabbitMQ |

## 7. Sources for domain rules

- Microfinance Act [Chapter 24:29] — credit-only MFIs, disclosure, repayment capacity: [ZimLII](https://zimlii.org/akn/zw/act/2013/3/eng@2019-11-19/source.pdf) · [Richtmann overview](https://www.richtmann.org/journal/index.php/mjss/article/download/9955/9588/38638)
- IFRS 9 staging presumptions (30 / 90 DPD): [PwC — IFRS 9 ECL](https://www.pwc.com/hu/hu/szolgaltatasok/ifrs/ifrs_9/ifrs9_kiadvanyok/ifrs_9_expected_credit_losses.pdf)
- In duplum rule in Zimbabwe: [allAfrica](https://allafrica.com/stories/200411110586.html) · [Obiter](https://obiter.mandela.ac.za/article/download/12338/17311/73503)
- ZWG currency code: [ISO 4217 amendment 177](https://www.six-group.com/dam/download/financial-information/data-center/iso-currrency/amendments/dl-currency-iso-amendment-177.pdf)
- Data protection: [Cyber and Data Protection Act](https://potraz.gov.zw/wp-content/uploads/2025/02/Cyber-and-Data-Protection-Act-Chapter-1207.pdf)

Product limits, rates, the 40% affordability ratio and approval limits are illustrative design choices, not regulatory figures.

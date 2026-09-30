# Concept paper — core lending system for InsureHub Microfinance

**Prepared for:** Managing Director, InsureHub Microfinance; Group Programme Manager (fictional)
**Prepared by:** Simbarashe Nyamusa, Solutions Development · **Date:** 2026-09-30 · **Status:** Draft for approval

## 1. Background

InsureHub Microfinance lends to civil servants and salaried workers (payroll-deduction loans) and to informal traders (short-term working-capital loans). The book is illustratively ~4,000 active loans, mostly in USD. Loans are tracked in spreadsheets and a legacy desktop package; repayments arrive through payroll deduction schedules, EcoCash, bank transfers and cash at branches.

## 2. Problem statement

| Pain | Evidence (illustrative) | Consequence |
|---|---|---|
| Arrears are found late | PAR is compiled manually at month-end | collection starts 30+ days after a missed payment, when recovery odds have already fallen |
| Repayments are posted by hand | EcoCash statements re-keyed daily | posting errors, customer disputes, 2 FTE on data entry |
| No consistent credit decision | officers approve on judgement | uneven loss rates between branches |
| No link to credit life cover | cover issued on paper at the insurer | loans without cover; claims disputes on death |
| Reporting to the board and regulator takes days | spreadsheets consolidated by hand | late, error-prone returns |

## 3. Objectives

1. Every repayment posted automatically within 5 minutes of the mobile-money confirmation.
2. Arrears visible daily (days past due, PAR 30/60/90) with collections tasks created automatically.
3. A consistent, explainable credit decision with maker-checker approval limits.
4. Every loan covered by credit life from InsureHub at disbursement.
5. Month-end portfolio and provisioning reports produced in minutes, not days.

## 4. Options considered

| Option | Description | Cost (indicative) | Pros | Cons |
|---|---|---|---|---|
| 0. Do nothing | keep spreadsheets | staff time | no project | all problems remain; growth capped |
| 1. Apache Fineract | open-source core banking (Java) | hosting + integration + skills | mature, full-featured, free licence | large and complex to run; heavy customisation for payroll deduction, EcoCash and group credit-life integration; UI needs work |
| 2. SaaS lending platform | subscription core lending | per-loan/per-month fees in USD | fast start, vendor maintains | USD subscription costs; limited local integrations (EcoCash, payroll bureaus); data residency questions |
| 3. **Build a focused lending core** | Java/Spring modular monolith integrated with group systems | internal team time | fits local products and group integrations exactly; reuses the group Payments hub; group skills (Java, .NET) | build and maintenance responsibility; must get financial maths right |

## 5. Recommendation

**Option 3**, scoped tightly to the products InsureHub Microfinance actually sells, reusing the group's Payments and Notifications hub and the insurer's policy system. Re-assess Option 1 if the product range grows to deposits/savings (a full core-banking need).

## 6. Benefits (targets)

| Measure | Now | Target |
|---|---|---|
| Time to post a mobile-money repayment | next day (manual) | < 5 min (automatic) |
| Time to first collections contact after a missed instalment | ~30 days | 1 day (reminder), 7 days (call task) |
| PAR 30 | illustrative 12% | < 8% in 12 months |
| Loans disbursed without credit life | unknown | 0 |
| Month-end reporting effort | 3 days | < 1 hour |

## 7. Costs

| Item | Cost |
|---|---|
| Build (4 weeks, one senior engineer, portfolio timeline) | internal |
| Hosting | $0 on the group demo platform (production estimate in the Platform ADR-0005) |
| Integrations | reuses existing Payments/Notifications hub — no new vendor contract |

## 8. Risks

| Risk | Mitigation |
|---|---|
| Calculation errors (interest, rounding) | property-based tests; schedules reconciled to the cent; independent recalculation in tests |
| Regulatory treatment differs from assumptions | configurable rules (provisioning, penalties, in duplum cap); finance/compliance sign-off |
| Payroll deduction file formats vary by employer | adapter per format; manual upload fallback |
| Scope creep into deposits/savings | explicitly out of scope; revisit Option 1 |

## 9. Implementation approach

Four milestones (see `06-delivery-plan.md`): products & schedules → origination & approval → disbursement & repayments → end-of-day, arrears, provisioning & reporting.

## 10. Decision requested

Approve Option 3 and the four-milestone plan.

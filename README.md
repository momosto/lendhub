# LendHub — microfinance loan management system

**Status:** 📝 Planned — build starts 2026-10-08 (4 weeks) · **Stack rotation slot:** Java
**Stack:** Java 21 · Spring Boot 3 · Spring Modulith · Spring Data JPA · Flyway · PostgreSQL 16 · Spring Batch · Spring Security (OAuth2 resource server) · Spring AMQP (RabbitMQ) · springdoc-openapi · Micrometer + OpenTelemetry · Angular (standalone components, signals, Angular Material) · JUnit 5 · jqwik · Testcontainers · ArchUnit · Playwright · Gatling · GitHub Actions · Jib

**Author:** Simbarashe Nyamusa, Senior Software Engineer

LendHub is the core lending system of **InsureHub Microfinance**, the fictional lending arm of InsureHub Group. It takes a loan from application to closure: **origination, credit scoring, maker-checker approval, disbursement to EcoCash, amortisation (flat and reducing balance), repayments by mobile money and payroll deduction, arrears ageing (PAR 30/60/90), IFRS 9 staging, collections and a double-entry ledger** — with an end-of-day batch like a real bank's.

> Fictional company. Rates, fees and regulatory treatments are illustrative, loosely modelled on the Zimbabwean market.

## Why this project

- Broadens the portfolio from insurance to **banks, MFIs and fintechs** — the strongest hiring sector.
- Shows **Java/Spring Boot** at senior level (my CV's CLMS work, now public), and **Angular**, which many Zimbabwean banks run.
- Shows **financial calculations done right**: `BigDecimal`, rounding, schedules that always balance, accruals, allocation order, provisioning.
- Connects to the ecosystem: repayments through **InsureHub Integrations** (EcoCash), **credit life** cover from **InsureHub**, loan tools for the **InsureAssist** agent and **USSD**.

## Planning pack

| Document | Contents |
|---|---|
| [docs/01-concept-paper.md](docs/01-concept-paper.md) | business case, options (spreadsheets / Apache Fineract / SaaS / build), recommendation |
| [docs/02-requirements.md](docs/02-requirements.md) | products, personas, user stories with acceptance criteria, business rules, NFRs |
| [docs/03-architecture.md](docs/03-architecture.md) | C4, modules, loan state machine, calculations, EOD batch, data model, API, events |
| [docs/04-security-and-compliance.md](docs/04-security-and-compliance.md) | threat model, RBAC + maker-checker, audit, data protection, RBZ considerations |
| [docs/05-test-strategy.md](docs/05-test-strategy.md) | property-based tests for money maths, Testcontainers, load test of EOD |
| [docs/06-delivery-plan.md](docs/06-delivery-plan.md) | 4 milestones, backlog, definition of done, demo script |
| [docs/adr/](docs/adr/) | architecture decisions |

## Planned repo layout

```
lendhub/
├── api/                          Spring Boot app (Gradle, Kotlin DSL)
│   └── src/main/java/zw/insurehub/lendhub/
│       ├── borrowers/            KYC, profiles, groups
│       ├── products/             loan products & pricing
│       ├── origination/          applications, affordability, approval workflow
│       ├── scoring/              scorecard + bureau adapter
│       ├── loans/                accounts, schedules, state machine, restructures
│       ├── repayments/           allocation, payment callbacks, payroll deduction files
│       ├── collections/          DPD buckets, tasks, promises to pay
│       ├── ledger/               chart of accounts, double-entry journals
│       ├── provisioning/         IFRS 9 staging & ECL
│       ├── eod/                  Spring Batch end-of-day jobs
│       ├── reporting/            PAR, portfolio, regulatory-style returns
│       ├── integration/          Payments, InsureHub credit life, bureau, RabbitMQ externalisation
│       └── shared/               Money, Currency, clock, errors (ProblemDetails)
├── web/                          Angular back office
├── load-tests/                   Gatling simulations
├── deploy/                       kustomize manifests
└── docs/
```

## Demo accounts (planned)

| User | Role | Scenario |
|---|---|---|
| `officer@lendhub.demo` | Loan officer | capture an application for a Mbare trader |
| `manager@lendhub.demo` | Branch manager | approve up to US$1,000 (maker-checker) |
| `committee@lendhub.demo` | Credit committee | approve above US$1,000 |
| `collections@lendhub.demo` | Collections | work the PAR 30 queue |
| `finance@lendhub.demo` | Finance | run EOD, view ledger & provisions |
| `auditor@lendhub.demo` | Auditor | read-only + audit trail |

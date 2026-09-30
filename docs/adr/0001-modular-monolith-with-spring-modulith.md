# ADR-0001: Build LendHub as a modular monolith with Spring Modulith, not microservices

- **Status:** Accepted · **Date:** 2026-09-30

## Context
LendHub has ~11 business areas with strong transactional coupling (a repayment changes the loan, the schedule and the ledger atomically). One developer, one small VM (2 OCPU / 12 GB). The portfolio already shows microservices in InsureHub Integrations.

## Options
1. **Microservices per area** — independent deploys, but distributed transactions (sagas) for every repayment, more RAM, more ops.
2. **Plain layered monolith** — simplest, but boundaries erode over time.
3. **Modular monolith with Spring Modulith** — one deployable; module boundaries verified in tests; modules talk via events; any module can later be extracted.

## Decision
Option 3.

## Consequences
- ➕ ACID transactions for money movements; one JVM on a small VM.
- ➕ Boundaries are enforced (`ApplicationModules.of(...).verify()`) and documented (Modulith generates C4/PlantUML module diagrams into `docs/`).
- ➕ A different architecture style from Integrations, so the portfolio shows judgement ("I pick the style for the problem").
- ➖ All modules scale together; acceptable at MFI volumes (thousands to low millions of instalments).
- Extraction path: `integration` or `reporting` would be the first candidates to split out.

## Sources
- [Spring Modulith reference](https://docs.spring.io/spring-modulith/reference/)

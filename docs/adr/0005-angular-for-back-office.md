# ADR-0005: Use Angular for the LendHub back office

- **Status:** Accepted · **Date:** 2026-09-30

## Context
InsureHub uses React; ClaimGuard uses HTMX. Many Zimbabwean banks and enterprises run Angular, and Angular is on my CV. The back office is form- and table-heavy with role-based screens.

## Options
1. React (reuse InsureHub patterns).
2. **Angular** (standalone components, signals, Angular Material).
3. HTMX server-rendered (Thymeleaf).

## Decision
Angular. Its opinionated structure (DI, router guards, reactive forms, typed HTTP) suits enterprise back offices, and it adds a third front-end approach to the portfolio.

## Consequences
- ➕ Portfolio shows React, Angular and HTMX, each chosen for a reason.
- ➖ Heavier bundle than HTMX; irrelevant for staff users on desktop.

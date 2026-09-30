# ADR-0002: Represent money as BigDecimal in a Money value object, with explicit rounding rules

- **Status:** Accepted · **Date:** 2026-09-30

## Context
Interest, allocation and provisioning must reconcile to the cent. Floating point (`double`) cannot represent 0.10 exactly. Currencies are USD and ZWG (ISO 4217 code for ZiG, [source](https://www.six-group.com/dam/download/financial-information/data-center/iso-currrency/amendments/dl-currency-iso-amendment-177.pdf)).

## Options
1. `double` — fast, wrong.
2. `long` minor units (cents) — exact, but rates and intermediate interest need more precision, and conversions spread everywhere.
3. **`BigDecimal` inside a `Money(amount, currency)` value object** — exact decimal, explicit scale and rounding.
4. JSR 354 (Moneta) — full-featured, heavier than needed.

## Decision
Option 3:
- Stored amounts: `NUMERIC(19,2)`, scale 2. Rates: `NUMERIC(12,6)`.
- Intermediate calculations: `MathContext.DECIMAL128`, rounded **only** when a value is stored or shown.
- Instalment amounts: `HALF_UP` (what customers expect on paper); daily accruals: `HALF_EVEN` (banker's rounding, avoids drift over many days).
- Rounding residue always goes to the **last** instalment so Σ principal = principal exactly.
- `Money` operations reject mixed currencies.

## Consequences
- ➕ Provably balanced schedules (property tests).
- ➖ More verbose than primitives; mitigated by the value object API (`plus`, `times`, `allocate`).

# ADR-0003: Use Spring Batch for end-of-day processing

- **Status:** Accepted · **Date:** 2026-09-30

## Context
Every night LendHub must accrue interest, apply penalties, age arrears and (at month-end) calculate provisions across the whole book. It must be restartable, must never run twice for the same date, and must handle 1M+ instalment rows (matching my CV's high-volume processing work).

## Options
1. `@Scheduled` methods with hand-written loops — simple, but no restart, no chunking, no run history.
2. **Spring Batch** — chunk-oriented processing, partitioning, restartability and a job repository.
3. Database stored procedures — fast, but business rules end up split between Java and SQL and are hard to test.

## Decision
Spring Batch, with one job (`eodJob`) of ordered steps, chunk size 1,000, partitioned by loan ID range, and the job parameter `businessDate` as the identity (a completed instance cannot re-run).

## Consequences
- ➕ Restart from the failed chunk; full run history for auditors.
- ➕ Business rules stay in tested Java domain code; SQL is used only for bulk reads and writes.
- ➖ Spring Batch metadata tables and learning curve; acceptable.
- The measured runtime against the 1M-row NFR is published in `docs/perf/`.

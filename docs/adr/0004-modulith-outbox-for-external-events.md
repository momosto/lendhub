# ADR-0004: Publish external events through Spring Modulith's event publication registry (outbox)

- **Status:** Accepted · **Date:** 2026-09-30

## Context
`loan.disbursed`, `loan.instalment-due` and `loan.arrears-changed` must reach RabbitMQ exactly when the database transaction commits: never lost, never sent for a rolled-back change. InsureHub Integrations solved this with a hand-built transactional outbox and relay.

## Options
1. Publish to RabbitMQ inside the transaction — events get lost or sent for rolled-back changes.
2. Hand-built outbox table + relay (as in Integrations) — proven, but extra code.
3. **Spring Modulith event publication registry + `spring-modulith-events-amqp` externalisation** — the registry writes event publications in the business transaction and retries incomplete publications, which is a transactional outbox built into the framework.

## Decision
Option 3. Events annotated `@Externalized("insurehub.events::loan.disbursed")`; incomplete publications resubmitted on restart and by a scheduled task.

## Consequences
- ➕ Same guarantee as Integrations with far less code; a good interview comparison ("hand-built in .NET vs framework-provided in Spring, and when I'd choose each").
- ➖ At-least-once delivery, so consumers must stay idempotent on `eventId` (they already are).
- Event schema and routing keys follow the group event catalogue (`insurehub-platform/docs/ecosystem-architecture.md` §6).

## Sources
- [Spring Modulith — working with application events](https://docs.spring.io/spring-modulith/reference/events.html)
- [Spring blog — simplified event externalization](https://spring.io/blog/2023/09/22/simplified-event-externalization-with-spring-modulith/)
- [Baeldung — event externalization with Spring Modulith](https://www.baeldung.com/spring-modulith-event-externalization)

# ADR-0006: Implementation decisions for v0.1 (outbox listener, synchronous ledger, demo token issuer)

- **Status:** Accepted · **Date:** 2026-10-01

## Context
Building v0.1 surfaced three decisions the planning ADRs left open.

## Decisions

### 1. Externalise events with registry-backed listeners, not `@Externalized`
The group bus contract is InsureHub Integrations' envelope `{messageId, type, payload (JSON string), occurredAt, traceParent}` with routing keys from the event catalogue. `@Externalized` would serialise the domain event itself. Instead `integration.EventExternalizer` has an `@ApplicationModuleListener` per event: Spring Modulith records the publication in `event_publication` inside the business transaction and marks it complete only after `RabbitTemplate.convertAndSend` succeeds. Incomplete publications are resubmitted on restart (`republish-outstanding-events-on-restart`). This is still the transactional outbox from ADR-0004.

### 2. Post journals synchronously in the business transaction
Ledger listeners are plain `@EventListener`s. A repayment and its journal commit or roll back together, so the subledger and GL can never drift, and the EOD trial-balance step checks one consistent state. Each source event posts at most once (`UNIQUE(source_type, source_ref)`), and an unbalanced entry throws before insert.

### 3. Issue demo tokens in-app until Keycloak (platform phase 3)
LendHub is an OAuth2 resource server (HS256 `JwtDecoder`). `POST /api/v1/auth/login` issues tokens for the demo users. Moving to Keycloak replaces the decoder bean with an issuer-URI decoder and removes the login endpoint; controllers and `@PreAuthorize` rules stay the same.

### 4. Assigned UUIDs with wrapper `@Version`
Entities generate their UUID in the constructor. Versioned aggregates use `Long version` (null = new) so Spring Data persists rather than merges, and later changes to the same instance are kept. This was found by an integration test (a covered loan stayed `AWAITING_COVER`).

## Consequences
- ➕ Byte-compatible with the .NET services' bus and webhook formats; atomic financial postings; simple path to SSO.
- ➖ Events must be mapped by hand in `EventExternalizer` (one method per event); the demo issuer must never be enabled in a real deployment.

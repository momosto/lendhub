# LendHub — operations runbook

Platform-wide runbooks (backup/restore, certificates, DLQ, data breach) live in `insurehub-platform/docs/runbooks/`. This page covers LendHub-specific operations.

## Service overview

| Item | Value |
|---|---|
| Deployables | `lendhub-api` (Spring Boot, 1 replica, `Recreate` strategy), `lendhub-web` (nginx) |
| Dependencies | PostgreSQL 16 (`lendhub` database), RabbitMQ exchange `insurehub.events` (queue `lendhub`, DLQ `lendhub.dlq`) |
| Health | `/actuator/health/liveness`, `/actuator/health/readiness` (DB, and Rabbit when enabled) |
| Metrics | `/actuator/prometheus` (HTTP latency, JVM, Hikari, Spring Batch `spring.batch.*`) |
| Logs | JSON to stdout; correlate with `traceId` |
| Config | env vars (see `api/src/main/resources/application.yml`); secrets `JWT_SECRET`, `FIELD_ENCRYPTION_KEY`, `PAYMENTS_CALLBACK_SECRET`, DB password from the SOPS-encrypted secret |

## Daily: end of day

EOD runs at 00:30 Africa/Harare when `EOD_SCHEDULE_ENABLED=true`, catching up if the business date is behind. Finance can also run it from **Finance & EOD** or with `POST /api/v1/eod/runs {"days": 1}`.

**Check:** the latest row in `GET /api/v1/eod/runs` is `COMPLETED` and the business date equals today.

### EOD failed
1. Read the error in the EOD history (or `select business_date, status, error from eod.eod_run order by business_date desc limit 5;`).
2. Fix the cause (common: database unavailable, trial balance out of balance, a loan with corrupt data).
3. Run EOD again. It **restarts** the same Spring Batch job instance: completed steps are skipped and the failed step resumes from its last committed chunk. Accruals and penalties are never posted twice (journals are unique per loan and date).
4. The business date only advances after the final step, so nothing else has to be repaired.

### Trial balance out of balance (EOD stops at `trialBalanceCheck`)
This should be impossible (every journal is checked before insert). Treat it as a P1 data-integrity incident:
```sql
select e.source_type, e.source_ref, sum(l.debit) dr, sum(l.credit) cr
from ledger.journal_entry e join ledger.journal_line l on l.entry_id = e.id
group by 1, 2 having sum(l.debit) <> sum(l.credit);
```
Never edit journals. Post a correcting entry through a reviewed migration, then rerun EOD.

### EOD stuck in RUNNING after a crash
If the pod died mid-run, the `eod_run` row stays `RUNNING` and new runs get 409. After confirming no process is running:
```sql
update eod.eod_run set status = 'FAILED', error = 'pod restarted' where status = 'RUNNING';
```
Then run EOD. Spring Batch also marks the stale execution as failed on restart; if it doesn't, stop it with the Batch tables (`batch_job_execution.status = 'FAILED'`).

## Payments not arriving

| Symptom | Check | Fix |
|---|---|---|
| Callbacks 401 | `PAYMENTS_CALLBACK_SECRET` matches Payments' `Merchant:CallbackSecret`; clocks within 5 min | rotate both secrets together |
| Payments say "succeeded", loans not credited | `lendhub` queue depth and `lendhub.dlq` in RabbitMQ | inspect the DLQ message; redeliver per `dead-letter-queue.md`. Reposting is safe: repayments are unique on provider reference |
| Duplicate payment suspected | `select * from repayments.repayment where provider_reference = ?` | duplicates are impossible by constraint; a "double" usually means two genuine payments, so reverse one with `POST /repayments/{id}/reversal` |

## Outbox (events not reaching the bus)

```sql
select event_type, listener_id, publication_date from event_publication where completion_date is null order by publication_date;
```
Incomplete publications are resubmitted when the app restarts. If RabbitMQ was down, a rollout restart (`kubectl -n lendhub rollout restart deploy/lendhub-api`) flushes them. Consumers are idempotent on `messageId`.

## Credit life cover failed

Loans show `AWAITING_COVER` with `coverError`. Once InsureHub is reachable, Finance clicks **Retry credit life** (`POST /loans/{id}/credit-life`). The call is idempotent on the loan number, so a retry cannot create two policies.

## Data protection requests

- **Access request:** export the borrower, applications, loans and repayments by `customer_ref` (staff with AUDITOR role; log the request).
- **Erasure:** loans are kept 7 years after closure (illustrative retention). After that, anonymise `borrowers.borrower` (names, msisdn, address; replace national ID ciphertext). Never delete journals.
- **Breach:** follow `insurehub-platform/docs/runbooks/data-breach.md` (POTRAZ notification within 24 h). Rotating `FIELD_ENCRYPTION_KEY` needs a re-encryption job (not built yet; see the traceability backlog).

## Post-deploy smoke test

```bash
python scripts/smoke_test.py --api https://lendhub-api.<domain> --days 1
```
Use `--days 1` in shared environments: each run advances the business date.

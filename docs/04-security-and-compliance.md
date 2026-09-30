# LendHub — security & compliance

## 1. Threat model (STRIDE)

| Threat | Scenario | Control |
|---|---|---|
| Spoofing | fake payment callback credits a loan | HMAC-signed callbacks with replay window (same scheme as Integrations); repayment unique on provider reference |
| Tampering | officer edits a schedule to hide arrears | schedules immutable after disbursement; changes only via restructure (committee approval); Envers audit |
| Repudiation | manager denies approving a bad loan | approval decisions store user, time, limit applied and reason; append-only |
| Information disclosure | staff browse borrowers outside their branch | branch-scoped queries (row-level filter by `branchId` from the token); national ID masked in lists |
| Denial of service | EOD starved by API traffic | EOD at night; separate DB connection pool for batch |
| Elevation of privilege | officer approves own application | maker-checker enforced in the domain (`approver != capturer`), not just the UI; approval limits by role |
| Fraud (insider) | ghost borrowers, split loans to dodge limits | duplicate ID/phone checks; related-party exposure check (same phone/ID across applications within 30 days) flagged to committee |

## 2. Access control

| Role | Can |
|---|---|
| OFFICER | borrowers, applications (own branch) |
| MANAGER | approve ≤ US$1,000 (own branch), view branch reports |
| COMMITTEE | approve > US$1,000, restructure, write-off |
| FINANCE | disburse, payroll uploads, EOD, ledger, provisioning |
| COLLECTIONS | worklist, promises, notes |
| AUDITOR | read-only everything + audit trail |
| CHANNEL (service) | customer-scoped loan summary and repayment requests only |

Spring Security OAuth2 resource server; method security (`@PreAuthorize`) on application services — the same pattern I wired across 20 controllers on the CLMS project. Phase 3: tokens from Keycloak (group SSO).

## 3. Data protection (Cyber and Data Protection Act [Chapter 12:07])

- **Lawful basis & consent:** bureau-check consent captured with timestamp and wording version on each application.
- **Minimisation:** events carry `customerRef` and loan number, never national ID or names.
- **Security:** TLS; national ID stored encrypted at column level (JPA `AttributeConverter` with an app key from the secret store) plus a keyed hash for duplicate detection.
- **Retention:** closed loans kept 7 years (illustrative) then anonymised; rejected applications 12 months.
- **Breach:** 24-hour notification duty to POTRAZ ([Act](https://potraz.gov.zw/wp-content/uploads/2025/02/Cyber-and-Data-Protection-Act-Chapter-1207.pdf)) → platform runbook `data-breach.md`.
- **Organisational:** a real deployment needs a data-controller licence and a DPO under SI 155 of 2024 ([summary](https://www.mmmlawfirm.co.zw/understanding-si-155-of-2024-new-regulations-on-data-protection-licensing-and-data-protection-officers-in-zimbabwe/)).

## 4. Microfinance regulation (illustrative mapping)

| Requirement (Microfinance Act [Ch. 24:29], RBZ) | LendHub feature |
|---|---|
| Credit-only MFI licensed by the Registrar at the RBZ | scope limited to credit (no deposits) |
| Full disclosure of loan cost | offer shows schedule, total interest, fees, credit life, effective annual rate |
| Lending based on need and repayment capacity; avoid over-indebtedness | affordability rule (instalment ≤ 40% of disposable income), bureau check, exposure limits |
| Returns to the regulator | illustrative month-end classification/provision return (LH-82) |

Sources: [Microfinance Act (ZimLII)](https://zimlii.org/akn/zw/act/2013/3/eng@2019-11-19/source.pdf) · [Richtmann — new microfinance law](https://www.richtmann.org/journal/index.php/mjss/article/download/9955/9588/38638) · [RBZ licensing requirements 2023](https://www.rbz.co.zw/documents/BLSS/2023/COMFIs_-_Minimum_Licensing_Requirements_2023.pdf). Rates, limits and return layouts here are illustrative; confirm with compliance before real use.

## 5. Secure development

- OWASP ASVS L2 checklist in the PR template; CodeQL (Java + TypeScript), gitleaks, Dependabot, Trivy image scan via the platform's reusable workflow.
- Angular: no `innerHTML` bindings with user data; strict CSP from Cloudflare Pages headers; tokens in memory (not localStorage).

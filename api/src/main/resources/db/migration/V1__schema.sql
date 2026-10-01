-- LendHub schema. One PostgreSQL schema per module (module-owned data, docs/03-architecture.md §2).
-- Money: numeric(19,2). Rates: numeric(12,6). Times: timestamptz (UTC). Dates: business dates.

create schema if not exists products;
create schema if not exists borrowers;
create schema if not exists origination;
create schema if not exists loans;
create schema if not exists repayments;
create schema if not exists collections;
create schema if not exists ledger;
create schema if not exists provisioning;
create schema if not exists eod;
create schema if not exists audit;

-- ---------------------------------------------------------------- products
create table products.loan_product (
    id                                  uuid primary key,
    code                                varchar(40)   not null unique,
    name                                varchar(100)  not null,
    method                              varchar(30)   not null,
    frequencies                         varchar(40)   not null,
    monthly_interest_rate               numeric(12,6) not null,
    establishment_fee_rate              numeric(12,6) not null,
    credit_life_monthly_rate            numeric(12,6) not null,
    penalty_monthly_rate                numeric(12,6) not null,
    settlement_fee_rate                 numeric(12,6) not null,
    grace_days                          integer       not null,
    min_amount                          numeric(19,2) not null,
    max_amount                          numeric(19,2) not null,
    min_term_months                     integer       not null,
    max_term_months                     integer       not null,
    group_lending                       boolean       not null,
    in_duplum_enabled                   boolean       not null,
    max_instalment_to_disposable_income numeric(12,6) not null
);

create table products.public_holiday (
    holiday_date date primary key,
    name         varchar(100) not null
);

-- ---------------------------------------------------------------- borrowers
create sequence borrowers.customer_ref_seq start 1001;

create table borrowers.borrower (
    id                     uuid primary key,
    customer_ref           varchar(20)  not null unique,
    first_name             varchar(60)  not null,
    last_name              varchar(60)  not null,
    national_id            text         not null,          -- AES-GCM ciphertext
    national_id_hash       varchar(64)  not null unique,   -- keyed hash for duplicate checks
    msisdn                 varchar(15)  not null,
    date_of_birth          date,
    address                varchar(200),
    branch                 varchar(40)  not null,
    employment_type        varchar(20)  not null,
    employer               varchar(100),
    years_in_employment    numeric(5,2),
    bureau_consent_at      timestamptz  not null,
    bureau_consent_version varchar(40)  not null,
    created_by             varchar(100) not null,
    created_at             timestamptz  not null,
    version                bigint       not null default 0
);
create index ix_borrower_msisdn on borrowers.borrower (msisdn);
create index ix_borrower_branch on borrowers.borrower (branch);

create table borrowers.loan_group (
    id             uuid primary key,
    name           varchar(100) not null,
    branch         varchar(40),
    chairperson_id uuid         not null references borrowers.borrower (id),
    active         boolean      not null,
    created_by     varchar(100),
    created_at     timestamptz
);

create table borrowers.group_member (
    group_id    uuid not null references borrowers.loan_group (id),
    borrower_id uuid not null references borrowers.borrower (id),
    primary key (group_id, borrower_id)
);

-- ---------------------------------------------------------------- origination
create sequence origination.application_seq start 1001;

create table origination.loan_application (
    id                          uuid primary key,
    application_number          varchar(20)   not null unique,
    borrower_id                 uuid          not null,
    customer_ref                varchar(20)   not null,
    borrower_name               varchar(130),
    msisdn                      varchar(15),
    branch                      varchar(40)   not null,
    product_code                varchar(40)   not null,
    currency                    varchar(3)    not null,
    amount                      numeric(19,2) not null,
    instalments                 integer       not null,
    frequency                   varchar(10)   not null,
    purpose                     varchar(200),
    monthly_income              numeric(19,2) not null,
    monthly_expenses            numeric(19,2) not null,
    other_debt_repayments       numeric(19,2) not null,
    status                      varchar(20)   not null,
    captured_by                 varchar(100)  not null,
    created_at                  timestamptz   not null,
    submitted_at                timestamptz,
    monthly_instalment_estimate numeric(19,2),
    disposable_income           numeric(19,2),
    affordability_ratio         numeric(12,4),
    affordable                  boolean       not null default false,
    score_points                integer,
    grade                       varchar(1),
    score_reasons               varchar(4000),
    bureau_status               varchar(20),
    bureau_reference            varchar(40),
    related_party_flag          boolean       not null default false,
    group_id                    uuid,
    decided_by                  varchar(100),
    decided_at                  timestamptz,
    decline_reason              varchar(40),
    offer_instalment            numeric(19,2),
    offer_total_interest        numeric(19,2),
    offer_establishment_fee     numeric(19,2),
    offer_total_credit_life     numeric(19,2),
    offer_total_repayable       numeric(19,2),
    offer_net_disbursed         numeric(19,2),
    offer_effective_annual_rate numeric(12,2),
    offer_expires_on            date,
    otp_hash                    varchar(64),
    otp_attempts                integer       not null default 0,
    accepted_at                 timestamptz,
    version                     bigint        not null default 0
);
create index ix_application_branch on origination.loan_application (branch, created_at desc);
create index ix_application_offer on origination.loan_application (offer_expires_on) where status = 'OFFERED';

create table origination.approval_decision (
    id                uuid primary key,
    application_id    uuid         not null references origination.loan_application (id),
    decision          varchar(10)  not null,
    reason_code       varchar(40),
    comment           varchar(500),
    decided_by        varchar(100) not null,
    role              varchar(20)  not null,
    limit_applied_usd numeric(19,2),
    amount_usd        numeric(19,2) not null,
    decided_at        timestamptz  not null
);

-- ---------------------------------------------------------------- loans
create sequence loans.loan_number_seq start 1;

create table loans.loan_account (
    id                        uuid primary key,
    loan_number               varchar(20)   not null unique,
    application_id            uuid          not null unique,
    application_number        varchar(20),
    borrower_id               uuid          not null,
    customer_ref              varchar(20)   not null,
    borrower_name             varchar(130),
    msisdn                    varchar(15),
    branch                    varchar(40)   not null,
    product_code              varchar(40)   not null,
    currency                  varchar(3)    not null,
    principal                 numeric(19,2) not null,
    method                    varchar(30)   not null,
    frequency                 varchar(10)   not null,
    instalment_count          integer       not null,
    penalty_monthly_rate      numeric(12,6) not null,
    grace_days                integer       not null,
    in_duplum_enabled         boolean       not null,
    settlement_fee_rate       numeric(12,6) not null,
    establishment_fee         numeric(19,2),
    net_disbursed             numeric(19,2),
    status                    varchar(20)   not null,
    credit_life_policy_number varchar(40),
    cover_error               varchar(500),
    disbursement_reference    varchar(40),
    disbursed_on              date,
    maturity_date             date,
    days_past_due             integer       not null default 0,
    worst_days_past_due       integer       not null default 0,
    arrears_bucket            varchar(20)   not null,
    restructured              boolean       not null default false,
    restructured_on           date,
    written_off_on            date,
    closed_on                 date,
    credit_balance            numeric(19,2) not null default 0,
    captured_by               varchar(100),
    approved_by               varchar(100),
    disbursed_by              varchar(100),
    created_at                timestamptz   not null,
    version                   bigint        not null default 0
);
-- EOD and dashboards read the live book only (partial index keeps it small as closed loans accumulate)
create index ix_loan_servicing on loans.loan_account (status) where status in ('ACTIVE', 'IN_ARREARS', 'RESTRUCTURED');
create index ix_loan_customer on loans.loan_account (customer_ref);
create index ix_loan_borrower on loans.loan_account (borrower_id);

create table loans.instalment (
    id               uuid primary key,
    loan_id          uuid          not null references loans.loan_account (id),
    seq              integer       not null,
    period_start     date          not null,
    due_date         date          not null,
    principal_due    numeric(19,2) not null,
    interest_due     numeric(19,2) not null,
    credit_life_due  numeric(19,2) not null,
    fees_due         numeric(19,2) not null,
    penalty_due      numeric(19,2) not null,
    principal_paid   numeric(19,2) not null,
    interest_paid    numeric(19,2) not null,
    credit_life_paid numeric(19,2) not null,
    fees_paid        numeric(19,2) not null,
    penalty_paid     numeric(19,2) not null,
    interest_accrued numeric(19,2) not null,
    state            varchar(10)   not null,
    paid_on          date,
    unique (loan_id, seq)
);
create index ix_instalment_loan_due on loans.instalment (loan_id, due_date);
create index ix_instalment_unpaid_due on loans.instalment (due_date) where state = 'OPEN';

create table loans.loan_change_request (
    id              uuid primary key,
    loan_id         uuid         not null references loans.loan_account (id),
    loan_number     varchar(20)  not null,
    type            varchar(20)  not null,
    new_instalments integer,
    reason          varchar(500) not null,
    requested_by    varchar(100) not null,
    requested_at    timestamptz  not null,
    status          varchar(10)  not null,
    decided_by      varchar(100),
    decided_at      timestamptz
);

-- ---------------------------------------------------------------- repayments
create table repayments.repayment (
    id                 uuid primary key,
    loan_id            uuid          not null,
    loan_number        varchar(20)   not null,
    provider_reference varchar(100)  not null unique,   -- idempotency for callbacks and payroll lines
    channel            varchar(20)   not null,
    type               varchar(20)   not null,
    currency           varchar(3)    not null,
    amount             numeric(19,2) not null,
    credit_added       numeric(19,2) not null,
    value_date         date          not null,
    received_at        timestamptz   not null,
    status             varchar(10)   not null,
    posted_by          varchar(100),
    reversal_reason    varchar(500),
    reversed_by        varchar(100),
    reversed_at        timestamptz
);
create index ix_repayment_loan on repayments.repayment (loan_id, value_date);

create table repayments.allocation (
    id             uuid primary key,
    repayment_id   uuid          references repayments.repayment (id),
    instalment_seq integer       not null,
    component      varchar(20)   not null,
    amount         numeric(19,2) not null
);

create table repayments.payroll_batch (
    id           uuid primary key,
    file_name    varchar(200),
    uploaded_by  varchar(100),
    uploaded_at  timestamptz   not null,
    matched      integer       not null,
    unmatched    integer       not null,
    total_posted numeric(19,2) not null
);

create table repayments.payroll_line (
    id                 uuid primary key,
    batch_id           uuid          references repayments.payroll_batch (id),
    line_no            integer       not null,
    employer           varchar(100),
    national_id_masked varchar(20),
    amount             numeric(19,2) not null,
    period             varchar(20),
    status             varchar(10)   not null,
    loan_number        varchar(20),
    message            varchar(200)
);

create table repayments.payment_request (
    id              uuid primary key,
    idempotency_key varchar(100)  not null unique,
    loan_id         uuid          not null,
    loan_number     varchar(20)   not null,
    msisdn          varchar(15)   not null,
    currency        varchar(3)    not null,
    amount          numeric(19,2) not null,
    payment_id      varchar(64),
    status          varchar(20)   not null,
    requested_by    varchar(100),
    requested_at    timestamptz   not null
);

-- ---------------------------------------------------------------- collections
create table collections.collection_task (
    id              uuid primary key,
    loan_id         uuid          not null,
    loan_number     varchar(20)   not null,
    customer_ref    varchar(20),
    borrower_name   varchar(130),
    type            varchar(20)   not null,
    dpd_at_creation integer       not null,
    currency        varchar(3)    not null,
    arrears_amount  numeric(19,2) not null,
    priority        numeric(19,2) not null,
    status          varchar(10)   not null,
    created_on      date          not null,
    notes           text,
    completed_by    varchar(100),
    completed_at    timestamptz
);
create index ix_task_open on collections.collection_task (priority desc) where status = 'OPEN';
create index ix_task_loan on collections.collection_task (loan_id, type, created_on);

create table collections.promise_to_pay (
    id            uuid primary key,
    loan_id       uuid          not null,
    loan_number   varchar(20)   not null,
    task_id       uuid,
    promised_on   date          not null,
    promised_date date          not null,
    currency      varchar(3)    not null,
    amount        numeric(19,2) not null,
    status        varchar(10)   not null,
    created_by    varchar(100),
    created_at    timestamptz   not null
);

-- ---------------------------------------------------------------- ledger
create table ledger.gl_account (
    code varchar(10) primary key,
    name varchar(100) not null,
    type varchar(20)  not null
);

create table ledger.journal_entry (
    id            uuid primary key,
    source_type   varchar(30)  not null,
    source_ref    varchar(100) not null,
    business_date date         not null,
    posted_at     timestamptz  not null,
    currency      varchar(3)   not null,
    loan_number   varchar(20),
    description   varchar(200),
    unique (source_type, source_ref)                   -- each business event posts once
);
create index ix_journal_loan on ledger.journal_entry (loan_number);

create table ledger.journal_line (
    id           uuid primary key,
    entry_id     uuid          not null references ledger.journal_entry (id),
    account_code varchar(10)   not null references ledger.gl_account (code),
    debit        numeric(19,2) not null,
    credit       numeric(19,2) not null,
    posted_at    timestamptz   not null,
    check (debit >= 0 and credit >= 0 and (debit = 0 or credit = 0))
);
-- large, append-only table: BRIN is tiny and good enough for time-range scans
create index ix_journal_line_posted on ledger.journal_line using brin (posted_at);
create index ix_journal_line_entry on ledger.journal_line (entry_id);

-- ---------------------------------------------------------------- provisioning
create table provisioning.provision_run (
    id             uuid primary key,
    run_group      uuid          not null,
    as_of          date          not null,
    currency       varchar(3)    not null,
    loans          integer       not null,
    stage1_ecl     numeric(19,2) not null,
    stage2_ecl     numeric(19,2) not null,
    stage3_ecl     numeric(19,2) not null,
    total_ecl      numeric(19,2) not null,
    previous_total numeric(19,2) not null,
    movement       numeric(19,2) not null,
    run_by         varchar(100),
    created_at     timestamptz   not null
);

create table provisioning.stage_assignment (
    id            uuid primary key,
    run_id        uuid          not null references provisioning.provision_run (id),
    loan_id       uuid          not null,
    loan_number   varchar(20)   not null,
    stage         varchar(10)   not null,
    days_past_due integer       not null,
    restructured  boolean       not null,
    ead           numeric(19,2) not null,
    pd            numeric(12,6) not null,
    lgd           numeric(12,6) not null,
    ecl           numeric(19,2) not null
);
create index ix_stage_loan on provisioning.stage_assignment (loan_id);

-- ---------------------------------------------------------------- eod
create table eod.business_date (
    id            integer primary key check (id = 1),
    business_date date not null
);

create table eod.eod_run (
    id               uuid primary key,
    business_date    date         not null unique,     -- one EOD per date
    status           varchar(10)  not null,
    started_by       varchar(100),
    started_at       timestamptz  not null,
    finished_at      timestamptz,
    job_execution_id bigint,
    loans_processed  integer      not null default 0,
    arrears_changes  integer      not null default 0,
    tasks_created    integer      not null default 0,
    reminders_sent   integer      not null default 0,
    offers_expired   integer      not null default 0,
    promises_broken  integer      not null default 0,
    month_end        boolean      not null default false,
    error            varchar(2000)
);

-- ---------------------------------------------------------------- audit (Hibernate Envers)
create sequence audit.revinfo_seq start 1 increment 1;

create table audit.revinfo (
    rev      bigint primary key,
    revtstmp bigint not null,
    username varchar(100)
);

create table audit.borrower_aud (
    id                     uuid     not null,
    rev                    bigint   not null references audit.revinfo (rev),
    revtype                smallint,
    customer_ref           varchar(20),
    first_name             varchar(60),
    last_name              varchar(60),
    national_id            text,
    national_id_hash       varchar(64),
    msisdn                 varchar(15),
    date_of_birth          date,
    address                varchar(200),
    branch                 varchar(40),
    employment_type        varchar(20),
    employer               varchar(100),
    years_in_employment    numeric(5,2),
    bureau_consent_at      timestamptz,
    bureau_consent_version varchar(40),
    created_by             varchar(100),
    created_at             timestamptz,
    primary key (id, rev)
);

create table audit.loan_application_aud (
    id                          uuid     not null,
    rev                         bigint   not null references audit.revinfo (rev),
    revtype                     smallint,
    application_number          varchar(20),
    borrower_id                 uuid,
    customer_ref                varchar(20),
    borrower_name               varchar(130),
    msisdn                      varchar(15),
    branch                      varchar(40),
    product_code                varchar(40),
    currency                    varchar(3),
    amount                      numeric(19,2),
    instalments                 integer,
    frequency                   varchar(10),
    purpose                     varchar(200),
    monthly_income              numeric(19,2),
    monthly_expenses            numeric(19,2),
    other_debt_repayments       numeric(19,2),
    status                      varchar(20),
    captured_by                 varchar(100),
    created_at                  timestamptz,
    submitted_at                timestamptz,
    monthly_instalment_estimate numeric(19,2),
    disposable_income           numeric(19,2),
    affordability_ratio         numeric(12,4),
    affordable                  boolean,
    score_points                integer,
    grade                       varchar(1),
    score_reasons               varchar(4000),
    bureau_status               varchar(20),
    bureau_reference            varchar(40),
    related_party_flag          boolean,
    group_id                    uuid,
    decided_by                  varchar(100),
    decided_at                  timestamptz,
    decline_reason              varchar(40),
    offer_instalment            numeric(19,2),
    offer_total_interest        numeric(19,2),
    offer_establishment_fee     numeric(19,2),
    offer_total_credit_life     numeric(19,2),
    offer_total_repayable       numeric(19,2),
    offer_net_disbursed         numeric(19,2),
    offer_effective_annual_rate numeric(12,2),
    offer_expires_on            date,
    otp_hash                    varchar(64),
    otp_attempts                integer,
    accepted_at                 timestamptz,
    primary key (id, rev)
);

create table audit.loan_account_aud (
    id                        uuid     not null,
    rev                       bigint   not null references audit.revinfo (rev),
    revtype                   smallint,
    loan_number               varchar(20),
    application_id            uuid,
    application_number        varchar(20),
    borrower_id               uuid,
    customer_ref              varchar(20),
    borrower_name             varchar(130),
    msisdn                    varchar(15),
    branch                    varchar(40),
    product_code              varchar(40),
    currency                  varchar(3),
    principal                 numeric(19,2),
    method                    varchar(30),
    frequency                 varchar(10),
    instalment_count          integer,
    penalty_monthly_rate      numeric(12,6),
    grace_days                integer,
    in_duplum_enabled         boolean,
    settlement_fee_rate       numeric(12,6),
    establishment_fee         numeric(19,2),
    net_disbursed             numeric(19,2),
    status                    varchar(20),
    credit_life_policy_number varchar(40),
    cover_error               varchar(500),
    disbursement_reference    varchar(40),
    disbursed_on              date,
    maturity_date             date,
    days_past_due             integer,
    worst_days_past_due       integer,
    arrears_bucket            varchar(20),
    restructured              boolean,
    restructured_on           date,
    written_off_on            date,
    closed_on                 date,
    credit_balance            numeric(19,2),
    captured_by               varchar(100),
    approved_by               varchar(100),
    disbursed_by              varchar(100),
    created_at                timestamptz,
    primary key (id, rev)
);

-- ---------------------------------------------------------------- Spring Modulith event publication registry (outbox)
create table public.event_publication (
    id               uuid primary key,
    listener_id      varchar(512) not null,
    event_type       varchar(512) not null,
    serialized_event text         not null,
    publication_date timestamptz  not null,
    completion_date  timestamptz
);
create index ix_event_publication_incomplete on public.event_publication (publication_date) where completion_date is null;

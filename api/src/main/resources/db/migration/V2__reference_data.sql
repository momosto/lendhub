-- Reference data. Rates, fees and limits are illustrative design choices for a fictional MFI, not market quotes.

insert into products.loan_product (id, code, name, method, frequencies, monthly_interest_rate, establishment_fee_rate,
    credit_life_monthly_rate, penalty_monthly_rate, settlement_fee_rate, grace_days, min_amount, max_amount,
    min_term_months, max_term_months, group_lending, in_duplum_enabled, max_instalment_to_disposable_income)
values
    ('7d1b6f2e-0001-4c3a-9a51-000000000001', 'SALARY_ADVANCE', 'Salary Advance Loan', 'FLAT', 'MONTHLY',
     0.045000, 0.030000, 0.005000, 0.050000, 0.010000, 3, 100.00, 3000.00, 3, 24, false, true, 0.400000),
    ('7d1b6f2e-0002-4c3a-9a51-000000000002', 'TRADER', 'Trader Loan', 'REDUCING_BALANCE', 'WEEKLY,MONTHLY',
     0.080000, 0.040000, 0.006000, 0.050000, 0.000000, 3, 50.00, 1500.00, 1, 6, false, true, 0.400000),
    ('7d1b6f2e-0003-4c3a-9a51-000000000003', 'GROUP', 'Group (solidarity) Loan', 'REDUCING_BALANCE', 'WEEKLY',
     0.070000, 0.030000, 0.006000, 0.050000, 0.000000, 3, 50.00, 500.00, 1, 6, true, true, 0.400000);

-- Zimbabwe public holidays (Public Holidays and Prohibition of Business Act); Sunday holidays move to Monday.
insert into products.public_holiday (holiday_date, name) values
    ('2026-01-01', 'New Year''s Day'),
    ('2026-02-21', 'Robert Gabriel Mugabe National Youth Day'),
    ('2026-04-03', 'Good Friday'),
    ('2026-04-04', 'Holy Saturday'),
    ('2026-04-06', 'Easter Monday'),
    ('2026-04-18', 'Independence Day'),
    ('2026-05-01', 'Workers'' Day'),
    ('2026-05-25', 'Africa Day'),
    ('2026-08-10', 'Heroes'' Day'),
    ('2026-08-11', 'Defence Forces Day'),
    ('2026-12-22', 'National Unity Day'),
    ('2026-12-25', 'Christmas Day'),
    ('2026-12-26', 'Boxing Day'),
    ('2027-01-01', 'New Year''s Day'),
    ('2027-02-22', 'Robert Gabriel Mugabe National Youth Day (observed)'),
    ('2027-03-26', 'Good Friday'),
    ('2027-03-27', 'Holy Saturday'),
    ('2027-03-29', 'Easter Monday'),
    ('2027-04-19', 'Independence Day (observed)'),
    ('2027-05-01', 'Workers'' Day'),
    ('2027-05-25', 'Africa Day'),
    ('2027-08-09', 'Heroes'' Day'),
    ('2027-08-10', 'Defence Forces Day'),
    ('2027-12-22', 'National Unity Day'),
    ('2027-12-25', 'Christmas Day'),
    ('2027-12-27', 'Boxing Day (observed)');

insert into ledger.gl_account (code, name, type) values
    ('1000', 'Cash at bank and mobile-money float', 'ASSET'),
    ('1100', 'Loans receivable - principal', 'ASSET'),
    ('1110', 'Interest receivable', 'ASSET'),
    ('1120', 'Penalty interest receivable', 'ASSET'),
    ('1190', 'Loan loss provision (IFRS 9 ECL)', 'ASSET'),
    ('2100', 'Credit life premiums payable to InsureHub', 'LIABILITY'),
    ('2200', 'Customer credit balances (overpayments)', 'LIABILITY'),
    ('4000', 'Interest income', 'INCOME'),
    ('4100', 'Fee income', 'INCOME'),
    ('4200', 'Penalty interest income', 'INCOME'),
    ('4300', 'Recoveries on written-off loans', 'INCOME'),
    ('5000', 'Impairment expense', 'EXPENSE');

insert into eod.business_date (id, business_date) values (1, current_date);

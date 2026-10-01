package zw.insurehub.lendhub.ledger;

import static zw.insurehub.lendhub.ledger.GlAccount.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import zw.insurehub.lendhub.loans.LoanEvents;
import zw.insurehub.lendhub.provisioning.ProvisionCalculated;
import zw.insurehub.lendhub.repayments.RepaymentEvents;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;

/**
 * Double-entry ledger (LH-72). Listeners run synchronously inside the business transaction, so a loan event and its
 * journal commit or roll back together. Every entry is balanced, and each source event posts at most once.
 */
@Service
@Transactional
public class LedgerService {

    public record TrialBalanceRow(String accountCode, String accountName, GlAccount.Type type, CurrencyCode currency,
            BigDecimal debits, BigDecimal credits, BigDecimal balance) {
    }

    public record TrialBalance(List<TrialBalanceRow> rows, Map<CurrencyCode, BigDecimal> totalDebits,
            Map<CurrencyCode, BigDecimal> totalCredits, boolean balanced) {
    }

    private final JournalEntryRepository journals;
    private final GlAccountRepository accounts;
    private final JdbcTemplate jdbc;

    LedgerService(JournalEntryRepository journals, GlAccountRepository accounts, JdbcTemplate jdbc) {
        this.journals = journals;
        this.accounts = accounts;
        this.jdbc = jdbc;
    }

    /** Collects debit and credit lines, skipping zero amounts. */
    private static final class Lines {
        private final List<JournalEntry.JournalLine> lines = new ArrayList<>();
        private final Instant now = Instant.now();

        Lines dr(String account, Money amount) {
            if (amount.isPositive()) lines.add(new JournalEntry.JournalLine(account, amount.amount(), BigDecimal.ZERO.setScale(2), now));
            else if (amount.isNegative()) cr(account, Money.zero(amount.currency()).minus(amount));
            return this;
        }

        Lines cr(String account, Money amount) {
            if (amount.isPositive()) lines.add(new JournalEntry.JournalLine(account, BigDecimal.ZERO.setScale(2), amount.amount(), now));
            else if (amount.isNegative()) dr(account, Money.zero(amount.currency()).minus(amount));
            return this;
        }
    }

    private void post(String sourceType, String sourceRef, LocalDate date, CurrencyCode ccy, String loanNumber,
            String description, Lines lines) {
        if (lines.lines.isEmpty() || journals.existsBySourceTypeAndSourceRef(sourceType, sourceRef)) return;
        journals.save(new JournalEntry(sourceType, sourceRef, date, ccy, loanNumber, description, lines.lines));
    }

    @EventListener
    void on(LoanEvents.LoanDisbursed e) {
        post("DISBURSEMENT", e.loanId().toString(), e.disbursedOn(), e.principal().currency(), e.loanNumber(),
                "Disbursement to EcoCash, establishment fee deducted",
                new Lines().dr(LOANS_PRINCIPAL, e.principal()).cr(CASH, e.netDisbursed()).cr(FEE_INCOME, e.establishmentFee()));
    }

    @EventListener
    void on(LoanEvents.InterestAccrued e) {
        post("ACCRUAL", e.loanId() + ":" + e.date(), e.date(), e.amount().currency(), e.loanNumber(), "Daily interest accrual",
                new Lines().dr(INTEREST_RECEIVABLE, e.amount()).cr(INTEREST_INCOME, e.amount()));
    }

    @EventListener
    void on(LoanEvents.PenaltyCharged e) {
        post("PENALTY", e.loanId() + ":" + e.date(), e.date(), e.amount().currency(), e.loanNumber(), "Penalty interest on arrears",
                new Lines().dr(PENALTY_RECEIVABLE, e.amount()).cr(PENALTY_INCOME, e.amount()));
    }

    @EventListener
    void on(LoanEvents.CreditApplied e) {
        var b = e.breakdown();
        post("CREDIT_APPLIED", e.loanId() + ":" + e.date(), e.date(), b.principal().currency(), e.loanNumber(),
                "Credit balance applied to due instalments",
                creditComponents(new Lines().dr(CUSTOMER_CREDIT, b.total()), b));
    }

    @EventListener
    void on(RepaymentEvents.RepaymentAllocated e) {
        var lines = new Lines().dr(CASH, e.amount());
        if (e.recovery()) {
            lines.cr(RECOVERIES, e.amount());
        } else {
            creditComponents(lines, e.breakdown()).cr(CUSTOMER_CREDIT, e.creditAdded());
        }
        post("REPAYMENT", e.repaymentId().toString(), e.valueDate(), e.amount().currency(), e.loanNumber(),
                (e.recovery() ? "Recovery on written-off loan via " : "Repayment via ") + e.channel(), lines);
    }

    @EventListener
    void on(RepaymentEvents.RepaymentReversed e) {
        Money zero = Money.zero(e.amount().currency());
        var lines = new Lines().cr(CASH, e.amount());
        if (e.recovery()) {
            lines.dr(RECOVERIES, e.amount());
        } else {
            var b = e.breakdown();
            lines.dr(PENALTY_RECEIVABLE, b.penalty()).dr(FEE_INCOME, b.fee()).dr(CREDIT_LIFE_PAYABLE, b.creditLife())
                    .dr(INTEREST_RECEIVABLE, b.interest()).dr(LOANS_PRINCIPAL, b.principal())
                    .dr(CUSTOMER_CREDIT, e.creditAdded().max(zero));
        }
        post("REVERSAL", e.repaymentId().toString(), e.valueDate(), e.amount().currency(), e.loanNumber(),
                "Reversal of repayment", lines);
    }

    @EventListener
    void on(LoanEvents.LoanWrittenOff e) {
        Money total = e.principal().plus(e.interest()).plus(e.penalties());
        post("WRITE_OFF", e.loanId().toString(), e.date(), total.currency(), e.loanNumber(), "Write-off approved by committee",
                new Lines().dr(IMPAIRMENT_EXPENSE, total).cr(LOANS_PRINCIPAL, e.principal())
                        .cr(INTEREST_RECEIVABLE, e.interest()).cr(PENALTY_RECEIVABLE, e.penalties()));
    }

    @EventListener
    void on(ProvisionCalculated e) {
        post("PROVISION", e.runId() + ":" + e.movement().currency(), e.asOf(), e.movement().currency(), null,
                "IFRS 9 ECL provision movement", new Lines().dr(IMPAIRMENT_EXPENSE, e.movement()).cr(LOAN_LOSS_PROVISION, e.movement()));
    }

    private static Lines creditComponents(Lines lines, LoanEvents.Breakdown b) {
        return lines.cr(PENALTY_RECEIVABLE, b.penalty()).cr(FEE_INCOME, b.fee()).cr(CREDIT_LIFE_PAYABLE, b.creditLife())
                .cr(INTEREST_RECEIVABLE, b.interest()).cr(LOANS_PRINCIPAL, b.principal());
    }

    // ---- queries -----------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TrialBalance trialBalance() {
        var names = new TreeMap<String, GlAccount>();
        accounts.findAll().forEach(a -> names.put(a.getCode(), a));
        List<TrialBalanceRow> rows = jdbc.query("""
                select l.account_code, e.currency, sum(l.debit) dr, sum(l.credit) cr
                from ledger.journal_line l join ledger.journal_entry e on e.id = l.entry_id
                group by l.account_code, e.currency order by e.currency, l.account_code""",
                (rs, i) -> {
                    var account = names.get(rs.getString(1));
                    BigDecimal dr = rs.getBigDecimal(3);
                    BigDecimal cr = rs.getBigDecimal(4);
                    return new TrialBalanceRow(rs.getString(1), account == null ? "?" : account.getName(),
                            account == null ? null : account.getType(), CurrencyCode.valueOf(rs.getString(2)), dr, cr, dr.subtract(cr));
                });
        Map<CurrencyCode, BigDecimal> drs = new TreeMap<>();
        Map<CurrencyCode, BigDecimal> crs = new TreeMap<>();
        for (var r : rows) {
            drs.merge(r.currency(), r.debits(), BigDecimal::add);
            crs.merge(r.currency(), r.credits(), BigDecimal::add);
        }
        boolean balanced = drs.keySet().stream().allMatch(c -> drs.get(c).compareTo(crs.get(c)) == 0);
        return new TrialBalance(rows, drs, crs, balanced);
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> journals(String loanNumber, int limit) {
        return loanNumber != null ? journals.findByLoanNumberOrderByPostedAt(loanNumber)
                : journals.findAllByOrderByPostedAtDesc(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<GlAccount> chartOfAccounts() {
        return accounts.findAll(org.springframework.data.domain.Sort.by("code"));
    }
}

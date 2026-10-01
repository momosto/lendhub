package zw.insurehub.lendhub.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import zw.insurehub.lendhub.loans.LoanAccount;
import zw.insurehub.lendhub.loans.LoanServicing;
import zw.insurehub.lendhub.loans.LoanServicing.LoanSnapshot;
import zw.insurehub.lendhub.loans.domain.ArrearsBucket;
import zw.insurehub.lendhub.provisioning.ProvisioningService;
import zw.insurehub.lendhub.provisioning.domain.Ifrs9;
import zw.insurehub.lendhub.repayments.RepaymentService;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.time.BusinessDateProvider;

/** Read models for the dashboard and month-end returns (LH-80, LH-82). Reads other modules only through their APIs. */
@Service
@Transactional(readOnly = true)
public class ReportingService {

    public record ParLine(String key, int loans, BigDecimal portfolio, BigDecimal par1, BigDecimal par30, BigDecimal par60,
            BigDecimal par90, BigDecimal par30Percent) {
    }

    public record ParReport(LocalDate asOf, CurrencyCode currency, ParLine total, List<ParLine> byBranch,
            List<ParLine> byProduct, List<ParLine> byOfficer, Map<ArrearsBucket, BigDecimal> buckets) {
    }

    public record Portfolio(LocalDate asOf, CurrencyCode currency, int activeLoans, BigDecimal grossLoanPortfolio,
            int disbursedMtdCount, BigDecimal disbursedMtdAmount, BigDecimal dueMtd, BigDecimal collectedMtd,
            BigDecimal collectionEfficiencyPercent, BigDecimal par30Percent, Map<String, Integer> loansByStatus) {
    }

    public record ReturnLine(String classification, int loans, BigDecimal outstandingPrincipal, BigDecimal provision) {
    }

    private final LoanServicing loans;
    private final RepaymentService repayments;
    private final ProvisioningService provisioning;
    private final BusinessDateProvider businessDate;
    private final JdbcTemplate jdbc;

    ReportingService(LoanServicing loans, RepaymentService repayments, ProvisioningService provisioning,
            BusinessDateProvider businessDate, JdbcTemplate jdbc) {
        this.loans = loans;
        this.repayments = repayments;
        this.provisioning = provisioning;
        this.businessDate = businessDate;
        this.jdbc = jdbc;
    }

    private List<LoanSnapshot> book(CurrencyCode ccy) {
        return loans.snapshots().stream().filter(l -> l.currency() == ccy)
                .filter(l -> l.status() != LoanAccount.Status.CLOSED && l.status() != LoanAccount.Status.WRITTEN_OFF)
                .toList();
    }

    /** Portfolio at risk: outstanding principal of loans more than N days past due, as value and % (LH-80). */
    public ParReport par(CurrencyCode ccy) {
        var book = book(ccy);
        Map<ArrearsBucket, BigDecimal> buckets = new EnumMap<>(ArrearsBucket.class);
        for (ArrearsBucket b : ArrearsBucket.values()) buckets.put(b, BigDecimal.ZERO.setScale(2));
        book.forEach(l -> buckets.merge(l.bucket(), l.outstandingPrincipal().amount(), BigDecimal::add));
        return new ParReport(businessDate.today(), ccy, parLine("TOTAL", book), group(book, LoanSnapshot::branch),
                group(book, LoanSnapshot::productCode), group(book, LoanSnapshot::capturedBy), buckets);
    }

    private List<ParLine> group(List<LoanSnapshot> book, Function<LoanSnapshot, String> key) {
        Map<String, List<LoanSnapshot>> groups = book.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.toList()));
        return groups.entrySet().stream().map(e -> parLine(e.getKey(), e.getValue())).toList();
    }

    static ParLine parLine(String key, List<LoanSnapshot> loans) {
        BigDecimal glp = sum(loans, 0);
        BigDecimal par30 = sum(loans, 30);
        BigDecimal pct = glp.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                : par30.multiply(BigDecimal.valueOf(100)).divide(glp, 2, RoundingMode.HALF_UP);
        return new ParLine(key, loans.size(), glp, sum(loans, 1), par30, sum(loans, 60), sum(loans, 90), pct);
    }

    /** Outstanding principal of loans with DPD ≥ threshold (threshold 0 = whole portfolio). PAR30 = DPD > 30. */
    private static BigDecimal sum(List<LoanSnapshot> loans, int over) {
        return loans.stream().filter(l -> over == 0 || (over == 1 ? l.daysPastDue() >= 1 : l.daysPastDue() > over))
                .map(l -> l.outstandingPrincipal().amount()).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public Portfolio portfolio(CurrencyCode ccy) {
        LocalDate today = businessDate.today();
        LocalDate monthStart = today.withDayOfMonth(1);
        var all = loans.snapshots().stream().filter(l -> l.currency() == ccy).toList();
        var book = book(ccy);
        var disbursedMtd = all.stream().filter(l -> !l.disbursedOn().isBefore(monthStart)).toList();
        BigDecimal dueMtd = jdbc.queryForObject("""
                select coalesce(sum(i.principal_due + i.interest_due + i.credit_life_due + i.fees_due + i.penalty_due), 0)
                from loans.instalment i join loans.loan_account l on l.id = i.loan_id
                where i.due_date between ? and ? and i.state <> 'CANCELLED' and l.currency = ?""",
                BigDecimal.class, monthStart, today, ccy.name());
        BigDecimal collected = repayments.totalReceivedBetween(monthStart, today, ccy).amount();
        BigDecimal efficiency = dueMtd.signum() == 0 ? null
                : collected.multiply(BigDecimal.valueOf(100)).divide(dueMtd, 2, RoundingMode.HALF_UP);
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        all.forEach(l -> byStatus.merge(l.status().name(), 1, Integer::sum));
        var total = parLine("TOTAL", book);
        return new Portfolio(today, ccy, book.size(), total.portfolio(), disbursedMtd.size(),
                disbursedMtd.stream().map(l -> l.principal().amount()).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add),
                dueMtd.setScale(2), collected, efficiency, total.par30Percent(), byStatus);
    }

    /**
     * Illustrative month-end classification return: performing (Stage 1), special mention (1–30 DPD),
     * substandard (Stage 2), doubtful (91–180), loss (>180) with the latest ECL per loan.
     */
    public List<ReturnLine> regulatoryReturn(CurrencyCode ccy) {
        Map<String, BigDecimal[]> rows = new LinkedHashMap<>();
        for (String c : List.of("PERFORMING", "SPECIAL_MENTION", "SUBSTANDARD", "DOUBTFUL", "LOSS")) {
            rows.put(c, new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2) });
        }
        for (var l : book(ccy)) {
            String c = l.daysPastDue() > 180 ? "LOSS" : l.daysPastDue() > 90 ? "DOUBTFUL"
                    : Ifrs9.stage(l.daysPastDue(), l.restructured(), false) == Ifrs9.Stage.STAGE_2 ? "SUBSTANDARD"
                    : l.daysPastDue() > 0 ? "SPECIAL_MENTION" : "PERFORMING";
            BigDecimal provision = provisioning.latestFor(l.id()).stream().reduce((a, b) -> b)
                    .map(a -> a.getEcl()).orElse(BigDecimal.ZERO.setScale(2));
            var r = rows.get(c);
            r[0] = r[0].add(BigDecimal.ONE);
            r[1] = r[1].add(l.outstandingPrincipal().amount());
            r[2] = r[2].add(provision);
        }
        List<ReturnLine> out = new ArrayList<>();
        rows.forEach((k, v) -> out.add(new ReturnLine(k, v[0].intValue(), v[1], v[2])));
        return out;
    }

    public String regulatoryReturnCsv(CurrencyCode ccy) {
        var sb = new StringBuilder("institution,return,as_of,currency,classification,loans,outstanding_principal,provision\n");
        LocalDate asOf = businessDate.today();
        for (var r : regulatoryReturn(ccy)) {
            sb.append("INSUREHUB_MICROFINANCE,PORTFOLIO_CLASSIFICATION,").append(asOf).append(',').append(ccy).append(',')
                    .append(r.classification()).append(',').append(r.loans()).append(',').append(r.outstandingPrincipal().toPlainString())
                    .append(',').append(r.provision().toPlainString()).append('\n');
        }
        return sb.toString();
    }
}

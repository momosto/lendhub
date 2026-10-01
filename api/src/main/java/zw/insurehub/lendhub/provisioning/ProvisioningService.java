package zw.insurehub.lendhub.provisioning;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import zw.insurehub.lendhub.loans.LoanAccount;
import zw.insurehub.lendhub.loans.LoanServicing;
import zw.insurehub.lendhub.provisioning.domain.Ifrs9;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;
import zw.insurehub.lendhub.shared.security.CurrentUser;

/** Public API of the provisioning module: stages every disbursed loan and books the ECL movement (LH-70, LH-71). */
@Service
@Transactional
public class ProvisioningService {

    private record Staged(CurrencyCode currency, UUID loanId, String loanNumber, Ifrs9.Stage stage, int dpd,
            boolean restructured, BigDecimal ead, BigDecimal pd, BigDecimal ecl) {
    }

    private final ProvisionRunRepo runs;
    private final StageAssignmentRepo assignments;
    private final LoanServicing loans;
    private final EclParameters params;
    private final ApplicationEventPublisher events;

    ProvisioningService(ProvisionRunRepo runs, StageAssignmentRepo assignments, LoanServicing loans, EclParameters params,
            ApplicationEventPublisher events) {
        this.runs = runs;
        this.assignments = assignments;
        this.loans = loans;
        this.params = params;
        this.events = events;
    }

    public List<ProvisionRun> run(LocalDate asOf) {
        UUID group = UUID.randomUUID();
        Map<CurrencyCode, BigDecimal[]> totals = new EnumMap<>(CurrencyCode.class);
        Map<CurrencyCode, Integer> counts = new EnumMap<>(CurrencyCode.class);
        var staged = new java.util.ArrayList<Staged>();
        for (var loan : loans.snapshots()) {
            if (loan.status() == LoanAccount.Status.CLOSED) continue;
            boolean writtenOff = loan.status() == LoanAccount.Status.WRITTEN_OFF;
            var stage = Ifrs9.stage(loan.daysPastDue(), loan.restructured(), writtenOff);
            Money ead = writtenOff ? Money.zero(loan.currency()) : loan.outstandingPrincipal().plus(loan.interestReceivable());
            BigDecimal pd = params.pd(stage);
            Money ecl = Ifrs9.ecl(pd, params.lgd(), ead);
            staged.add(new Staged(loan.currency(), loan.id(), loan.loanNumber(), stage, loan.daysPastDue(), loan.restructured(),
                    ead.amount(), pd, ecl.amount()));
            var t = totals.computeIfAbsent(loan.currency(), c -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO });
            t[stage.ordinal()] = t[stage.ordinal()].add(ecl.amount());
            counts.merge(loan.currency(), 1, Integer::sum);
        }
        var result = new java.util.ArrayList<ProvisionRun>();
        for (CurrencyCode ccy : CurrencyCode.values()) {
            BigDecimal previous = runs.findFirstByCurrencyOrderByCreatedAtDesc(ccy).map(ProvisionRun::getTotalEcl).orElse(BigDecimal.ZERO);
            var t = totals.getOrDefault(ccy, new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO });
            if (counts.getOrDefault(ccy, 0) == 0 && previous.signum() == 0) continue;
            var run = runs.save(new ProvisionRun(group, asOf, ccy, counts.getOrDefault(ccy, 0), t[0], t[1], t[2], previous,
                    CurrentUser.get().username()));
            result.add(run);
            staged.stream().filter(st -> st.currency() == ccy).forEach(st -> assignments.save(new ProvisionRun.StageAssignment(
                    run.getId(), st.loanId(), st.loanNumber(), st.stage(), st.dpd(), st.restructured(), st.ead(), st.pd(),
                    params.lgd(), st.ecl())));
            events.publishEvent(new ProvisionCalculated(run.getId(), asOf, Money.of(run.getTotalEcl(), ccy),
                    Money.of(run.getMovement(), ccy)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ProvisionRun> runs() {
        return runs.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<ProvisionRun.StageAssignment> assignments(UUID runId) {
        return assignments.findByRunIdOrderByLoanNumber(runId);
    }

    @Transactional(readOnly = true)
    public List<ProvisionRun.StageAssignment> latestFor(UUID loanId) {
        return assignments.findByLoanId(loanId);
    }
}

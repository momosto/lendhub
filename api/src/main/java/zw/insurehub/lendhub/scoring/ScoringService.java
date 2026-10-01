package zw.insurehub.lendhub.scoring;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

/** Public API of the scoring module: bureau check + in-house history + scorecard. */
@Service
public class ScoringService {

    public record ScoringRequest(UUID borrowerId, String nationalId, boolean salaried, BigDecimal yearsInEmployment,
            boolean groupMember, BigDecimal affordabilityRatio, BigDecimal maxAffordabilityRatio) {
    }

    public record Scored(Scorecard.Result result, CreditBureau.BureauReport bureau) {
    }

    private final CreditBureau bureau;
    private final RepaymentHistoryProvider history;

    ScoringService(CreditBureau bureau, RepaymentHistoryProvider history) {
        this.bureau = bureau;
        this.history = history;
    }

    public Scored score(ScoringRequest r) {
        var report = bureau.report(r.nationalId());
        var result = Scorecard.score(new Scorecard.Input(r.salaried(), r.yearsInEmployment(), report,
                history.historyOf(r.borrowerId()), r.groupMember(), r.affordabilityRatio(), r.maxAffordabilityRatio()));
        return new Scored(result, report);
    }
}

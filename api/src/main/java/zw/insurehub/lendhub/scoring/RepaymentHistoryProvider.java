package zw.insurehub.lendhub.scoring;

import java.util.UUID;

/**
 * Port for in-house repayment history, implemented by the loans module. Declared here so that scoring
 * never depends on loans (the dependency points the other way, which keeps the module graph acyclic).
 */
public interface RepaymentHistoryProvider {

    record RepaymentHistory(int closedLoans, int openLoans, int worstDaysPastDue) {
        public static final RepaymentHistory NONE = new RepaymentHistory(0, 0, 0);
    }

    RepaymentHistory historyOf(UUID borrowerId);
}

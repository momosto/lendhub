package zw.insurehub.lendhub.repayments;

import java.time.LocalDate;
import java.util.UUID;

import zw.insurehub.lendhub.loans.LoanEvents.Breakdown;
import zw.insurehub.lendhub.shared.money.Money;

/** Events from the repayments module, consumed by the ledger. */
public final class RepaymentEvents {

    /**
     * Money received and split. {@code creditAdded} is the overpayment held as a credit balance;
     * {@code recovery} marks money received on a written-off loan.
     */
    public record RepaymentAllocated(UUID repaymentId, UUID loanId, String loanNumber, Money amount, Breakdown breakdown,
            Money creditAdded, boolean recovery, String channel, LocalDate valueDate) {
    }

    /** Compensating event for a reversed repayment; the ledger posts the mirror entry. */
    public record RepaymentReversed(UUID repaymentId, UUID loanId, String loanNumber, Money amount, Breakdown breakdown,
            Money creditAdded, boolean recovery, LocalDate valueDate) {
    }

    private RepaymentEvents() {
    }
}

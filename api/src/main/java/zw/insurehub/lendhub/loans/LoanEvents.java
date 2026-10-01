package zw.insurehub.lendhub.loans;

import java.time.LocalDate;
import java.util.UUID;

import zw.insurehub.lendhub.loans.domain.ArrearsBucket;
import zw.insurehub.lendhub.shared.money.Money;

/**
 * Domain events published by the loans module. Events carry the customer reference and loan number, never the
 * national ID or full KYC record (data minimisation). The phone number is included because the Notifications
 * service needs it to send the SMS/WhatsApp message.
 */
public final class LoanEvents {

    /** Split of an amount over instalment components, used by the ledger. */
    public record Breakdown(Money penalty, Money fee, Money creditLife, Money interest, Money principal) {
        public Money total() {
            return penalty.plus(fee).plus(creditLife).plus(interest).plus(principal);
        }
    }

    public record LoanDisbursed(UUID loanId, String loanNumber, String customerRef, String msisdn, String branch,
            String productCode, Money principal, Money establishmentFee, Money netDisbursed, LocalDate disbursedOn,
            LocalDate firstDueDate, Money firstInstalment, String creditLifePolicyNumber) {
    }

    public record InterestAccrued(UUID loanId, String loanNumber, Money amount, LocalDate date) {
    }

    public record PenaltyCharged(UUID loanId, String loanNumber, Money amount, LocalDate date) {
    }

    /** A credit balance (earlier overpayment) was applied to instalments that fell due. */
    public record CreditApplied(UUID loanId, String loanNumber, Breakdown breakdown, LocalDate date) {
    }

    public record ArrearsBucketChanged(UUID loanId, String loanNumber, String customerRef, String msisdn, int daysPastDue,
            ArrearsBucket fromBucket, ArrearsBucket toBucket, Money arrearsAmount, LocalDate date) {
    }

    public record InstalmentDue(UUID loanId, String loanNumber, String customerRef, String msisdn, LocalDate dueDate,
            Money amountDue, boolean overdue) {
    }

    public record LoanClosed(UUID loanId, String loanNumber, String customerRef, LocalDate closedOn) {
    }

    public record LoanWrittenOff(UUID loanId, String loanNumber, Money principal, Money interest, Money penalties,
            LocalDate date) {
    }

    public record LoanRestructured(UUID loanId, String loanNumber, int newInstalments, LocalDate date) {
    }

    private LoanEvents() {
    }
}

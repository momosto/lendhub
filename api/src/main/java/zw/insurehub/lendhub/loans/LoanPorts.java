package zw.insurehub.lendhub.loans;

import java.time.LocalDate;

import zw.insurehub.lendhub.shared.money.Money;

/** Outbound ports of the loans module; adapters live in the integration module (hexagonal style). */
public final class LoanPorts {

    /** InsureHub credit life cover (I5: POST /api/partners/credit-life/policies, idempotent on loan number). */
    public interface CreditLifeProvider {

        record CoverRequest(String loanNumber, String customerRef, String borrowerName, Money sumInsured, int termMonths,
                LocalDate startDate) {
        }

        String issuePolicy(CoverRequest request);
    }

    /** Disbursement to the borrower's EcoCash wallet (B2C, via the Payments hub; simulated in the demo). */
    public interface DisbursementGateway {

        String disburse(String loanNumber, String msisdn, Money amount);
    }

    private LoanPorts() {
    }
}

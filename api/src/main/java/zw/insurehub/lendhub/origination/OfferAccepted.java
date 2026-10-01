package zw.insurehub.lendhub.origination;

import java.math.BigDecimal;
import java.util.UUID;

import zw.insurehub.lendhub.products.schedule.Frequency;
import zw.insurehub.lendhub.shared.money.CurrencyCode;

/**
 * The borrower accepted an approved offer (the "LoanApproved" event in the architecture doc).
 * The loans module books the loan account and requests credit life cover.
 */
public record OfferAccepted(UUID applicationId, String applicationNumber, UUID borrowerId, String customerRef,
        String borrowerName, String msisdn, String branch, String productCode, CurrencyCode currency, BigDecimal amount,
        int instalments, Frequency frequency, String capturedBy, String approvedBy) {
}

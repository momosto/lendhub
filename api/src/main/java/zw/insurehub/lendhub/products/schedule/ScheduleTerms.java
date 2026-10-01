package zw.insurehub.lendhub.products.schedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

import zw.insurehub.lendhub.shared.money.Money;

/**
 * Inputs to the schedule engine.
 *
 * @param principal               amount borrowed
 * @param method                  flat or reducing balance
 * @param frequency               weekly or monthly
 * @param instalments             number of instalments (n)
 * @param monthlyInterestRate     nominal rate per month, e.g. 0.05 for 5%
 * @param establishmentFeeRate    fee as a fraction of principal, deducted at disbursement
 * @param creditLifeMonthlyRate   credit life premium per month as a fraction of principal
 * @param startDate               disbursement date; instalment k falls due k periods later
 */
public record ScheduleTerms(
        Money principal,
        RepaymentMethod method,
        Frequency frequency,
        int instalments,
        BigDecimal monthlyInterestRate,
        BigDecimal establishmentFeeRate,
        BigDecimal creditLifeMonthlyRate,
        LocalDate startDate) {

    public ScheduleTerms {
        Objects.requireNonNull(principal);
        Objects.requireNonNull(method);
        Objects.requireNonNull(frequency);
        Objects.requireNonNull(startDate);
        if (!principal.isPositive()) throw new IllegalArgumentException("Principal must be positive");
        if (instalments < 1) throw new IllegalArgumentException("At least one instalment is required");
        if (monthlyInterestRate == null || monthlyInterestRate.signum() < 0) throw new IllegalArgumentException("Rate must be >= 0");
        if (establishmentFeeRate == null) establishmentFeeRate = BigDecimal.ZERO;
        if (creditLifeMonthlyRate == null) creditLifeMonthlyRate = BigDecimal.ZERO;
    }
}

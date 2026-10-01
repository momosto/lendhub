package zw.insurehub.lendhub.loans.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import zw.insurehub.lendhub.shared.money.Money;

/** Daily accrual, penalties and the in duplum cap (LH-50, LH-51). Pure functions. */
public final class InterestRules {

    private static final MathContext MC = MathContext.DECIMAL128;

    /**
     * Interest to accrue on {@code day} for an instalment whose period runs (periodStart, dueDate].
     * Each day accrues interestDue / days (HALF_EVEN); on the due date the remainder is accrued, so the
     * accruals for a period always equal the schedule's interest exactly.
     */
    public static Money dailyAccrual(Money interestDue, Money accruedSoFar, LocalDate periodStart, LocalDate dueDate, LocalDate day) {
        Money remaining = interestDue.minus(accruedSoFar).max(Money.zero(interestDue.currency()));
        if (!day.isAfter(periodStart) || !remaining.isPositive()) return Money.zero(interestDue.currency());
        if (!day.isBefore(dueDate)) return remaining;
        long days = Math.max(1, ChronoUnit.DAYS.between(periodStart, dueDate));
        Money daily = Money.rounded(interestDue.amount().divide(BigDecimal.valueOf(days), MC), interestDue.currency(),
                RoundingMode.HALF_EVEN);
        return daily.min(remaining);
    }

    /** One day's penalty on the overdue amount: overdue × monthly penalty rate / 30 (HALF_EVEN). */
    public static Money dailyPenalty(Money overdue, BigDecimal penaltyMonthlyRate) {
        if (!overdue.isPositive() || penaltyMonthlyRate == null || penaltyMonthlyRate.signum() <= 0) {
            return Money.zero(overdue.currency());
        }
        return Money.rounded(overdue.amount().multiply(penaltyMonthlyRate, MC).divide(BigDecimal.valueOf(30), MC),
                overdue.currency(), RoundingMode.HALF_EVEN);
    }

    /**
     * In duplum: arrear interest plus penalties may not exceed the outstanding principal. Returns how much of
     * {@code proposed} may still be charged. Golden case: principal 200, arrears charges 195, penalty 10 → 5.
     */
    public static Money inDuplumAllowance(Money proposed, Money arrearChargesSoFar, Money outstandingPrincipal) {
        Money headroom = outstandingPrincipal.minus(arrearChargesSoFar).max(Money.zero(proposed.currency()));
        return proposed.min(headroom);
    }

    private InterestRules() {
    }
}

package zw.insurehub.lendhub.products.schedule;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;

public enum Frequency {
    WEEKLY(52),
    MONTHLY(12);

    private final int periodsPerYear;

    Frequency(int periodsPerYear) {
        this.periodsPerYear = periodsPerYear;
    }

    public int periodsPerYear() {
        return periodsPerYear;
    }

    /** Converts a monthly rate to this frequency's periodic rate: weekly = monthly × 12 / 52. */
    public BigDecimal periodicRate(BigDecimal monthlyRate) {
        return this == MONTHLY ? monthlyRate
                : monthlyRate.multiply(BigDecimal.valueOf(12), MathContext.DECIMAL128)
                        .divide(BigDecimal.valueOf(52), MathContext.DECIMAL128);
    }

    /** Unadjusted due date of instalment {@code k} (1-based) for a loan that starts on {@code start}. */
    public LocalDate dueDate(LocalDate start, int k) {
        return this == MONTHLY ? start.plusMonths(k) : start.plusWeeks(k);
    }

    /** Term in months, used to check product limits: weekly instalments × 12 / 52. */
    public BigDecimal termInMonths(int instalments) {
        return this == MONTHLY ? BigDecimal.valueOf(instalments)
                : BigDecimal.valueOf(instalments * 12L).divide(BigDecimal.valueOf(52), 2, java.math.RoundingMode.HALF_UP);
    }
}

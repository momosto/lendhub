package zw.insurehub.lendhub.shared.money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount in a currency, always stored at scale 2 (ADR-0002).
 * Intermediate maths uses {@link #CALC}; rounding happens only when a value becomes a Money.
 */
public record Money(BigDecimal amount, CurrencyCode currency) implements Comparable<Money> {

    public static final MathContext CALC = MathContext.DECIMAL128;
    public static final int SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.scale() != SCALE) {
            amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
        }
    }

    public static Money of(String amount, CurrencyCode currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money of(BigDecimal amount, CurrencyCode currency) {
        return new Money(amount, currency);
    }

    /** Rounds an unrounded intermediate value with the given mode (HALF_UP for instalments, HALF_EVEN for accruals). */
    public static Money rounded(BigDecimal raw, CurrencyCode currency, RoundingMode mode) {
        return new Money(raw.setScale(SCALE, mode), currency);
    }

    public static Money zero(CurrencyCode currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money times(BigDecimal factor, RoundingMode mode) {
        return rounded(amount.multiply(factor, CALC), currency, mode);
    }

    public Money min(Money other) {
        requireSameCurrency(other);
        return compareTo(other) <= 0 ? this : other;
    }

    public Money max(Money other) {
        requireSameCurrency(other);
        return compareTo(other) >= 0 ? this : other;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        if (other.currency != currency) {
            throw new IllegalArgumentException("Cannot mix " + currency + " and " + other.currency);
        }
    }

    @Override
    public String toString() {
        return currency + " " + amount.toPlainString();
    }
}

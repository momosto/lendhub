package zw.insurehub.lendhub.shared.fx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.springframework.boot.context.properties.ConfigurationProperties;

import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;

/**
 * Exchange rate used only to compare ZWG amounts against USD approval limits.
 * The rate is configuration with its source and date (never hard-coded in logic), per the group standard.
 */
@ConfigurationProperties("lendhub.fx")
public record FxRates(BigDecimal zwgPerUsd, String source, LocalDate rateDate) {

    public FxRates {
        if (zwgPerUsd == null) zwgPerUsd = new BigDecimal("26.80");
        if (source == null) source = "illustrative demo rate";
        if (rateDate == null) rateDate = LocalDate.of(2026, 10, 1);
    }

    public BigDecimal toUsd(Money money) {
        return money.currency() == CurrencyCode.USD
                ? money.amount()
                : money.amount().divide(zwgPerUsd, 2, RoundingMode.HALF_UP);
    }
}

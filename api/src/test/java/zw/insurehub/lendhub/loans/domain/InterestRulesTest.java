package zw.insurehub.lendhub.loans.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Scale;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;

class InterestRulesTest {

    private static Money usd(String v) {
        return Money.of(v, CurrencyCode.USD);
    }

    @Test
    void in_duplum_caps_arrear_charges_at_the_outstanding_principal() {
        // golden case: principal 200, arrear interest 195, daily penalty 10 → only 5 accrues
        assertThat(InterestRules.inDuplumAllowance(usd("10"), usd("195"), usd("200")).amount()).isEqualByComparingTo("5.00");
        // once the cap is reached nothing more accrues
        assertThat(InterestRules.inDuplumAllowance(usd("10"), usd("200"), usd("200")).isZero()).isTrue();
        assertThat(InterestRules.inDuplumAllowance(usd("10"), usd("250"), usd("200")).isZero()).isTrue();
    }

    @Test
    void daily_penalty_is_overdue_times_monthly_rate_over_30() {
        // 600 × 5% / 30 = 1.00
        assertThat(InterestRules.dailyPenalty(usd("600"), new BigDecimal("0.05")).amount()).isEqualByComparingTo("1.00");
        assertThat(InterestRules.dailyPenalty(usd("0"), new BigDecimal("0.05")).isZero()).isTrue();
    }

    @Test
    void accrual_on_the_due_date_takes_the_remainder() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate due = LocalDate.of(2026, 10, 31);
        assertThat(InterestRules.dailyAccrual(usd("50"), usd("0"), start, due, start.plusDays(1)).amount()).isEqualByComparingTo("1.67");
        assertThat(InterestRules.dailyAccrual(usd("50"), usd("48.33"), start, due, due).amount()).isEqualByComparingTo("1.67");
        assertThat(InterestRules.dailyAccrual(usd("50"), usd("50"), start, due, due).isZero()).isTrue();
        assertThat(InterestRules.dailyAccrual(usd("50"), usd("0"), start, due, start).isZero()).isTrue();
    }

    @Property
    void accruals_over_a_period_equal_the_scheduled_interest(
            @ForAll @BigRange(min = "0.00", max = "5000.00") @Scale(2) BigDecimal interest,
            @ForAll @IntRange(min = 1, max = 92) int days) {
        Money due = Money.of(interest, CurrencyCode.USD);
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate dueDate = start.plusDays(days);
        Money accrued = Money.zero(CurrencyCode.USD);
        for (LocalDate d = start.plusDays(1); !d.isAfter(dueDate); d = d.plusDays(1)) {
            Money a = InterestRules.dailyAccrual(due, accrued, start, dueDate, d);
            assertThat(a.isNegative()).isFalse();
            accrued = accrued.plus(a);
        }
        assertThat(accrued).isEqualTo(due);
    }
}

package zw.insurehub.lendhub.products.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;

/**
 * "Money maths is proven, not sampled": for any principal, rate, term, method and frequency the schedule balances
 * to the cent. CI runs these with -PjqwikTries=10000.
 */
class ScheduleProperties {

    private final ScheduleCalculator calc = new ScheduleCalculator(BusinessDays.WEEKENDS_ONLY);

    @Provide
    Arbitrary<BigDecimal> principals() {
        return Arbitraries.bigDecimals().between(new BigDecimal("1.00"), new BigDecimal("50000.00")).ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> rates() {
        return Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("0.30")).ofScale(6);
    }

    private Schedule schedule(BigDecimal p, BigDecimal rate, int n, RepaymentMethod m, Frequency f, BigDecimal fee, BigDecimal cl) {
        return calc.calculate(new ScheduleTerms(Money.of(p, CurrencyCode.USD), m, f, n, rate, fee, cl, LocalDate.of(2026, 10, 1)));
    }

    @Property
    void principal_parts_sum_exactly_to_the_principal(@ForAll("principals") BigDecimal p, @ForAll("rates") BigDecimal rate,
            @ForAll @IntRange(min = 1, max = 60) int n, @ForAll RepaymentMethod m, @ForAll Frequency f) {
        var s = schedule(p, rate, n, m, f, BigDecimal.ZERO, BigDecimal.ZERO);
        var sum = s.instalments().stream().map(Schedule.Line::principal).reduce(Money.zero(CurrencyCode.USD), Money::plus);
        assertThat(sum.amount()).isEqualByComparingTo(p);
        assertThat(s.instalments().getLast().closingBalance().isZero()).isTrue();
    }

    @Property
    void no_component_is_ever_negative(@ForAll("principals") BigDecimal p, @ForAll("rates") BigDecimal rate,
            @ForAll @IntRange(min = 1, max = 60) int n, @ForAll RepaymentMethod m, @ForAll Frequency f) {
        var s = schedule(p, rate, n, m, f, new BigDecimal("0.03"), new BigDecimal("0.005"));
        for (var line : s.instalments()) {
            assertThat(line.principal().isNegative()).isFalse();
            assertThat(line.interest().isNegative()).isFalse();
            assertThat(line.closingBalance().isNegative()).isFalse();
        }
    }

    @Property
    void totals_equal_the_sum_of_the_lines(@ForAll("principals") BigDecimal p, @ForAll("rates") BigDecimal rate,
            @ForAll @IntRange(min = 1, max = 60) int n, @ForAll RepaymentMethod m, @ForAll Frequency f) {
        var s = schedule(p, rate, n, m, f, new BigDecimal("0.03"), new BigDecimal("0.005"));
        var total = s.instalments().stream().map(Schedule.Line::total).reduce(Money.zero(CurrencyCode.USD), Money::plus);
        assertThat(total).isEqualTo(s.totalRepayable());
        assertThat(s.principal().plus(s.totalInterest()).plus(s.totalCreditLife())).isEqualTo(s.totalRepayable());
        assertThat(s.netDisbursed().plus(s.establishmentFee())).isEqualTo(s.principal());
    }

    @Property
    void flat_interest_is_exactly_principal_times_rate_times_term(@ForAll("principals") BigDecimal p,
            @ForAll("rates") BigDecimal rate, @ForAll @IntRange(min = 1, max = 36) int n) {
        var s = schedule(p, rate, n, RepaymentMethod.FLAT, Frequency.MONTHLY, BigDecimal.ZERO, BigDecimal.ZERO);
        var expected = Money.of(p, CurrencyCode.USD).times(rate.multiply(BigDecimal.valueOf(n)), java.math.RoundingMode.HALF_UP);
        assertThat(s.totalInterest()).isEqualTo(expected);
    }

    @Property
    void due_dates_strictly_increase_and_fall_on_weekdays(@ForAll @IntRange(min = 1, max = 60) int n, @ForAll Frequency f) {
        var s = schedule(new BigDecimal("1000"), new BigDecimal("0.05"), n, RepaymentMethod.REDUCING_BALANCE, f,
                BigDecimal.ZERO, BigDecimal.ZERO);
        LocalDate previous = LocalDate.of(2026, 10, 1);
        for (var line : s.instalments()) {
            assertThat(line.dueDate()).isAfter(previous);
            assertThat(line.dueDate().getDayOfWeek().getValue()).isLessThanOrEqualTo(5);
            previous = line.dueDate();
        }
    }
}

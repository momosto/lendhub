package zw.insurehub.lendhub.products.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;

/** Golden cases from docs/05-test-strategy.md, hand-calculated. */
class ScheduleCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private final ScheduleCalculator calc = new ScheduleCalculator(BusinessDays.WEEKENDS_ONLY);

    private static ScheduleTerms terms(RepaymentMethod method, Frequency f, int n, String rate) {
        return new ScheduleTerms(Money.of("1000", CurrencyCode.USD), method, f, n, new BigDecimal(rate),
                BigDecimal.ZERO, BigDecimal.ZERO, START);
    }

    @Test
    void flat_1000_at_5pc_for_6_months_is_216_67_times_5_and_216_65() {
        var s = calc.calculate(terms(RepaymentMethod.FLAT, Frequency.MONTHLY, 6, "0.05"));

        assertThat(s.totalInterest().amount()).isEqualByComparingTo("300.00");
        assertThat(s.instalments()).hasSize(6);
        for (int k = 0; k < 5; k++) {
            assertThat(s.instalments().get(k).total().amount()).isEqualByComparingTo("216.67");
            assertThat(s.instalments().get(k).interest().amount()).isEqualByComparingTo("50.00");
        }
        assertThat(s.instalments().get(5).total().amount()).isEqualByComparingTo("216.65");
        assertThat(s.instalments().get(5).closingBalance().isZero()).isTrue();
        assertThat(s.totalRepayable().amount()).isEqualByComparingTo("1300.00");
    }

    @Test
    void reducing_1000_at_5pc_for_6_months_has_annuity_197_02_and_first_interest_50() {
        var s = calc.calculate(terms(RepaymentMethod.REDUCING_BALANCE, Frequency.MONTHLY, 6, "0.05"));

        assertThat(s.first().total().amount()).isEqualByComparingTo("197.02");
        assertThat(s.first().interest().amount()).isEqualByComparingTo("50.00");
        assertThat(s.first().principal().amount()).isEqualByComparingTo("147.02");
        var principal = s.instalments().stream().map(Schedule.Line::principal).reduce(Money.zero(CurrencyCode.USD), Money::plus);
        assertThat(principal.amount()).isEqualByComparingTo("1000.00");
        assertThat(s.instalments().getLast().closingBalance().isZero()).isTrue();
    }

    @Test
    void weekly_rate_is_monthly_times_12_over_52() {
        assertThat(Frequency.WEEKLY.periodicRate(new BigDecimal("0.052")).doubleValue()).isCloseTo(0.012, org.assertj.core.data.Offset.offset(1e-12));
        var s = calc.calculate(terms(RepaymentMethod.REDUCING_BALANCE, Frequency.WEEKLY, 12, "0.08"));
        assertThat(s.first().dueDate()).isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test
    void due_dates_on_weekends_and_holidays_move_to_the_next_business_day() {
        // 2026-10-01 + 2 months = 2026-12-01 (Tue); + 3 months... use a start that lands on a Saturday
        LocalDate start = LocalDate.of(2026, 8, 22); // + 4 months = 2026-12-22 (Unity Day, Tuesday)
        BusinessDays withHoliday = date -> {
            Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 12, 22), LocalDate.of(2026, 12, 25));
            LocalDate d = date;
            while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY || holidays.contains(d)) {
                d = d.plusDays(1);
            }
            return d;
        };
        var s = new ScheduleCalculator(withHoliday).calculate(new ScheduleTerms(Money.of("500", CurrencyCode.USD),
                RepaymentMethod.FLAT, Frequency.MONTHLY, 4, new BigDecimal("0.04"), BigDecimal.ZERO, BigDecimal.ZERO, start));
        assertThat(s.instalments().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 9, 22));   // Tuesday, business day
        assertThat(s.instalments().get(3).dueDate()).isEqualTo(LocalDate.of(2026, 12, 23));  // Unity Day → next day
    }

    @Test
    void fees_and_credit_life_are_disclosed_and_raise_the_effective_rate() {
        var plain = calc.calculate(terms(RepaymentMethod.REDUCING_BALANCE, Frequency.MONTHLY, 6, "0.05"));
        var loaded = calc.calculate(new ScheduleTerms(Money.of("1000", CurrencyCode.USD), RepaymentMethod.REDUCING_BALANCE,
                Frequency.MONTHLY, 6, new BigDecimal("0.05"), new BigDecimal("0.03"), new BigDecimal("0.005"), START));

        assertThat(loaded.establishmentFee().amount()).isEqualByComparingTo("30.00");
        assertThat(loaded.netDisbursed().amount()).isEqualByComparingTo("970.00");
        assertThat(loaded.totalCreditLife().amount()).isEqualByComparingTo("30.00");      // 5.00 × 6
        assertThat(loaded.first().total().amount()).isEqualByComparingTo("202.02");       // 197.02 + 5.00
        // 5%/month compounds to (1.05^12 − 1) = 79.59% a year; fees and credit life push it higher
        assertThat(plain.effectiveAnnualRatePercent()).isEqualByComparingTo("79.59");
        assertThat(loaded.effectiveAnnualRatePercent()).isGreaterThan(plain.effectiveAnnualRatePercent());
    }

    @Test
    void zero_rate_loan_repays_principal_only() {
        var s = calc.calculate(terms(RepaymentMethod.REDUCING_BALANCE, Frequency.MONTHLY, 3, "0"));
        assertThat(s.totalInterest().isZero()).isTrue();
        assertThat(s.instalments().get(0).total().amount()).isEqualByComparingTo("333.33");
        assertThat(s.instalments().get(2).total().amount()).isEqualByComparingTo("333.34");
    }

    @Test
    void rejects_invalid_terms() {
        assertThatThrownBy(() -> terms(RepaymentMethod.FLAT, Frequency.MONTHLY, 0, "0.05")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> terms(RepaymentMethod.FLAT, Frequency.MONTHLY, 6, "-0.01")).isInstanceOf(IllegalArgumentException.class);
    }
}

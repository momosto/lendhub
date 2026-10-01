package zw.insurehub.lendhub.loans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import zw.insurehub.lendhub.loans.domain.ArrearsBucket;
import zw.insurehub.lendhub.loans.domain.Component;
import zw.insurehub.lendhub.products.schedule.BusinessDays;
import zw.insurehub.lendhub.products.schedule.Frequency;
import zw.insurehub.lendhub.products.schedule.RepaymentMethod;
import zw.insurehub.lendhub.products.schedule.Schedule;
import zw.insurehub.lendhub.products.schedule.ScheduleCalculator;
import zw.insurehub.lendhub.products.schedule.ScheduleTerms;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;
import zw.insurehub.lendhub.shared.web.DomainException;

/** State machine guards and servicing behaviour of the loan aggregate, without Spring. */
class LoanAccountTest {

    private static final LocalDate D0 = LocalDate.of(2026, 10, 1); // Thursday
    private LoanAccount loan;
    private Schedule schedule;

    private static Money usd(String v) {
        return Money.of(v, CurrencyCode.USD);
    }

    @BeforeEach
    void setUp() {
        loan = LoanAccount.book("LN-2610-000001", UUID.randomUUID(), "APP-001001", UUID.randomUUID(), "CUS-001001",
                "Chipo Moyo", "263771234567", "MBARE", "TRADER", usd("1000"), RepaymentMethod.REDUCING_BALANCE,
                Frequency.MONTHLY, 6, new BigDecimal("0.05"), 3, true, new BigDecimal("0.01"), "officer", "manager");
        schedule = new ScheduleCalculator(BusinessDays.WEEKENDS_ONLY).calculate(new ScheduleTerms(usd("1000"),
                RepaymentMethod.REDUCING_BALANCE, Frequency.MONTHLY, 6, new BigDecimal("0.05"), new BigDecimal("0.03"),
                BigDecimal.ZERO, D0));
    }

    private void activate() {
        loan.covered("CL-1");
        loan.disburse(schedule, D0, "finance", "B2C-1");
    }

    @Test
    void cannot_disburse_without_credit_life_cover() {
        assertThatThrownBy(() -> loan.disburse(schedule, D0, "finance", "B2C-1"))
                .isInstanceOf(DomainException.class).hasMessageContaining("expected COVERED");
    }

    @Test
    void capturer_and_approver_cannot_release_the_disbursement() {
        loan.covered("CL-1");
        assertThatThrownBy(() -> loan.disburse(schedule, D0, "officer", "x")).hasMessageContaining("someone other");
        assertThatThrownBy(() -> loan.disburse(schedule, D0, "MANAGER", "x")).hasMessageContaining("someone other");
    }

    @Test
    void disbursement_fixes_the_schedule_and_activates_the_loan() {
        activate();
        assertThat(loan.getStatus()).isEqualTo(LoanAccount.Status.ACTIVE);
        assertThat(loan.getInstalments()).hasSize(6);
        assertThat(loan.getNetDisbursed()).isEqualByComparingTo("970.00");
        assertThat(loan.outstandingPrincipal()).isEqualTo(usd("1000"));
        assertThat(loan.getMaturityDate()).isEqualTo(schedule.instalments().getLast().dueDate());
    }

    @Test
    void early_payment_is_held_as_credit_and_applied_when_the_instalment_falls_due() {
        activate();
        var r = loan.receive(usd("100"), D0.plusDays(5));
        assertThat(r.lines()).isEmpty();
        assertThat(loan.getCreditBalance()).isEqualTo(usd("100"));

        LocalDate due1 = loan.getInstalments().getFirst().getDueDate();
        var applied = loan.applyCredit(due1).orElseThrow();
        assertThat(applied.remainder().isZero()).isTrue();
        assertThat(loan.getCreditBalance().isZero()).isTrue();
        // 100 pays interest 50.00 first, then 50.00 of the 147.02 principal
        assertThat(loan.getInstalments().getFirst().outstanding(Component.INTEREST).isZero()).isTrue();
        assertThat(loan.getInstalments().getFirst().outstanding(Component.PRINCIPAL).amount()).isEqualByComparingTo("97.02");
    }

    @Test
    void missed_instalment_ages_into_arrears_and_back_when_paid() {
        activate();
        LocalDate due1 = loan.getInstalments().getFirst().getDueDate();
        assertThat(loan.age(due1)).isEmpty();
        assertThat(loan.age(due1.plusDays(31))).contains(ArrearsBucket.CURRENT);
        assertThat(loan.getDaysPastDue()).isEqualTo(31);
        assertThat(loan.getArrearsBucket()).isEqualTo(ArrearsBucket.DPD_31_60);
        assertThat(loan.getStatus()).isEqualTo(LoanAccount.Status.IN_ARREARS);

        loan.receive(loan.arrearsAmount(due1.plusDays(31)), due1.plusDays(31));
        loan.age(due1.plusDays(31));
        assertThat(loan.getStatus()).isEqualTo(LoanAccount.Status.ACTIVE);
        assertThat(loan.getWorstDaysPastDue()).isEqualTo(31);
    }

    @Test
    void accruals_for_a_period_equal_the_scheduled_interest() {
        activate();
        var first = loan.getInstalments().getFirst();
        Money total = usd("0");
        for (LocalDate d = D0.plusDays(1); !d.isAfter(first.getDueDate()); d = d.plusDays(1)) {
            total = total.plus(loan.accrue(d));
        }
        assertThat(total).isEqualTo(first.due(Component.INTEREST));
        assertThat(loan.interestReceivable()).isEqualTo(usd("50.00"));
    }

    @Test
    void penalties_stop_at_the_in_duplum_cap() {
        activate();
        LocalDate day = loan.getInstalments().getLast().getDueDate().plusDays(400);
        Money charged = usd("0");
        for (int i = 0; i < 1000; i++) charged = charged.plus(loan.chargePenalty(day.plusDays(i)));
        // arrear interest + penalties never exceed the outstanding principal (1,000)
        assertThat(loan.arrearCharges(day.plusDays(1000)).compareTo(loan.outstandingPrincipal())).isLessThanOrEqualTo(0);
        assertThat(loan.chargePenalty(day.plusDays(1001)).isZero()).isTrue();
    }

    @Test
    void early_settlement_waives_future_interest_and_closes_the_loan() {
        activate();
        LocalDate day = D0.plusDays(15);
        for (LocalDate d = D0.plusDays(1); !d.isAfter(day); d = d.plusDays(1)) loan.accrue(d);
        var quote = loan.settlementQuote(day);
        // principal 1,000 + ~15 days of interest (24–26) + 1% settlement fee (10)
        assertThat(quote.outstandingPrincipal()).isEqualTo(usd("1000"));
        assertThat(quote.settlementFee()).isEqualTo(usd("10.00"));
        assertThat(quote.accruedInterest().amount()).isBetween(new BigDecimal("23"), new BigDecimal("27"));

        loan.settle(quote.total(), day);
        assertThat(loan.getStatus()).isEqualTo(LoanAccount.Status.CLOSED);
        assertThat(loan.outstandingPrincipal().isZero()).isTrue();
        assertThatThrownBy(() -> loan.receive(usd("1"), day)).hasMessageContaining("CLOSED");
    }

    @Test
    void reversal_reopens_a_closed_loan() {
        activate();
        LocalDate day = D0.plusDays(2);
        var quote = loan.settlementQuote(day);
        var r = loan.settle(quote.total(), day);
        assertThat(loan.getStatus()).isEqualTo(LoanAccount.Status.CLOSED);
        loan.reverse(r.lines(), r.remainder(), day);
        assertThat(loan.getStatus()).isEqualTo(LoanAccount.Status.ACTIVE);
        assertThat(loan.outstandingPrincipal()).isEqualTo(usd("1000"));
    }

    @Test
    void restructure_reschedules_outstanding_principal_and_carries_arrears() {
        activate();
        LocalDate day = loan.getInstalments().getFirst().getDueDate().plusDays(40);
        for (LocalDate d = D0.plusDays(1); !d.isAfter(day); d = d.plusDays(1)) {
            loan.accrue(d);
            loan.chargePenalty(d);
        }
        loan.age(day);
        Money principalBefore = loan.outstandingPrincipal();
        var newSchedule = new ScheduleCalculator(BusinessDays.WEEKENDS_ONLY).calculate(new ScheduleTerms(principalBefore,
                RepaymentMethod.REDUCING_BALANCE, Frequency.MONTHLY, 10, new BigDecimal("0.05"), BigDecimal.ZERO, BigDecimal.ZERO, day));
        loan.restructure(newSchedule, day);

        assertThat(loan.getStatus()).isEqualTo(LoanAccount.Status.RESTRUCTURED);
        assertThat(loan.isRestructured()).isTrue();
        assertThat(loan.outstandingPrincipal()).isEqualTo(principalBefore);
        assertThat(loan.getDaysPastDue()).isZero();
        assertThat(loan.getInstalments().stream().filter(i -> i.getState() == Instalment.State.CANCELLED)).hasSize(6);
    }

    @Test
    void write_off_returns_the_amounts_to_derecognise() {
        activate();
        LocalDate day = loan.getInstalments().getFirst().getDueDate();
        for (LocalDate d = D0.plusDays(1); !d.isAfter(day); d = d.plusDays(1)) loan.accrue(d);
        var amounts = loan.writeOff(day.plusDays(100));
        assertThat(amounts.principal()).isEqualTo(usd("1000"));
        assertThat(amounts.interest()).isEqualTo(usd("50.00"));
        assertThat(loan.getStatus()).isEqualTo(LoanAccount.Status.WRITTEN_OFF);
    }

    @Test
    void currency_mismatch_is_rejected() {
        activate();
        assertThatThrownBy(() -> loan.receive(Money.of("10", CurrencyCode.ZWG), D0)).hasMessageContaining("ZWG");
    }
}

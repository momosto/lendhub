package zw.insurehub.lendhub.loans.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;

class AllocationWaterfallTest {

    private static Money usd(String v) {
        return Money.of(v, CurrencyCode.USD);
    }

    @Test
    void pays_penalty_fee_credit_life_interest_then_principal() {
        // golden case: 100 against penalty 5, fee 10, CL 2, interest 30, principal 150 → 5 / 10 / 2 / 30 / 53
        var due = AllocationWaterfall.due(1, usd("5"), usd("10"), usd("2"), usd("30"), usd("150"));
        var r = AllocationWaterfall.allocate(usd("100"), List.of(due), Component.DEFAULT_ORDER);

        assertThat(r.lines()).extracting(AllocationWaterfall.Line::component).containsExactly(
                Component.PENALTY, Component.FEE, Component.CREDIT_LIFE, Component.INTEREST, Component.PRINCIPAL);
        assertThat(r.lines()).extracting(l -> l.amount().amount().toPlainString())
                .containsExactly("5.00", "10.00", "2.00", "30.00", "53.00");
        assertThat(r.remainder().isZero()).isTrue();
    }

    @Test
    void oldest_instalment_is_cleared_first_and_the_rest_is_a_remainder() {
        var first = AllocationWaterfall.due(1, usd("0"), usd("0"), usd("1"), usd("10"), usd("40"));
        var second = AllocationWaterfall.due(2, usd("0"), usd("0"), usd("1"), usd("9"), usd("41"));
        var r = AllocationWaterfall.allocate(usd("120"), List.of(first, second), Component.DEFAULT_ORDER);

        assertThat(r.total(Component.PRINCIPAL).amount()).isEqualByComparingTo("81.00");
        assertThat(r.lines().stream().filter(l -> l.seq() == 1).map(AllocationWaterfall.Line::amount)
                .reduce(usd("0"), Money::plus).amount()).isEqualByComparingTo("51.00");
        assertThat(r.remainder().amount()).isEqualByComparingTo("18.00");
    }

    @Test
    void custom_order_puts_principal_first() {
        var due = AllocationWaterfall.due(1, usd("5"), usd("0"), usd("0"), usd("30"), usd("50"));
        var r = AllocationWaterfall.allocate(usd("60"), List.of(due),
                List.of(Component.PRINCIPAL, Component.INTEREST, Component.PENALTY, Component.FEE, Component.CREDIT_LIFE));
        assertThat(r.total(Component.PRINCIPAL).amount()).isEqualByComparingTo("50.00");
        assertThat(r.total(Component.INTEREST).amount()).isEqualByComparingTo("10.00");
        assertThat(r.total(Component.PENALTY).isZero()).isTrue();
    }

    @Test
    void zero_payment_allocates_nothing() {
        var due = AllocationWaterfall.due(1, usd("5"), usd("10"), usd("2"), usd("30"), usd("150"));
        var r = AllocationWaterfall.allocate(usd("0"), List.of(due), Component.DEFAULT_ORDER);
        assertThat(r.lines()).isEmpty();
    }
}

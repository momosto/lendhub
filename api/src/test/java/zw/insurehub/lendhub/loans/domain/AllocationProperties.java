package zw.insurehub.lendhub.loans.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;

class AllocationProperties {

    private static Arbitrary<Money> amounts(String max) {
        return Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal(max)).ofScale(2)
                .map(v -> Money.of(v, CurrencyCode.USD));
    }

    @Provide
    Arbitrary<List<AllocationWaterfall.Due>> dues() {
        Arbitrary<AllocationWaterfall.Due> one = Combinators.combine(amounts("20"), amounts("15"), amounts("5"), amounts("200"), amounts("1000"))
                .as((p, f, c, i, pr) -> AllocationWaterfall.due(0, p, f, c, i, pr));
        return one.list().ofMinSize(0).ofMaxSize(12).map(list -> {
            List<AllocationWaterfall.Due> numbered = new ArrayList<>();
            for (int k = 0; k < list.size(); k++) numbered.add(new AllocationWaterfall.Due(k + 1, list.get(k).outstanding()));
            return numbered;
        });
    }

    @Provide
    Arbitrary<Money> payments() {
        return amounts("5000");
    }

    @Property
    void allocations_plus_remainder_equal_the_payment(@ForAll("payments") Money pay, @ForAll("dues") List<AllocationWaterfall.Due> dues) {
        var r = AllocationWaterfall.allocate(pay, dues, Component.DEFAULT_ORDER);
        var allocated = r.lines().stream().map(AllocationWaterfall.Line::amount).reduce(Money.zero(CurrencyCode.USD), Money::plus);
        assertThat(allocated.plus(r.remainder())).isEqualTo(pay);
        assertThat(r.remainder().isNegative()).isFalse();
    }

    @Property
    void never_allocates_more_than_is_owed(@ForAll("payments") Money pay, @ForAll("dues") List<AllocationWaterfall.Due> dues) {
        var r = AllocationWaterfall.allocate(pay, dues, Component.DEFAULT_ORDER);
        for (var due : dues) {
            for (Component c : Component.values()) {
                var paid = r.lines().stream().filter(l -> l.seq() == due.seq() && l.component() == c)
                        .map(AllocationWaterfall.Line::amount).reduce(Money.zero(CurrencyCode.USD), Money::plus);
                assertThat(paid.compareTo(due.get(c))).isLessThanOrEqualTo(0);
            }
        }
    }

    @Property
    void a_remainder_only_exists_when_everything_is_paid(@ForAll("payments") Money pay, @ForAll("dues") List<AllocationWaterfall.Due> dues) {
        var r = AllocationWaterfall.allocate(pay, dues, Component.DEFAULT_ORDER);
        if (r.remainder().isPositive()) {
            var owed = dues.stream().flatMap(d -> d.outstanding().values().stream()).reduce(Money.zero(CurrencyCode.USD), Money::plus);
            var allocated = r.lines().stream().map(AllocationWaterfall.Line::amount).reduce(Money.zero(CurrencyCode.USD), Money::plus);
            assertThat(allocated).isEqualTo(owed);
        }
    }
}

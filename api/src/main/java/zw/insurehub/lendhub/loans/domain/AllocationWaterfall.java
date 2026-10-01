package zw.insurehub.lendhub.loans.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import zw.insurehub.lendhub.shared.money.Money;

/**
 * Splits a payment over outstanding instalments, oldest first, and within each instalment in the configured
 * component order (default: penalties → fees → credit life → interest → principal). Pure function (LH-42).
 * <p>
 * Golden case: 100 against penalty 5, fee 10, credit life 2, interest 30, principal 150 → 5 / 10 / 2 / 30 / 53.
 */
public final class AllocationWaterfall {

    /** What is still owed on one instalment. */
    public record Due(int seq, Map<Component, Money> outstanding) {
        public Money get(Component c) {
            return outstanding.get(c);
        }
    }

    public record Line(int seq, Component component, Money amount) {
    }

    public record Result(List<Line> lines, Money remainder) {
        public Money total(Component c) {
            return lines.stream().filter(l -> l.component() == c).map(Line::amount)
                    .reduce(Money.zero(remainder.currency()), Money::plus);
        }
    }

    public static Result allocate(Money amount, List<Due> oldestFirst, List<Component> order) {
        if (amount.isNegative()) throw new IllegalArgumentException("Payment cannot be negative");
        List<Line> lines = new ArrayList<>();
        Money left = amount;
        for (Due due : oldestFirst) {
            for (Component c : order) {
                if (!left.isPositive()) break;
                Money owed = due.get(c);
                if (owed == null || !owed.isPositive()) continue;
                Money take = left.min(owed);
                lines.add(new Line(due.seq(), c, take));
                left = left.minus(take);
            }
        }
        return new Result(List.copyOf(lines), left);
    }

    /** Convenience for building a {@link Due}. */
    public static Due due(int seq, Money penalty, Money fee, Money creditLife, Money interest, Money principal) {
        Map<Component, Money> m = new EnumMap<>(Component.class);
        m.put(Component.PENALTY, penalty);
        m.put(Component.FEE, fee);
        m.put(Component.CREDIT_LIFE, creditLife);
        m.put(Component.INTEREST, interest);
        m.put(Component.PRINCIPAL, principal);
        return new Due(seq, m);
    }

    private AllocationWaterfall() {
    }
}

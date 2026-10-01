package zw.insurehub.lendhub.products.schedule;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;

/**
 * Builds flat-rate and reducing-balance schedules (docs/03-architecture.md §5, ADR-0002).
 * <p>
 * Invariants, proven by property tests: Σ principal = principal exactly; no negative component;
 * the closing balance of the last instalment is zero.
 */
public final class ScheduleCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;

    private final BusinessDays businessDays;

    public ScheduleCalculator(BusinessDays businessDays) {
        this.businessDays = businessDays;
    }

    public Schedule calculate(ScheduleTerms t) {
        CurrencyCode ccy = t.principal().currency();
        BigDecimal i = t.frequency().periodicRate(t.monthlyInterestRate());
        List<Money[]> parts = t.method() == RepaymentMethod.FLAT
                ? flat(t.principal(), i, t.instalments())
                : reducing(t.principal(), i, t.instalments());

        BigDecimal clPeriodRate = t.frequency().periodicRate(t.creditLifeMonthlyRate());
        Money creditLife = t.principal().times(clPeriodRate, HALF_UP);
        Money fee = t.principal().times(t.establishmentFeeRate(), HALF_UP);

        List<Schedule.Line> lines = new ArrayList<>(t.instalments());
        Money balance = t.principal();
        Money totalInterest = Money.zero(ccy);
        Money totalCl = Money.zero(ccy);
        Money totalRepayable = Money.zero(ccy);
        for (int k = 1; k <= t.instalments(); k++) {
            Money principal = parts.get(k - 1)[0];
            Money interest = parts.get(k - 1)[1];
            balance = balance.minus(principal);
            Money total = principal.plus(interest).plus(creditLife);
            LocalDate due = businessDays.nextBusinessDay(t.frequency().dueDate(t.startDate(), k));
            lines.add(new Schedule.Line(k, due, principal, interest, creditLife, total, balance));
            totalInterest = totalInterest.plus(interest);
            totalCl = totalCl.plus(creditLife);
            totalRepayable = totalRepayable.plus(total);
        }
        Money net = t.principal().minus(fee);
        BigDecimal eir = effectiveAnnualRate(net, lines, t.frequency());
        return new Schedule(List.copyOf(lines), t.principal(), totalInterest, totalCl, fee, net, totalRepayable, eir);
    }

    /**
     * Flat: total interest = P × i × n. Each component is spread evenly with HALF_UP rounding and the last
     * instalment absorbs the residue. Components are capped by what is left, so the residue is never negative.
     * Golden case: P = 1,000, 5%/month, 6 months → 216.67 × 5 and 216.65.
     */
    static List<Money[]> flat(Money p, BigDecimal i, int n) {
        CurrencyCode ccy = p.currency();
        Money totalInterest = p.times(i.multiply(BigDecimal.valueOf(n), MC), HALF_UP);
        Money evenPrincipal = Money.rounded(p.amount().divide(BigDecimal.valueOf(n), MC), ccy, HALF_UP);
        Money evenInterest = Money.rounded(totalInterest.amount().divide(BigDecimal.valueOf(n), MC), ccy, HALF_UP);
        List<Money[]> out = new ArrayList<>(n);
        Money principalLeft = p;
        Money interestLeft = totalInterest;
        for (int k = 1; k <= n; k++) {
            Money principal = k == n ? principalLeft : evenPrincipal.min(principalLeft);
            Money interest = k == n ? interestLeft : evenInterest.min(interestLeft);
            principalLeft = principalLeft.minus(principal);
            interestLeft = interestLeft.minus(interest);
            out.add(new Money[] { principal, interest });
        }
        return out;
    }

    /**
     * Reducing balance with equal instalments: A = P·i / (1 − (1 + i)^−n), interest on the opening balance,
     * last instalment pays off whatever principal is left. Golden case: P = 1,000, 5%/month, 6 → 197.02.
     */
    static List<Money[]> reducing(Money p, BigDecimal i, int n) {
        CurrencyCode ccy = p.currency();
        Money instalment = annuity(p, i, n);
        List<Money[]> out = new ArrayList<>(n);
        Money outstanding = p;
        for (int k = 1; k <= n; k++) {
            Money interest = outstanding.times(i, HALF_UP);
            Money principal = k == n ? outstanding : instalment.minus(interest).min(outstanding).max(Money.zero(ccy));
            outstanding = outstanding.minus(principal);
            out.add(new Money[] { principal, interest });
        }
        return out;
    }

    public static Money annuity(Money p, BigDecimal i, int n) {
        if (i.signum() == 0) {
            return Money.rounded(p.amount().divide(BigDecimal.valueOf(n), MC), p.currency(), HALF_UP);
        }
        BigDecimal onePlusIPowMinusN = BigDecimal.ONE.divide(BigDecimal.ONE.add(i).pow(n, MC), MC);
        BigDecimal factor = i.divide(BigDecimal.ONE.subtract(onePlusIPowMinusN), MC);
        return Money.rounded(p.amount().multiply(factor, MC), p.currency(), HALF_UP);
    }

    /**
     * IRR of (−net disbursed, +instalment totals) by Newton–Raphson, annualised: (1 + irr)^periodsPerYear − 1.
     * This is a disclosure figure, so double precision is fine here; no money is stored from it.
     */
    static BigDecimal effectiveAnnualRate(Money net, List<Schedule.Line> lines, Frequency frequency) {
        double[] cf = new double[lines.size() + 1];
        cf[0] = -net.amount().doubleValue();
        for (int k = 0; k < lines.size(); k++) {
            cf[k + 1] = lines.get(k).total().amount().doubleValue();
        }
        double r = 0.05;
        for (int iter = 0; iter < 200; iter++) {
            double f = 0;
            double df = 0;
            for (int k = 0; k < cf.length; k++) {
                double disc = Math.pow(1 + r, k);
                f += cf[k] / disc;
                df -= k * cf[k] / (disc * (1 + r));
            }
            if (df == 0) break;
            double next = r - f / df;
            if (next <= -0.99) next = (r - 0.99) / 2;
            if (Math.abs(next - r) < 1e-12) {
                r = next;
                break;
            }
            r = next;
        }
        double annual = Math.pow(1 + r, frequency.periodsPerYear()) - 1;
        if (Double.isNaN(annual) || Double.isInfinite(annual)) return BigDecimal.ZERO;
        return BigDecimal.valueOf(annual * 100).setScale(2, RoundingMode.HALF_UP);
    }
}

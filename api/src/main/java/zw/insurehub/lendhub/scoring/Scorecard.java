package zw.insurehub.lendhub.scoring;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Points-based scorecard (LH-11). Every point awarded or withheld comes with a plain-language reason,
 * so the approver and the borrower can see why. Weights are illustrative, not a validated model.
 */
public final class Scorecard {

    public enum Grade { A, B, C, D, E }

    public record Input(boolean salaried, BigDecimal yearsInEmployment, CreditBureau.BureauReport bureau,
            RepaymentHistoryProvider.RepaymentHistory history, boolean groupMember, BigDecimal affordabilityRatio,
            BigDecimal maxAffordabilityRatio) {
    }

    public record Result(int points, Grade grade, List<String> reasons, boolean declineRecommended) {
    }

    public static Result score(Input in) {
        List<String> reasons = new ArrayList<>();
        int points = 0;

        if (in.salaried()) {
            points += 25;
            reasons.add("+25 salaried income (payroll deduction possible)");
        } else {
            points += 15;
            reasons.add("+15 self-employed / trader income");
        }

        BigDecimal years = in.yearsInEmployment() == null ? BigDecimal.ZERO : in.yearsInEmployment();
        if (years.compareTo(BigDecimal.valueOf(3)) >= 0) {
            points += 20;
            reasons.add("+20 three or more years in current job or business");
        } else if (years.compareTo(BigDecimal.ONE) >= 0) {
            points += 10;
            reasons.add("+10 one to three years in current job or business");
        } else {
            reasons.add("+0 less than one year in current job or business");
        }

        boolean adverse = false;
        switch (in.bureau().status()) {
            case CLEAR -> {
                points += 25;
                reasons.add("+25 credit bureau: clear record");
            }
            case THIN_FILE -> {
                points += 10;
                reasons.add("+10 credit bureau: little or no credit history");
            }
            case ADVERSE -> {
                adverse = true;
                reasons.add("Credit bureau: adverse listing — grade E");
            }
        }

        var h = in.history();
        if (h.closedLoans() == 0 && h.openLoans() == 0) {
            points += 5;
            reasons.add("+5 new customer (no repayment history with us)");
        } else if (h.worstDaysPastDue() <= 7) {
            points += 20;
            reasons.add("+20 good repayment history with us (worst arrears ≤ 7 days)");
        } else if (h.worstDaysPastDue() <= 30) {
            points += 5;
            reasons.add("+5 fair repayment history with us (worst arrears ≤ 30 days)");
        } else {
            points -= 20;
            reasons.add("−20 poor repayment history with us (arrears over 30 days)");
        }

        if (in.groupMember()) {
            points += 5;
            reasons.add("+5 member of a solidarity group (joint liability)");
        }

        boolean unaffordable = in.affordabilityRatio().compareTo(in.maxAffordabilityRatio()) > 0;
        if (unaffordable) {
            reasons.add("Instalment is " + pct(in.affordabilityRatio()) + " of disposable income, above the "
                    + pct(in.maxAffordabilityRatio()) + " limit — grade E");
        } else if (in.affordabilityRatio().compareTo(new BigDecimal("0.25")) <= 0) {
            points += 10;
            reasons.add("+10 instalment is " + pct(in.affordabilityRatio()) + " of disposable income");
        } else {
            points += 5;
            reasons.add("+5 instalment is " + pct(in.affordabilityRatio()) + " of disposable income");
        }

        Grade grade = adverse || unaffordable ? Grade.E
                : points >= 80 ? Grade.A
                : points >= 65 ? Grade.B
                : points >= 50 ? Grade.C
                : points >= 35 ? Grade.D
                : Grade.E;
        return new Result(points, grade, List.copyOf(reasons), grade == Grade.E);
    }

    private static String pct(BigDecimal ratio) {
        return ratio.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP) + "%";
    }

    private Scorecard() {
    }
}

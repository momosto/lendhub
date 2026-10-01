package zw.insurehub.lendhub.products.schedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import zw.insurehub.lendhub.shared.money.Money;

/**
 * A repayment schedule with the totals a borrower must see before accepting (full cost disclosure, LH-13).
 *
 * @param effectiveAnnualRatePercent EIR from the actual cash flows (IRR), annualised, in percent, 2 dp
 */
public record Schedule(
        List<Line> instalments,
        Money principal,
        Money totalInterest,
        Money totalCreditLife,
        Money establishmentFee,
        Money netDisbursed,
        Money totalRepayable,
        BigDecimal effectiveAnnualRatePercent) {

    /** One instalment. {@code total = principal + interest + creditLife}. */
    public record Line(int seq, LocalDate dueDate, Money principal, Money interest, Money creditLife, Money total,
            Money closingBalance) {
    }

    public Line first() {
        return instalments.getFirst();
    }
}

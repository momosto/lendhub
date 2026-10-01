package zw.insurehub.lendhub.loans;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import zw.insurehub.lendhub.loans.domain.Component;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;

/** One row of a loan's schedule, with what is due, what is paid and how much interest has been accrued. */
@Entity
@Table(name = "instalment", schema = "loans")
public class Instalment {

    public enum State { OPEN, PAID, CANCELLED }

    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id")
    private LoanAccount loan;
    private int seq;
    private LocalDate periodStart;
    private LocalDate dueDate;
    private BigDecimal principalDue;
    private BigDecimal interestDue;
    private BigDecimal creditLifeDue;
    private BigDecimal feesDue;
    private BigDecimal penaltyDue;
    private BigDecimal principalPaid;
    private BigDecimal interestPaid;
    private BigDecimal creditLifePaid;
    private BigDecimal feesPaid;
    private BigDecimal penaltyPaid;
    private BigDecimal interestAccrued;
    @Enumerated(EnumType.STRING)
    private State state;
    private LocalDate paidOn;

    protected Instalment() {
    }

    Instalment(LoanAccount loan, int seq, LocalDate periodStart, LocalDate dueDate, Money principal, Money interest, Money creditLife) {
        this.id = UUID.randomUUID();
        this.loan = loan;
        this.seq = seq;
        this.periodStart = periodStart;
        this.dueDate = dueDate;
        this.principalDue = principal.amount();
        this.interestDue = interest.amount();
        this.creditLifeDue = creditLife.amount();
        this.feesDue = BigDecimal.ZERO.setScale(2);
        this.penaltyDue = BigDecimal.ZERO.setScale(2);
        this.principalPaid = BigDecimal.ZERO.setScale(2);
        this.interestPaid = BigDecimal.ZERO.setScale(2);
        this.creditLifePaid = BigDecimal.ZERO.setScale(2);
        this.feesPaid = BigDecimal.ZERO.setScale(2);
        this.penaltyPaid = BigDecimal.ZERO.setScale(2);
        this.interestAccrued = BigDecimal.ZERO.setScale(2);
        this.state = State.OPEN;
    }

    private CurrencyCode ccy() {
        return loan.getCurrency();
    }

    public Money due(Component c) {
        return Money.of(switch (c) {
            case PRINCIPAL -> principalDue;
            case INTEREST -> interestDue;
            case CREDIT_LIFE -> creditLifeDue;
            case FEE -> feesDue;
            case PENALTY -> penaltyDue;
        }, ccy());
    }

    public Money paid(Component c) {
        return Money.of(switch (c) {
            case PRINCIPAL -> principalPaid;
            case INTEREST -> interestPaid;
            case CREDIT_LIFE -> creditLifePaid;
            case FEE -> feesPaid;
            case PENALTY -> penaltyPaid;
        }, ccy());
    }

    public Money outstanding(Component c) {
        return due(c).minus(paid(c));
    }

    public Money totalDue() {
        Money sum = Money.zero(ccy());
        for (Component c : Component.values()) sum = sum.plus(due(c));
        return sum;
    }

    public Money totalOutstanding() {
        Money sum = Money.zero(ccy());
        for (Component c : Component.values()) sum = sum.plus(outstanding(c));
        return sum;
    }

    public Money totalPaid() {
        Money sum = Money.zero(ccy());
        for (Component c : Component.values()) sum = sum.plus(paid(c));
        return sum;
    }

    void pay(Component c, Money amount, LocalDate on) {
        BigDecimal a = amount.amount();
        switch (c) {
            case PRINCIPAL -> principalPaid = principalPaid.add(a);
            case INTEREST -> interestPaid = interestPaid.add(a);
            case CREDIT_LIFE -> creditLifePaid = creditLifePaid.add(a);
            case FEE -> feesPaid = feesPaid.add(a);
            case PENALTY -> penaltyPaid = penaltyPaid.add(a);
        }
        if (outstanding(c).isNegative()) {
            throw new IllegalStateException("Over-allocation to " + c + " on instalment " + seq);
        }
        if (state == State.OPEN && totalOutstanding().isZero()) {
            state = State.PAID;
            paidOn = on;
        } else if (state == State.PAID && !totalOutstanding().isZero()) {
            state = State.OPEN;
            paidOn = null;
        }
    }

    void addPenalty(Money amount) {
        penaltyDue = penaltyDue.add(amount.amount());
    }

    void addAccrual(Money amount) {
        interestAccrued = interestAccrued.add(amount.amount());
    }

    void addDue(Component c, Money amount) {
        BigDecimal a = amount.amount();
        switch (c) {
            case PRINCIPAL -> principalDue = principalDue.add(a);
            case INTEREST -> interestDue = interestDue.add(a);
            case CREDIT_LIFE -> creditLifeDue = creditLifeDue.add(a);
            case FEE -> feesDue = feesDue.add(a);
            case PENALTY -> penaltyDue = penaltyDue.add(a);
        }
    }

    /** Early settlement: unearned interest and future credit life are waived down to what has been paid or accrued. */
    void truncateForSettlement(boolean dueYet) {
        BigDecimal earned = interestAccrued.max(interestPaid);
        interestDue = earned.min(interestDue);
        if (!dueYet) {
            creditLifeDue = creditLifePaid;
        }
    }

    void cancel() {
        state = State.CANCELLED;
    }

    void markAccruedInterest(Money accrued) {
        interestAccrued = accrued.amount();
    }

    public UUID getId() { return id; }
    public int getSeq() { return seq; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getDueDate() { return dueDate; }
    public Money getInterestAccrued() { return Money.of(interestAccrued, ccy()); }
    public State getState() { return state; }
    public LocalDate getPaidOn() { return paidOn; }
    public boolean isOpen() { return state == State.OPEN; }
}

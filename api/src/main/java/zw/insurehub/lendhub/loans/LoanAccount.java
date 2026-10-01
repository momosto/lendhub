package zw.insurehub.lendhub.loans;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import zw.insurehub.lendhub.loans.domain.AllocationWaterfall;
import zw.insurehub.lendhub.loans.domain.ArrearsBucket;
import zw.insurehub.lendhub.loans.domain.Component;
import zw.insurehub.lendhub.loans.domain.InterestRules;
import zw.insurehub.lendhub.products.schedule.Frequency;
import zw.insurehub.lendhub.products.schedule.RepaymentMethod;
import zw.insurehub.lendhub.products.schedule.Schedule;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;
import zw.insurehub.lendhub.shared.web.DomainException;

/**
 * Loan account aggregate. Every state change is a method with guards, so an illegal transition
 * (e.g. disbursing without credit life cover) cannot happen. See the state machine in docs/03-architecture.md §4.
 */
@Entity
@Table(name = "loan_account", schema = "loans")
@Audited
public class LoanAccount {

    public enum Status { AWAITING_COVER, COVERED, ACTIVE, IN_ARREARS, RESTRUCTURED, CLOSED, WRITTEN_OFF }

    @Id
    private UUID id;
    @Column(unique = true, nullable = false)
    private String loanNumber;
    private UUID applicationId;
    private String applicationNumber;
    private UUID borrowerId;
    private String customerRef;
    private String borrowerName;
    private String msisdn;
    private String branch;
    private String productCode;
    @Enumerated(EnumType.STRING)
    private CurrencyCode currency;
    private BigDecimal principal;
    @Enumerated(EnumType.STRING)
    private RepaymentMethod method;
    @Enumerated(EnumType.STRING)
    private Frequency frequency;
    private int instalmentCount;
    private BigDecimal penaltyMonthlyRate;
    private int graceDays;
    private boolean inDuplumEnabled;
    private BigDecimal settlementFeeRate;
    private BigDecimal establishmentFee;
    private BigDecimal netDisbursed;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String creditLifePolicyNumber;
    private String coverError;
    private String disbursementReference;
    private LocalDate disbursedOn;
    private LocalDate maturityDate;
    private int daysPastDue;
    private int worstDaysPastDue;
    @Enumerated(EnumType.STRING)
    private ArrearsBucket arrearsBucket;
    private boolean restructured;
    private LocalDate restructuredOn;
    private LocalDate writtenOffOn;
    private LocalDate closedOn;
    private BigDecimal creditBalance;
    private String capturedBy;
    private String approvedBy;
    private String disbursedBy;
    private Instant createdAt;
    @Version
    private Long version;

    @NotAudited
    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
    @OrderBy("seq")
    private List<Instalment> instalments = new ArrayList<>();

    protected LoanAccount() {
    }

    static LoanAccount book(String loanNumber, UUID applicationId, String applicationNumber, UUID borrowerId, String customerRef,
            String borrowerName, String msisdn, String branch, String productCode, Money principal, RepaymentMethod method,
            Frequency frequency, int instalments, BigDecimal penaltyRate, int graceDays, boolean inDuplum,
            BigDecimal settlementFeeRate, String capturedBy, String approvedBy) {
        var loan = new LoanAccount();
        loan.id = UUID.randomUUID();
        loan.loanNumber = loanNumber;
        loan.applicationId = applicationId;
        loan.applicationNumber = applicationNumber;
        loan.borrowerId = borrowerId;
        loan.customerRef = customerRef;
        loan.borrowerName = borrowerName;
        loan.msisdn = msisdn;
        loan.branch = branch;
        loan.productCode = productCode;
        loan.currency = principal.currency();
        loan.principal = principal.amount();
        loan.method = method;
        loan.frequency = frequency;
        loan.instalmentCount = instalments;
        loan.penaltyMonthlyRate = penaltyRate;
        loan.graceDays = graceDays;
        loan.inDuplumEnabled = inDuplum;
        loan.settlementFeeRate = settlementFeeRate == null ? BigDecimal.ZERO : settlementFeeRate;
        loan.creditBalance = BigDecimal.ZERO.setScale(2);
        loan.arrearsBucket = ArrearsBucket.CURRENT;
        loan.status = Status.AWAITING_COVER;
        loan.capturedBy = capturedBy;
        loan.approvedBy = approvedBy;
        loan.createdAt = Instant.now();
        return loan;
    }

    // ---- lifecycle -------------------------------------------------------------------------------------------

    void covered(String policyNumber) {
        require(Status.AWAITING_COVER);
        this.creditLifePolicyNumber = policyNumber;
        this.coverError = null;
        this.status = Status.COVERED;
    }

    void coverFailed(String error) {
        require(Status.AWAITING_COVER);
        this.coverError = error;
    }

    /** LH-20/21: disbursement needs confirmed credit life cover and a different person from the maker and checker. */
    void disburse(Schedule schedule, LocalDate on, String user, String reference) {
        require(Status.COVERED);
        if (creditLifePolicyNumber == null) {
            throw DomainException.rule("no-cover", "Disbursement is blocked until credit life cover is confirmed");
        }
        if (user.equalsIgnoreCase(capturedBy) || user.equalsIgnoreCase(approvedBy)) {
            throw DomainException.forbidden("maker-checker", "Disbursement must be released by someone other than the capturer and approver");
        }
        replaceSchedule(schedule, on);
        this.establishmentFee = schedule.establishmentFee().amount();
        this.netDisbursed = schedule.netDisbursed().amount();
        this.disbursedOn = on;
        this.disbursedBy = user;
        this.disbursementReference = reference;
        this.status = Status.ACTIVE;
    }

    private void replaceSchedule(Schedule schedule, LocalDate start) {
        LocalDate periodStart = start;
        for (Schedule.Line line : schedule.instalments()) {
            instalments.add(new Instalment(this, nextSeq(), periodStart, line.dueDate(), line.principal(), line.interest(),
                    line.creditLife()));
            periodStart = line.dueDate();
        }
        this.maturityDate = schedule.instalments().getLast().dueDate();
    }

    private int nextSeq() {
        return instalments.stream().mapToInt(Instalment::getSeq).max().orElse(0) + 1;
    }

    // ---- repayments --------------------------------------------------------------------------------------------

    /** Instalments that can take money today: open and due on or before {@code today}, oldest first. */
    List<Instalment> dueInstalments(LocalDate today) {
        return instalments.stream().filter(Instalment::isOpen).filter(i -> !i.getDueDate().isAfter(today))
                .sorted(Comparator.comparing(Instalment::getDueDate)).toList();
    }

    List<Instalment> openInstalments() {
        return instalments.stream().filter(Instalment::isOpen).sorted(Comparator.comparing(Instalment::getDueDate)).toList();
    }

    /**
     * Allocates a payment over due instalments (waterfall); anything left becomes a credit balance that is applied
     * to the next instalment when it falls due (LH-43).
     */
    AllocationWaterfall.Result receive(Money amount, LocalDate today) {
        requireCurrency(amount);
        if (status != Status.ACTIVE && status != Status.IN_ARREARS && status != Status.RESTRUCTURED) {
            throw DomainException.rule("not-repayable", "Loan " + loanNumber + " is " + status);
        }
        var result = AllocationWaterfall.allocate(amount, dues(dueInstalments(today)), Component.DEFAULT_ORDER);
        apply(result.lines(), today);
        creditBalance = creditBalance.add(result.remainder().amount());
        closeIfRepaid(today);
        return result;
    }

    /** EOD: applies any credit balance to instalments that have fallen due. */
    Optional<AllocationWaterfall.Result> applyCredit(LocalDate today) {
        if (creditBalance.signum() <= 0 || !isServicing()) return Optional.empty();
        var due = dueInstalments(today);
        if (due.isEmpty()) return Optional.empty();
        Money credit = Money.of(creditBalance, currency);
        var result = AllocationWaterfall.allocate(credit, dues(due), Component.DEFAULT_ORDER);
        if (result.lines().isEmpty()) return Optional.empty();
        apply(result.lines(), today);
        creditBalance = result.remainder().amount();
        closeIfRepaid(today);
        return Optional.of(result);
    }

    /** Compensating entries for a reversed payment: nothing is deleted (LH-44). */
    void reverse(List<AllocationWaterfall.Line> lines, Money creditAdded, LocalDate today) {
        for (var line : lines) {
            instalment(line.seq()).pay(line.component(), Money.zero(currency).minus(line.amount()), today);
        }
        creditBalance = creditBalance.subtract(creditAdded.amount());
        if (status == Status.CLOSED) {
            status = Status.ACTIVE;
            closedOn = null;
        }
    }

    private void apply(List<AllocationWaterfall.Line> lines, LocalDate today) {
        for (var line : lines) {
            instalment(line.seq()).pay(line.component(), line.amount(), today);
        }
    }

    private static List<AllocationWaterfall.Due> dues(List<Instalment> list) {
        return list.stream().map(i -> AllocationWaterfall.due(i.getSeq(), i.outstanding(Component.PENALTY),
                i.outstanding(Component.FEE), i.outstanding(Component.CREDIT_LIFE), i.outstanding(Component.INTEREST),
                i.outstanding(Component.PRINCIPAL))).toList();
    }

    private void closeIfRepaid(LocalDate today) {
        if (instalments.stream().noneMatch(Instalment::isOpen)) {
            status = Status.CLOSED;
            closedOn = today;
            daysPastDue = 0;
            arrearsBucket = ArrearsBucket.CURRENT;
        }
    }

    // ---- early settlement (LH-43) -----------------------------------------------------------------------------

    record SettlementQuote(Money outstandingPrincipal, Money accruedInterest, Money arrearsCharges, Money settlementFee,
            Money creditBalance, Money total) {
    }

    SettlementQuote settlementQuote(LocalDate asOf) {
        Money principalOut = outstandingPrincipal();
        Money accrued = Money.zero(currency);
        Money charges = Money.zero(currency);
        for (Instalment i : openInstalments()) {
            Money earned = i.getInterestAccrued().max(i.paid(Component.INTEREST)).min(i.due(Component.INTEREST));
            accrued = accrued.plus(earned.minus(i.paid(Component.INTEREST)).max(Money.zero(currency)));
            charges = charges.plus(i.outstanding(Component.PENALTY)).plus(i.outstanding(Component.FEE));
            if (!i.getDueDate().isAfter(asOf)) {
                charges = charges.plus(i.outstanding(Component.CREDIT_LIFE));
            }
        }
        Money fee = principalOut.times(settlementFeeRate, RoundingMode.HALF_UP);
        Money credit = Money.of(creditBalance, currency);
        Money total = principalOut.plus(accrued).plus(charges).plus(fee).minus(credit).max(Money.zero(currency));
        return new SettlementQuote(principalOut, accrued, charges, fee, credit, total);
    }

    /** Waives unearned interest and future credit life, adds the settlement fee, then takes the payment. */
    AllocationWaterfall.Result settle(Money amount, LocalDate today) {
        var quote = settlementQuote(today);
        if (amount.compareTo(quote.total()) < 0) {
            throw DomainException.rule("insufficient-settlement", "Settlement needs " + quote.total() + " but got " + amount);
        }
        for (Instalment i : openInstalments()) {
            i.truncateForSettlement(!i.getDueDate().isAfter(today));
        }
        var open = openInstalments();
        if (quote.settlementFee().isPositive() && !open.isEmpty()) {
            open.getFirst().addDue(Component.FEE, quote.settlementFee());
        }
        Money pot = amount.plus(Money.of(creditBalance, currency));
        creditBalance = BigDecimal.ZERO.setScale(2);
        var result = AllocationWaterfall.allocate(pot, dues(openInstalments()), Component.DEFAULT_ORDER);
        apply(result.lines(), today);
        creditBalance = result.remainder().amount();
        closeIfRepaid(today);
        return result;
    }

    // ---- end of day ------------------------------------------------------------------------------------------------

    /** LH-50: accrue one day of interest on the instalment whose period contains {@code day}. */
    Money accrue(LocalDate day) {
        if (!isServicing()) return Money.zero(currency);
        for (Instalment i : instalments) {
            if (i.getState() == Instalment.State.CANCELLED) continue;
            if (day.isAfter(i.getPeriodStart()) && !day.isAfter(i.getDueDate())) {
                Money amount = InterestRules.dailyAccrual(i.due(Component.INTEREST), i.getInterestAccrued(),
                        i.getPeriodStart(), i.getDueDate(), day);
                if (amount.isPositive()) i.addAccrual(amount);
                return amount;
            }
        }
        return Money.zero(currency);
    }

    /** LH-51: penalty on overdue amounts after the grace period, capped by the in duplum rule. */
    Money chargePenalty(LocalDate day) {
        if (!isServicing()) return Money.zero(currency);
        var overdueInstalments = instalments.stream().filter(Instalment::isOpen)
                .filter(i -> i.getDueDate().plusDays(graceDays).isBefore(day))
                .sorted(Comparator.comparing(Instalment::getDueDate)).toList();
        if (overdueInstalments.isEmpty()) return Money.zero(currency);
        Money overdue = Money.zero(currency);
        for (Instalment i : overdueInstalments) {
            overdue = overdue.plus(i.outstanding(Component.PRINCIPAL)).plus(i.outstanding(Component.INTEREST));
        }
        Money penalty = InterestRules.dailyPenalty(overdue, penaltyMonthlyRate);
        if (inDuplumEnabled) {
            penalty = InterestRules.inDuplumAllowance(penalty, arrearCharges(day), outstandingPrincipal());
        }
        if (penalty.isPositive()) overdueInstalments.getFirst().addPenalty(penalty);
        return penalty;
    }

    /** Unpaid interest on overdue instalments plus all unpaid penalties: the amount the in duplum rule caps. */
    Money arrearCharges(LocalDate day) {
        Money total = Money.zero(currency);
        for (Instalment i : instalments) {
            if (!i.isOpen()) continue;
            total = total.plus(i.outstanding(Component.PENALTY));
            if (i.getDueDate().isBefore(day)) total = total.plus(i.outstanding(Component.INTEREST));
        }
        return total;
    }

    /** LH-52: days past due from the oldest unpaid due date, with bucket and status transitions. */
    Optional<ArrearsBucket> age(LocalDate today) {
        if (!isServicing()) return Optional.empty();
        var oldest = instalments.stream().filter(Instalment::isOpen).filter(i -> i.getDueDate().isBefore(today))
                .map(Instalment::getDueDate).min(LocalDate::compareTo);
        daysPastDue = oldest.map(d -> (int) ChronoUnit.DAYS.between(d, today)).orElse(0);
        worstDaysPastDue = Math.max(worstDaysPastDue, daysPastDue);
        ArrearsBucket before = arrearsBucket;
        arrearsBucket = ArrearsBucket.of(daysPastDue);

        if (daysPastDue > 0 && (status == Status.ACTIVE || status == Status.RESTRUCTURED)) {
            status = Status.IN_ARREARS;
        } else if (daysPastDue == 0 && status == Status.IN_ARREARS) {
            status = restructured ? Status.RESTRUCTURED : Status.ACTIVE;
        }
        // LH-62: restructured loans cure only after 3 performing months
        if (status == Status.RESTRUCTURED && daysPastDue == 0 && !today.isBefore(restructuredOn.plusMonths(3))) {
            restructured = false;
            status = Status.ACTIVE;
        }
        return before == arrearsBucket ? Optional.empty() : Optional.of(before);
    }

    // ---- restructure and write-off (LH-62, LH-63) ------------------------------------------------------------------

    void restructure(Schedule newSchedule, LocalDate today) {
        if (status != Status.IN_ARREARS && status != Status.ACTIVE && status != Status.RESTRUCTURED) {
            throw DomainException.rule("not-restructurable", "Only active or in-arrears loans can be restructured");
        }
        Money carriedInterest = Money.zero(currency);
        Money carriedPenalty = Money.zero(currency);
        Money carriedFee = Money.zero(currency);
        Money carriedCl = Money.zero(currency);
        for (Instalment i : openInstalments()) {
            Money accruedUnpaid = i.getInterestAccrued().min(i.due(Component.INTEREST)).minus(i.paid(Component.INTEREST))
                    .max(Money.zero(currency));
            carriedInterest = carriedInterest.plus(accruedUnpaid);
            carriedPenalty = carriedPenalty.plus(i.outstanding(Component.PENALTY));
            carriedFee = carriedFee.plus(i.outstanding(Component.FEE));
            if (!i.getDueDate().isAfter(today)) carriedCl = carriedCl.plus(i.outstanding(Component.CREDIT_LIFE));
            i.cancel();
        }
        int firstNew = nextSeq();
        replaceSchedule(newSchedule, today);
        Instalment first = instalment(firstNew);
        first.addDue(Component.INTEREST, carriedInterest);
        first.markAccruedInterest(carriedInterest);
        first.addDue(Component.PENALTY, carriedPenalty);
        first.addDue(Component.FEE, carriedFee);
        first.addDue(Component.CREDIT_LIFE, carriedCl);
        this.instalmentCount = newSchedule.instalments().size();
        this.restructured = true;
        this.restructuredOn = today;
        this.status = Status.RESTRUCTURED;
        this.daysPastDue = 0;
        this.arrearsBucket = ArrearsBucket.CURRENT;
    }

    record WriteOffAmounts(Money principal, Money interest, Money penalties) {
    }

    WriteOffAmounts writeOff(LocalDate today) {
        if (status != Status.IN_ARREARS && status != Status.RESTRUCTURED && status != Status.ACTIVE) {
            throw DomainException.rule("not-writable-off", "Loan " + loanNumber + " is " + status);
        }
        Money principalOut = outstandingPrincipal();
        Money interest = Money.zero(currency);
        Money penalties = Money.zero(currency);
        for (Instalment i : openInstalments()) {
            interest = interest.plus(i.getInterestAccrued().min(i.due(Component.INTEREST)).minus(i.paid(Component.INTEREST))
                    .max(Money.zero(currency)));
            penalties = penalties.plus(i.outstanding(Component.PENALTY));
            i.cancel();
        }
        this.status = Status.WRITTEN_OFF;
        this.writtenOffOn = today;
        return new WriteOffAmounts(principalOut, interest, penalties);
    }

    // ---- queries ---------------------------------------------------------------------------------------------------

    public Money outstandingPrincipal() {
        Money sum = Money.zero(currency);
        for (Instalment i : instalments) {
            if (i.isOpen()) sum = sum.plus(i.outstanding(Component.PRINCIPAL));
        }
        return sum;
    }

    /** Accrued interest not yet paid (the interest receivable for this loan). */
    public Money interestReceivable() {
        Money sum = Money.zero(currency);
        for (Instalment i : instalments) {
            if (i.isOpen()) sum = sum.plus(i.getInterestAccrued().minus(i.paid(Component.INTEREST)).max(Money.zero(currency)));
        }
        return sum;
    }

    /** Everything overdue today (all components). */
    public Money arrearsAmount(LocalDate today) {
        Money sum = Money.zero(currency);
        for (Instalment i : instalments) {
            if (i.isOpen() && i.getDueDate().isBefore(today)) sum = sum.plus(i.totalOutstanding());
        }
        return sum;
    }

    public Optional<Instalment> nextInstalment(LocalDate today) {
        return openInstalments().stream().filter(i -> !i.getDueDate().isBefore(today)).findFirst()
                .or(() -> openInstalments().stream().findFirst());
    }

    Instalment instalment(int seq) {
        return instalments.stream().filter(i -> i.getSeq() == seq).findFirst()
                .orElseThrow(() -> new IllegalStateException("No instalment " + seq + " on " + loanNumber));
    }

    public boolean isServicing() {
        return status == Status.ACTIVE || status == Status.IN_ARREARS || status == Status.RESTRUCTURED;
    }

    private void require(Status expected) {
        if (status != expected) {
            throw DomainException.rule("invalid-status", "Loan " + loanNumber + " is " + status + ", expected " + expected);
        }
    }

    private void requireCurrency(Money m) {
        if (m.currency() != currency) {
            throw DomainException.rule("currency-mismatch", "Loan " + loanNumber + " is in " + currency + ", payment is " + m.currency());
        }
    }

    public Money principalMoney() { return Money.of(principal, currency); }
    public UUID getId() { return id; }
    public String getLoanNumber() { return loanNumber; }
    public UUID getApplicationId() { return applicationId; }
    public String getApplicationNumber() { return applicationNumber; }
    public UUID getBorrowerId() { return borrowerId; }
    public String getCustomerRef() { return customerRef; }
    public String getBorrowerName() { return borrowerName; }
    public String getMsisdn() { return msisdn; }
    public String getBranch() { return branch; }
    public String getProductCode() { return productCode; }
    public CurrencyCode getCurrency() { return currency; }
    public BigDecimal getPrincipal() { return principal; }
    public RepaymentMethod getMethod() { return method; }
    public Frequency getFrequency() { return frequency; }
    public int getInstalmentCount() { return instalmentCount; }
    public BigDecimal getEstablishmentFee() { return establishmentFee; }
    public BigDecimal getNetDisbursed() { return netDisbursed; }
    public Status getStatus() { return status; }
    public String getCreditLifePolicyNumber() { return creditLifePolicyNumber; }
    public String getCoverError() { return coverError; }
    public String getDisbursementReference() { return disbursementReference; }
    public LocalDate getDisbursedOn() { return disbursedOn; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public int getDaysPastDue() { return daysPastDue; }
    public int getWorstDaysPastDue() { return worstDaysPastDue; }
    public ArrearsBucket getArrearsBucket() { return arrearsBucket; }
    public boolean isRestructured() { return restructured; }
    public LocalDate getRestructuredOn() { return restructuredOn; }
    public LocalDate getWrittenOffOn() { return writtenOffOn; }
    public LocalDate getClosedOn() { return closedOn; }
    public Money getCreditBalance() { return Money.of(creditBalance, currency); }
    public String getCapturedBy() { return capturedBy; }
    public String getApprovedBy() { return approvedBy; }
    public String getDisbursedBy() { return disbursedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Instalment> getInstalments() { return List.copyOf(instalments); }
}

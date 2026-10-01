package zw.insurehub.lendhub.loans;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import zw.insurehub.lendhub.loans.LoanEvents.Breakdown;
import zw.insurehub.lendhub.loans.domain.AllocationWaterfall;
import zw.insurehub.lendhub.loans.domain.ArrearsBucket;
import zw.insurehub.lendhub.loans.domain.Component;
import zw.insurehub.lendhub.origination.OfferAccepted;
import zw.insurehub.lendhub.products.ProductCatalog;
import zw.insurehub.lendhub.scoring.RepaymentHistoryProvider;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;
import zw.insurehub.lendhub.shared.security.CurrentUser;
import zw.insurehub.lendhub.shared.time.BusinessDateProvider;
import zw.insurehub.lendhub.shared.web.DomainException;

/** Public API of the loans module: booking, cover, disbursement, repayments, end-of-day operations, restructures. */
@Service
@Transactional
public class LoanServicing implements RepaymentHistoryProvider {

    private static final Logger log = LoggerFactory.getLogger(LoanServicing.class);
    private static final EnumSet<LoanAccount.Status> SERVICING =
            EnumSet.of(LoanAccount.Status.ACTIVE, LoanAccount.Status.IN_ARREARS, LoanAccount.Status.RESTRUCTURED);

    /** Result of posting money to a loan, for the repayments module and the ledger. */
    public record Posting(List<AllocationWaterfall.Line> lines, Breakdown breakdown, Money creditAdded, boolean recovery,
            boolean closed) {
    }

    /** Flat view of a loan for provisioning and reports. */
    public record LoanSnapshot(UUID id, String loanNumber, String customerRef, String branch, String productCode,
            CurrencyCode currency, LoanAccount.Status status, int daysPastDue, ArrearsBucket bucket, boolean restructured,
            Money principal, Money outstandingPrincipal, Money interestReceivable, Money arrearsAmount, LocalDate disbursedOn,
            String capturedBy) {
    }

    /** What the end-of-day ageing step learned about one loan, for collections. */
    public record AgeingResult(UUID loanId, String loanNumber, String customerRef, String borrowerName, int daysPastDue,
            Money arrearsAmount, boolean inArrears) {
    }

    private final LoanAccountRepository loans;
    private final LoanChangeRequestRepository changes;
    private final ProductCatalog catalog;
    private final BusinessDateProvider businessDate;
    private final ApplicationEventPublisher events;
    private final LoanPorts.CreditLifeProvider creditLife;
    private final LoanPorts.DisbursementGateway disbursements;

    LoanServicing(LoanAccountRepository loans, LoanChangeRequestRepository changes, ProductCatalog catalog,
            BusinessDateProvider businessDate, ApplicationEventPublisher events, LoanPorts.CreditLifeProvider creditLife,
            LoanPorts.DisbursementGateway disbursements) {
        this.loans = loans;
        this.changes = changes;
        this.catalog = catalog;
        this.businessDate = businessDate;
        this.events = events;
        this.creditLife = creditLife;
        this.disbursements = disbursements;
    }

    // ---- booking and credit life (LH-20) -------------------------------------------------------------------------

    /** Runs after the origination transaction commits; the publication registry retries it if it fails. */
    @ApplicationModuleListener
    void on(OfferAccepted e) {
        if (loans.findByApplicationId(e.applicationId()).isPresent()) {
            return; // idempotent: the registry may redeliver
        }
        var product = catalog.byCode(e.productCode());
        String number = "LN-%s-%06d".formatted(businessDate.today().format(java.time.format.DateTimeFormatter.ofPattern("yyMM")),
                loans.nextNumber());
        var loan = LoanAccount.book(number, e.applicationId(), e.applicationNumber(), e.borrowerId(), e.customerRef(),
                e.borrowerName(), e.msisdn(), e.branch(), e.productCode(), Money.of(e.amount(), e.currency()),
                product.getMethod(), e.frequency(), e.instalments(), product.getPenaltyMonthlyRate(), product.getGraceDays(),
                product.isInDuplumEnabled(), product.getSettlementFeeRate(), e.capturedBy(), e.approvedBy());
        requestCover(loans.save(loan));
    }

    public LoanAccount retryCover(UUID loanId) {
        var loan = get(loanId);
        requestCover(loan);
        return loan;
    }

    private void requestCover(LoanAccount loan) {
        int months = loan.getFrequency().termInMonths(loan.getInstalmentCount()).setScale(0, java.math.RoundingMode.CEILING).intValue();
        try {
            String policy = creditLife.issuePolicy(new LoanPorts.CreditLifeProvider.CoverRequest(loan.getLoanNumber(),
                    loan.getCustomerRef(), loan.getBorrowerName(), loan.principalMoney(), months, businessDate.today()));
            loan.covered(policy);
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Credit life cover for {} failed: {}", loan.getLoanNumber(), e.getMessage());
            loan.coverFailed(e.getMessage());
        }
    }

    // ---- disbursement (LH-21) ---------------------------------------------------------------------------------------

    public LoanAccount disburse(UUID loanId) {
        var user = CurrentUser.get();
        var loan = get(loanId);
        var product = catalog.byCode(loan.getProductCode());
        LocalDate today = businessDate.today();
        var schedule = catalog.schedule(product, loan.principalMoney(), loan.getInstalmentCount(), loan.getFrequency(), today);
        if (loan.getStatus() != LoanAccount.Status.COVERED) {
            throw DomainException.rule("invalid-status", "Loan " + loan.getLoanNumber() + " is " + loan.getStatus() + ", expected COVERED");
        }
        if (user.username().equalsIgnoreCase(loan.getCapturedBy()) || user.username().equalsIgnoreCase(loan.getApprovedBy())) {
            throw DomainException.forbidden("maker-checker", "Disbursement must be released by someone other than the capturer and approver");
        }
        String reference = disbursements.disburse(loan.getLoanNumber(), loan.getMsisdn(), schedule.netDisbursed());
        loan.disburse(schedule, today, user.username(), reference);
        var first = schedule.first();
        events.publishEvent(new LoanEvents.LoanDisbursed(loan.getId(), loan.getLoanNumber(), loan.getCustomerRef(),
                loan.getMsisdn(), loan.getBranch(), loan.getProductCode(), loan.principalMoney(), schedule.establishmentFee(),
                schedule.netDisbursed(), today, first.dueDate(), first.total(), loan.getCreditLifePolicyNumber()));
        return loan;
    }

    // ---- repayments (LH-40..44) -------------------------------------------------------------------------------------

    /** Posts money to a loan. Payments on written-off loans are recoveries and are not allocated (LH-63). */
    public Posting post(UUID loanId, Money amount, LocalDate on) {
        var loan = get(loanId);
        if (loan.getStatus() == LoanAccount.Status.WRITTEN_OFF) {
            return new Posting(List.of(), zeroBreakdown(amount.currency()), Money.zero(amount.currency()), true, false);
        }
        var result = loan.receive(amount, on);
        return finish(loan, result, on);
    }

    /** Early settlement: pays everything off, waiving unearned interest (LH-43). */
    public Posting settle(UUID loanId, Money amount, LocalDate on) {
        var loan = get(loanId);
        var result = loan.settle(amount, on);
        return finish(loan, result, on);
    }

    private Posting finish(LoanAccount loan, AllocationWaterfall.Result result, LocalDate on) {
        boolean closed = loan.getStatus() == LoanAccount.Status.CLOSED;
        if (closed) {
            events.publishEvent(new LoanEvents.LoanClosed(loan.getId(), loan.getLoanNumber(), loan.getCustomerRef(), on));
        }
        return new Posting(result.lines(), breakdown(result), result.remainder(), false, closed);
    }

    public void reverse(UUID loanId, List<AllocationWaterfall.Line> lines, Money creditAdded, LocalDate on) {
        get(loanId).reverse(lines, creditAdded, on);
    }

    public LoanAccount.SettlementQuote settlementQuote(UUID loanId, LocalDate asOf) {
        return get(loanId).settlementQuote(asOf);
    }

    // ---- end of day (LH-50..53) -------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<UUID> servicingLoanIds() {
        return loans.findIdsByStatusIn(SERVICING);
    }

    public Money accrue(UUID loanId, LocalDate day) {
        var loan = get(loanId);
        Money amount = loan.accrue(day);
        if (amount.isPositive()) {
            events.publishEvent(new LoanEvents.InterestAccrued(loan.getId(), loan.getLoanNumber(), amount, day));
        }
        return amount;
    }

    public Money chargePenalty(UUID loanId, LocalDate day) {
        var loan = get(loanId);
        Money amount = loan.chargePenalty(day);
        if (amount.isPositive()) {
            events.publishEvent(new LoanEvents.PenaltyCharged(loan.getId(), loan.getLoanNumber(), amount, day));
        }
        return amount;
    }

    /** Applies credit balances to newly due instalments, then ages the loan. */
    public AgeingResult applyCreditAndAge(UUID loanId, LocalDate day) {
        var loan = get(loanId);
        loan.applyCredit(day).ifPresent(r -> {
            events.publishEvent(new LoanEvents.CreditApplied(loan.getId(), loan.getLoanNumber(), breakdown(r), day));
            if (loan.getStatus() == LoanAccount.Status.CLOSED) {
                events.publishEvent(new LoanEvents.LoanClosed(loan.getId(), loan.getLoanNumber(), loan.getCustomerRef(), day));
            }
        });
        Money arrears = loan.arrearsAmount(day);
        loan.age(day).ifPresent(from -> events.publishEvent(new LoanEvents.ArrearsBucketChanged(loan.getId(),
                loan.getLoanNumber(), loan.getCustomerRef(), loan.getMsisdn(), loan.getDaysPastDue(), from,
                loan.getArrearsBucket(), arrears, day)));
        return new AgeingResult(loan.getId(), loan.getLoanNumber(), loan.getCustomerRef(), loan.getBorrowerName(),
                loan.getDaysPastDue(), arrears, loan.getDaysPastDue() > 0);
    }

    /** LH-53: reminder 3 days before the due date, and on day 1 overdue. */
    public int sendReminders(LocalDate day) {
        int sent = 0;
        for (LoanAccount loan : loans.findByStatusIn(SERVICING)) {
            for (Instalment i : loan.openInstalments()) {
                boolean dueSoon = i.getDueDate().equals(day.plusDays(3));
                boolean dayOneOverdue = i.getDueDate().equals(day.minusDays(1));
                if (dueSoon || dayOneOverdue) {
                    events.publishEvent(new LoanEvents.InstalmentDue(loan.getId(), loan.getLoanNumber(), loan.getCustomerRef(),
                            loan.getMsisdn(), i.getDueDate(), i.totalOutstanding(), dayOneOverdue));
                    sent++;
                }
            }
        }
        return sent;
    }

    // ---- restructure and write-off: maker-checker (LH-62, LH-63) ---------------------------------------------------

    public LoanChangeRequest requestChange(UUID loanId, LoanChangeRequest.Type type, Integer newInstalments, String reason) {
        var loan = get(loanId);
        if (type == LoanChangeRequest.Type.RESTRUCTURE && (newInstalments == null || newInstalments < 1)) {
            throw DomainException.rule("instalments-required", "A restructure needs the new number of instalments");
        }
        if (!loan.isServicing()) {
            throw DomainException.rule("invalid-status", "Loan " + loan.getLoanNumber() + " is " + loan.getStatus());
        }
        return changes.save(new LoanChangeRequest(loan, type, newInstalments, reason, CurrentUser.get().username()));
    }

    public LoanChangeRequest decideChange(UUID requestId, boolean approve) {
        var request = changes.findById(requestId).orElseThrow(() -> DomainException.notFound("Change request", requestId));
        request.decide(approve, CurrentUser.get().username());
        if (!approve) return request;
        var loan = get(request.getLoanId());
        LocalDate today = businessDate.today();
        if (request.getType() == LoanChangeRequest.Type.RESTRUCTURE) {
            var product = catalog.byCode(loan.getProductCode());
            var schedule = catalog.reschedule(product, loan.outstandingPrincipal(), request.getNewInstalments(),
                    loan.getFrequency(), today);
            loan.restructure(schedule, today);
            events.publishEvent(new LoanEvents.LoanRestructured(loan.getId(), loan.getLoanNumber(), request.getNewInstalments(), today));
        } else {
            var amounts = loan.writeOff(today);
            events.publishEvent(new LoanEvents.LoanWrittenOff(loan.getId(), loan.getLoanNumber(), amounts.principal(),
                    amounts.interest(), amounts.penalties(), today));
        }
        return request;
    }

    @Transactional(readOnly = true)
    public List<LoanChangeRequest> pendingChanges() {
        return changes.findByStatusOrderByRequestedAt(LoanChangeRequest.Status.PENDING);
    }

    @Transactional(readOnly = true)
    public List<LoanChangeRequest> changesFor(UUID loanId) {
        return changes.findByLoanIdOrderByRequestedAt(loanId);
    }

    // ---- queries -----------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public LoanAccount get(UUID id) {
        return loans.findById(id).orElseThrow(() -> DomainException.notFound("Loan", id));
    }

    @Transactional(readOnly = true)
    public LoanAccount getScoped(UUID id) {
        var loan = get(id);
        var user = CurrentUser.get();
        if (!user.seesAllBranches() && !loan.getBranch().equals(user.branch())) {
            throw DomainException.notFound("Loan", id);
        }
        return loan;
    }

    @Transactional(readOnly = true)
    public Optional<LoanAccount> byNumber(String loanNumber) {
        return loans.findByLoanNumber(loanNumber);
    }

    @Transactional(readOnly = true)
    public List<LoanAccount> list() {
        var user = CurrentUser.get();
        return user.seesAllBranches() ? loans.findAllByOrderByCreatedAtDesc() : loans.findByBranchOrderByCreatedAtDesc(user.branch());
    }

    @Transactional(readOnly = true)
    public List<LoanAccount> byCustomer(String customerRef) {
        return loans.findByCustomerRefOrderByCreatedAtDesc(customerRef);
    }

    @Transactional(readOnly = true)
    public Optional<LoanAccount> activeLoanOfBorrower(UUID borrowerId) {
        return loans.findByBorrowerId(borrowerId).stream().filter(LoanAccount::isServicing).findFirst();
    }

    @Transactional(readOnly = true)
    public List<LoanSnapshot> snapshots() {
        LocalDate today = businessDate.today();
        List<LoanSnapshot> out = new ArrayList<>();
        for (LoanAccount l : loans.findAll()) {
            if (l.getDisbursedOn() == null) continue;
            out.add(new LoanSnapshot(l.getId(), l.getLoanNumber(), l.getCustomerRef(), l.getBranch(), l.getProductCode(),
                    l.getCurrency(), l.getStatus(), l.getDaysPastDue(), l.getArrearsBucket(), l.isRestructured(),
                    l.principalMoney(), l.outstandingPrincipal(), l.interestReceivable(), l.arrearsAmount(today),
                    l.getDisbursedOn(), l.getCapturedBy()));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public RepaymentHistory historyOf(UUID borrowerId) {
        var all = loans.findByBorrowerId(borrowerId);
        int closed = (int) all.stream().filter(l -> l.getStatus() == LoanAccount.Status.CLOSED).count();
        int open = (int) all.stream().filter(LoanAccount::isServicing).count();
        int worst = all.stream().mapToInt(LoanAccount::getWorstDaysPastDue).max().orElse(0);
        if (all.stream().anyMatch(l -> l.getStatus() == LoanAccount.Status.WRITTEN_OFF)) worst = Math.max(worst, 180);
        return new RepaymentHistory(closed, open, worst);
    }

    static Breakdown breakdown(AllocationWaterfall.Result r) {
        return new Breakdown(r.total(Component.PENALTY), r.total(Component.FEE), r.total(Component.CREDIT_LIFE),
                r.total(Component.INTEREST), r.total(Component.PRINCIPAL));
    }

    static Breakdown zeroBreakdown(CurrencyCode ccy) {
        Money z = Money.zero(ccy);
        return new Breakdown(z, z, z, z, z);
    }
}

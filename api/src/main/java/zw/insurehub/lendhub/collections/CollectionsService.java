package zw.insurehub.lendhub.collections;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import zw.insurehub.lendhub.loans.LoanServicing;
import zw.insurehub.lendhub.repayments.RepaymentService;
import zw.insurehub.lendhub.shared.money.Money;
import zw.insurehub.lendhub.shared.security.CurrentUser;
import zw.insurehub.lendhub.shared.time.BusinessDateProvider;
import zw.insurehub.lendhub.shared.web.DomainException;

/** Public API of the collections module: worklist generation, task handling and promises to pay. */
@Service
@Transactional
public class CollectionsService {

    private final CollectionTaskRepository tasks;
    private final PromiseToPayRepository promises;
    private final LoanServicing loans;
    private final RepaymentService repayments;
    private final BusinessDateProvider businessDate;

    CollectionsService(CollectionTaskRepository tasks, PromiseToPayRepository promises, LoanServicing loans,
            RepaymentService repayments, BusinessDateProvider businessDate) {
        this.tasks = tasks;
        this.promises = promises;
        this.loans = loans;
        this.repayments = repayments;
        this.businessDate = businessDate;
    }

    /**
     * EOD: creates the task for the highest DPD threshold reached (7 call, 30 visit, 60 demand letter, 90 legal
     * review) unless one already exists in this arrears episode; closes open tasks when the loan is current again.
     */
    public Optional<CollectionTask> onAgeing(LoanServicing.AgeingResult r, LocalDate today) {
        if (!r.inArrears()) {
            tasks.findByLoanIdAndStatus(r.loanId(), CollectionTask.Status.OPEN).forEach(t -> t.cancel("Arrears cleared on " + today));
            return Optional.empty();
        }
        var type = Arrays.stream(CollectionTask.Type.values())
                .filter(t -> t != CollectionTask.Type.FOLLOW_UP && r.daysPastDue() >= t.dpdThreshold)
                .max(Comparator.comparingInt(t -> t.dpdThreshold));
        if (type.isEmpty()) return Optional.empty();
        LocalDate episodeStart = today.minusDays(r.daysPastDue());
        if (tasks.existsByLoanIdAndTypeAndCreatedOnGreaterThanEqual(r.loanId(), type.get(), episodeStart)) {
            return Optional.empty();
        }
        return Optional.of(tasks.save(new CollectionTask(r.loanId(), r.loanNumber(), r.customerRef(), r.borrowerName(),
                type.get(), r.daysPastDue(), r.arrearsAmount().currency(), r.arrearsAmount().amount(), today, null)));
    }

    /** EOD: promises whose date has passed are kept if enough was paid since the promise, otherwise broken. */
    public int reviewPromises(LocalDate today) {
        int broken = 0;
        for (PromiseToPay p : promises.findByStatusAndPromisedDateBefore(PromiseToPay.Status.OPEN, today)) {
            Money paid = repayments.totalReceivedSince(p.getLoanId(), p.getCurrency(), p.getPromisedOn());
            boolean kept = paid.amount().compareTo(p.getAmount()) >= 0;
            p.resolve(kept);
            if (!kept) {
                broken++;
                var loan = loans.get(p.getLoanId());
                tasks.save(new CollectionTask(loan.getId(), loan.getLoanNumber(), loan.getCustomerRef(), loan.getBorrowerName(),
                        CollectionTask.Type.FOLLOW_UP, loan.getDaysPastDue(), loan.getCurrency(),
                        loan.arrearsAmount(today).amount(), today,
                        "Broken promise: " + p.getCurrency() + " " + p.getAmount() + " by " + p.getPromisedDate()));
            }
        }
        return broken;
    }

    @Transactional(readOnly = true)
    public List<CollectionTask> worklist(boolean openOnly) {
        return openOnly ? tasks.findByStatusOrderByPriorityDesc(CollectionTask.Status.OPEN) : tasks.findAllByOrderByCreatedOnDesc();
    }

    @Transactional(readOnly = true)
    public List<CollectionTask> forLoan(UUID loanId) {
        return tasks.findByLoanIdOrderByCreatedOnDesc(loanId);
    }

    public CollectionTask complete(UUID taskId, String note) {
        var task = tasks.findById(taskId).orElseThrow(() -> DomainException.notFound("Task", taskId));
        task.complete(CurrentUser.get().username(), note);
        return task;
    }

    public PromiseToPay promise(UUID taskId, LocalDate promisedDate, java.math.BigDecimal amount) {
        var task = tasks.findById(taskId).orElseThrow(() -> DomainException.notFound("Task", taskId));
        LocalDate today = businessDate.today();
        if (promisedDate.isBefore(today)) throw DomainException.rule("date-in-past", "The promised date must be today or later");
        if (amount.signum() <= 0) throw DomainException.rule("invalid-amount", "Amount must be positive");
        return promises.save(new PromiseToPay(task.getLoanId(), task.getLoanNumber(), task.getId(), today, promisedDate,
                task.getCurrency(), amount, CurrentUser.get().username()));
    }

    @Transactional(readOnly = true)
    public List<PromiseToPay> promisesFor(UUID loanId) {
        return promises.findByLoanIdOrderByPromisedDateDesc(loanId);
    }
}

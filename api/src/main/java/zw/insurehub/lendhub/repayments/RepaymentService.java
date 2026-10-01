package zw.insurehub.lendhub.repayments;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import zw.insurehub.lendhub.borrowers.BorrowerService;
import zw.insurehub.lendhub.borrowers.KycRules;
import zw.insurehub.lendhub.loans.LoanAccount;
import zw.insurehub.lendhub.loans.LoanServicing;
import zw.insurehub.lendhub.loans.domain.AllocationWaterfall;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;
import zw.insurehub.lendhub.shared.security.CurrentUser;
import zw.insurehub.lendhub.shared.time.BusinessDateProvider;
import zw.insurehub.lendhub.shared.web.DomainException;

/** Public API of the repayments module. */
@Service
@Transactional
public class RepaymentService {

    private static final Logger log = LoggerFactory.getLogger(RepaymentService.class);

    public record ReceivePayment(String loanNumber, Money amount, Repayment.Channel channel, String providerReference) {
    }

    /** Outcome of a receipt; {@code duplicate} is true when the provider reference was already posted. */
    public record Receipt(Repayment repayment, boolean duplicate) {
    }

    private final RepaymentRepository repayments;
    private final PayrollBatchRepository payrollBatches;
    private final PaymentRequestRepository paymentRequests;
    private final LoanServicing loans;
    private final BorrowerService borrowers;
    private final BusinessDateProvider businessDate;
    private final ApplicationEventPublisher events;
    private final PaymentCollector collector;

    RepaymentService(RepaymentRepository repayments, PayrollBatchRepository payrollBatches,
            PaymentRequestRepository paymentRequests, LoanServicing loans, BorrowerService borrowers,
            BusinessDateProvider businessDate, ApplicationEventPublisher events, PaymentCollector collector) {
        this.repayments = repayments;
        this.payrollBatches = payrollBatches;
        this.paymentRequests = paymentRequests;
        this.loans = loans;
        this.borrowers = borrowers;
        this.businessDate = businessDate;
        this.events = events;
        this.collector = collector;
    }

    /** Posts a payment; idempotent on the provider reference (LH-40). */
    public Receipt receive(ReceivePayment cmd) {
        Optional<Repayment> existing = repayments.findByProviderReference(cmd.providerReference());
        if (existing.isPresent()) {
            log.info("Duplicate payment {} ignored", cmd.providerReference());
            return new Receipt(existing.get(), true);
        }
        LoanAccount loan = loans.byNumber(cmd.loanNumber())
                .orElseThrow(() -> DomainException.notFound("Loan", cmd.loanNumber()));
        return new Receipt(post(loan, cmd.amount(), cmd.channel(), cmd.providerReference(), Repayment.Type.REPAYMENT), false);
    }

    public Repayment settle(UUID loanId, Money amount, Repayment.Channel channel, String providerReference) {
        if (repayments.findByProviderReference(providerReference).isPresent()) {
            throw DomainException.conflict("duplicate-reference", "Reference " + providerReference + " was already used");
        }
        return post(loans.get(loanId), amount, channel, providerReference, Repayment.Type.SETTLEMENT);
    }

    private Repayment post(LoanAccount loan, Money amount, Repayment.Channel channel, String reference, Repayment.Type type) {
        if (!amount.isPositive()) throw DomainException.rule("invalid-amount", "Amount must be positive");
        LocalDate today = businessDate.today();
        var repayment = new Repayment(loan.getId(), loan.getLoanNumber(), reference, channel, type, amount, today,
                CurrentUser.get().username());
        var posting = type == Repayment.Type.SETTLEMENT ? loans.settle(loan.getId(), amount, today)
                : loans.post(loan.getId(), amount, today);
        repayment.allocated(posting.lines().stream()
                .map(l -> new Repayment.Allocation(l.seq(), l.component(), l.amount().amount())).toList(),
                posting.recovery() ? Money.zero(amount.currency()) : posting.creditAdded(), posting.recovery());
        repayments.save(repayment);
        events.publishEvent(new RepaymentEvents.RepaymentAllocated(repayment.getId(), loan.getId(), loan.getLoanNumber(),
                amount, posting.breakdown(), posting.recovery() ? Money.zero(amount.currency()) : posting.creditAdded(),
                posting.recovery(), channel.name(), today));
        return repayment;
    }

    /** LH-44: a reversal re-opens the allocations with compensating entries; nothing is deleted. */
    public Repayment reverse(UUID repaymentId, String reason) {
        var repayment = repayments.findById(repaymentId).orElseThrow(() -> DomainException.notFound("Repayment", repaymentId));
        repayment.reversed(reason, CurrentUser.get().username());
        var ccy = repayment.getCurrency();
        var lines = repayment.getAllocations().stream()
                .map(a -> new AllocationWaterfall.Line(a.getInstalmentSeq(), a.getComponent(), Money.of(a.getAmount(), ccy)))
                .toList();
        Money credit = Money.of(repayment.getCreditAdded(), ccy);
        boolean recovery = repayment.getType() == Repayment.Type.RECOVERY;
        if (!recovery) {
            loans.reverse(repayment.getLoanId(), lines, credit, businessDate.today());
        }
        var result = new AllocationWaterfall.Result(lines, credit);
        events.publishEvent(new RepaymentEvents.RepaymentReversed(repayment.getId(), repayment.getLoanId(),
                repayment.getLoanNumber(), repayment.amountMoney(), breakdownOf(result, ccy), credit, recovery, businessDate.today()));
        return repayment;
    }

    private static zw.insurehub.lendhub.loans.LoanEvents.Breakdown breakdownOf(AllocationWaterfall.Result r, CurrencyCode ccy) {
        return new zw.insurehub.lendhub.loans.LoanEvents.Breakdown(
                r.total(zw.insurehub.lendhub.loans.domain.Component.PENALTY),
                r.total(zw.insurehub.lendhub.loans.domain.Component.FEE),
                r.total(zw.insurehub.lendhub.loans.domain.Component.CREDIT_LIFE),
                r.total(zw.insurehub.lendhub.loans.domain.Component.INTEREST),
                r.total(zw.insurehub.lendhub.loans.domain.Component.PRINCIPAL));
    }

    /**
     * LH-41: payroll deduction CSV with header {@code employer,national_id,amount,period}. Each line is matched to the
     * borrower's servicing loan by national ID and posted; unmatched lines are listed for review.
     */
    public PayrollBatch importPayroll(String fileName, InputStream csv) {
        var batch = new PayrollBatch(fileName, CurrentUser.get().username());
        try (var reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (lineNo == 1 && line.toLowerCase().startsWith("employer")) continue;
                if (line.isBlank()) continue;
                batch.add(payrollLine(lineNo, line));
            }
        } catch (IOException e) {
            throw DomainException.rule("unreadable-file", "Could not read the payroll file: " + e.getMessage());
        }
        return payrollBatches.save(batch);
    }

    private PayrollBatch.Line payrollLine(int lineNo, String raw) {
        String[] f = raw.split(",", -1);
        if (f.length < 4) {
            return new PayrollBatch.Line(lineNo, null, null, BigDecimal.ZERO, null, PayrollBatch.Line.Status.REJECTED, null,
                    "Expected 4 columns: employer,national_id,amount,period");
        }
        String employer = f[0].trim();
        String period = f[3].trim();
        BigDecimal amount;
        String masked;
        try {
            amount = new BigDecimal(f[2].trim());
            masked = KycRules.mask(KycRules.normaliseNationalId(f[1].trim()));
        } catch (RuntimeException e) {
            return new PayrollBatch.Line(lineNo, employer, null, BigDecimal.ZERO, period, PayrollBatch.Line.Status.REJECTED,
                    null, "Invalid national ID or amount");
        }
        var borrower = borrowers.findByNationalId(f[1].trim());
        var loan = borrower.flatMap(b -> loans.activeLoanOfBorrower(b.getId()));
        if (loan.isEmpty()) {
            return new PayrollBatch.Line(lineNo, employer, masked, amount, period, PayrollBatch.Line.Status.UNMATCHED, null,
                    borrower.isEmpty() ? "No borrower with this national ID" : "Borrower has no active loan");
        }
        String reference = "PAYROLL-" + employer.replaceAll("[^A-Za-z0-9]", "").toUpperCase() + "-" + period + "-"
                + loan.get().getLoanNumber();
        var receipt = receive(new ReceivePayment(loan.get().getLoanNumber(), Money.of(amount, loan.get().getCurrency()),
                Repayment.Channel.PAYROLL, reference));
        return new PayrollBatch.Line(lineNo, employer, masked, amount, period,
                receipt.duplicate() ? PayrollBatch.Line.Status.REJECTED : PayrollBatch.Line.Status.POSTED,
                loan.get().getLoanNumber(), receipt.duplicate() ? "Already posted for this period" : "Posted");
    }

    /** LH-90: starts an EcoCash payment through the Payments hub; idempotent on the Idempotency-Key. */
    public PaymentRequest requestPayment(UUID loanId, Money amount, String msisdn, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw DomainException.rule("idempotency-key-required", "Send an Idempotency-Key header");
        }
        var existing = paymentRequests.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            var r = existing.get();
            if (!r.getLoanId().equals(loanId) || r.getAmount().compareTo(amount.amount()) != 0) {
                throw DomainException.conflict("idempotency-conflict", "This Idempotency-Key was used for a different request");
            }
            return r;
        }
        var loan = loans.get(loanId);
        if (!loan.isServicing()) throw DomainException.rule("not-repayable", "Loan " + loan.getLoanNumber() + " is " + loan.getStatus());
        String phone = msisdn != null ? KycRules.normaliseMsisdn(msisdn) : loan.getMsisdn();
        var request = paymentRequests.save(new PaymentRequest(idempotencyKey, loanId, loan.getLoanNumber(), phone,
                amount.currency(), amount.amount(), CurrentUser.get().username()));
        var started = collector.requestPayment(loan.getLoanNumber(), phone, amount, idempotencyKey);
        request.started(started.paymentId(), started.status());
        return request;
    }

    @Transactional(readOnly = true)
    public List<Repayment> forLoan(UUID loanId) {
        return repayments.findByLoanIdOrderByReceivedAtDesc(loanId);
    }

    @Transactional(readOnly = true)
    public Money totalReceivedSince(UUID loanId, CurrencyCode currency, LocalDate since) {
        return Money.of(repayments.totalReceivedSince(loanId, since), currency);
    }

    @Transactional(readOnly = true)
    public Money totalReceivedBetween(LocalDate from, LocalDate to, CurrencyCode currency) {
        return Money.of(repayments.totalReceivedBetween(from, to, currency), currency);
    }

    @Transactional(readOnly = true)
    public List<PayrollBatch> payrollBatches() {
        return payrollBatches.findAllByOrderByUploadedAtDesc();
    }

    @Transactional(readOnly = true)
    public PayrollBatch payrollBatch(UUID id) {
        return payrollBatches.findById(id).orElseThrow(() -> DomainException.notFound("Payroll batch", id));
    }
}

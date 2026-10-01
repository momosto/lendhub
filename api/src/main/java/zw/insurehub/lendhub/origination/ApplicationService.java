package zw.insurehub.lendhub.origination;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import zw.insurehub.lendhub.borrowers.Borrower;
import zw.insurehub.lendhub.borrowers.BorrowerService;
import zw.insurehub.lendhub.products.ProductCatalog;
import zw.insurehub.lendhub.products.schedule.Frequency;
import zw.insurehub.lendhub.scoring.ScoringService;
import zw.insurehub.lendhub.shared.fx.FxRates;
import zw.insurehub.lendhub.shared.money.Money;
import zw.insurehub.lendhub.shared.security.CurrentUser;
import zw.insurehub.lendhub.shared.security.Roles;
import zw.insurehub.lendhub.shared.time.BusinessDateProvider;
import zw.insurehub.lendhub.shared.web.DomainException;

/** Origination workflow: capture → submit (affordability + scoring) → maker-checker decision → offer → OTP acceptance. */
@Service
@Transactional
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);
    private static final MathContext MC = MathContext.DECIMAL128;
    public static final List<String> DECLINE_REASONS = List.of("AFFORDABILITY", "ADVERSE_BUREAU", "INSUFFICIENT_HISTORY",
            "INCOMPLETE_DOCUMENTS", "RELATED_PARTY_EXPOSURE", "POLICY_EXCEPTION", "OTHER");

    public record CaptureApplication(UUID borrowerId, String productCode, Money amount, int instalments, Frequency frequency,
            String purpose, BigDecimal monthlyIncome, BigDecimal monthlyExpenses, BigDecimal otherDebtRepayments) {
    }

    public record Decision(boolean approve, String reasonCode, String comment) {
    }

    /** The OTP is returned only when demo mode exposes it (in production it goes by SMS only). */
    public record OfferIssued(LoanApplication application, String demoOtp) {
    }

    private final LoanApplicationRepository applications;
    private final ApprovalDecisionRepository decisions;
    private final BorrowerService borrowers;
    private final ProductCatalog catalog;
    private final ScoringService scoring;
    private final BusinessDateProvider businessDate;
    private final FxRates fx;
    private final ApplicationEventPublisher events;
    private final BigDecimal managerLimitUsd;
    private final boolean exposeOtp;
    private final SecureRandom random = new SecureRandom();

    ApplicationService(LoanApplicationRepository applications, ApprovalDecisionRepository decisions, BorrowerService borrowers,
            ProductCatalog catalog, ScoringService scoring, BusinessDateProvider businessDate, FxRates fx,
            ApplicationEventPublisher events, @Value("${lendhub.approval.manager-limit-usd:1000}") BigDecimal managerLimitUsd,
            @Value("${lendhub.demo.expose-otp:true}") boolean exposeOtp) {
        this.applications = applications;
        this.decisions = decisions;
        this.borrowers = borrowers;
        this.catalog = catalog;
        this.scoring = scoring;
        this.businessDate = businessDate;
        this.fx = fx;
        this.events = events;
        this.managerLimitUsd = managerLimitUsd;
        this.exposeOtp = exposeOtp;
    }

    public LoanApplication capture(CaptureApplication cmd) {
        var user = CurrentUser.get();
        Borrower borrower = borrowers.get(cmd.borrowerId());
        var product = catalog.byCode(cmd.productCode());
        catalog.checkLimits(product, cmd.amount(), cmd.instalments(), cmd.frequency());
        UUID groupId = null;
        if (product.isGroupLending()) {
            groupId = borrowers.activeGroupOf(borrower.getId())
                    .orElseThrow(() -> DomainException.rule("group-required", product.getName() + " requires membership of an active solidarity group"))
                    .getId();
        }
        var app = new LoanApplication("APP-%06d".formatted(applications.nextNumber()), borrower.getId(),
                borrower.getCustomerRef(), borrower.getFullName(), borrower.getMsisdn(), borrower.getBranch(),
                product.getCode(), cmd.amount(), cmd.instalments(), cmd.frequency(), cmd.purpose(),
                nz(cmd.monthlyIncome()), nz(cmd.monthlyExpenses()), nz(cmd.otherDebtRepayments()), groupId, user.username());
        return applications.save(app);
    }

    /** LH-10 + LH-11: affordability and scoring run when the officer submits. */
    public LoanApplication submit(UUID id) {
        var app = get(id);
        var product = catalog.byCode(app.getProductCode());
        var schedule = catalog.quote(product, app.amountMoney(), app.getInstalments(), app.getFrequency(), businessDate.today());
        BigDecimal instalment = schedule.first().total().amount();
        BigDecimal monthlyInstalment = app.getFrequency() == Frequency.WEEKLY
                ? instalment.multiply(BigDecimal.valueOf(52), MC).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
                : instalment;
        BigDecimal disposable = app.getMonthlyIncome().subtract(app.getMonthlyExpenses()).subtract(app.getOtherDebtRepayments());
        BigDecimal ratio = disposable.signum() <= 0 ? new BigDecimal("9.99")
                : monthlyInstalment.divide(disposable, 4, RoundingMode.HALF_UP);
        BigDecimal max = product.getMaxInstalmentToDisposableIncome();
        app.submit(monthlyInstalment, disposable, ratio, ratio.compareTo(max) <= 0);

        Borrower borrower = borrowers.find(app.getBorrowerId()).orElseThrow();
        var scored = scoring.score(new ScoringService.ScoringRequest(borrower.getId(), borrower.getNationalId(),
                borrower.getEmploymentType() == Borrower.EmploymentType.SALARIED, borrower.getYearsInEmployment(),
                app.getGroupId() != null, ratio, max));
        boolean related = relatedPartyExposure(app);
        var reasons = new java.util.ArrayList<>(scored.result().reasons());
        if (related) {
            reasons.add("Flag: another application with the same phone or borrower in the last 30 days — refer to committee");
        }
        app.scored(scored.result().points(), scored.result().grade().name(), String.join("\n", reasons),
                scored.bureau().status().name(), scored.bureau().reference(), related);
        return app;
    }

    /** LH-12: maker ≠ checker, managers up to the limit in their own branch, committee above (and for grade E). */
    public OfferIssued decide(UUID id, Decision decision) {
        var user = CurrentUser.get();
        var app = get(id);
        BigDecimal amountUsd = fx.toUsd(app.amountMoney());
        String role;
        BigDecimal limit;
        if (user.has(Roles.COMMITTEE)) {
            role = Roles.COMMITTEE;
            limit = null;
        } else if (user.has(Roles.MANAGER)) {
            role = Roles.MANAGER;
            limit = managerLimitUsd;
            if (!app.getBranch().equals(user.branch())) {
                throw DomainException.forbidden("other-branch", "Managers can only decide on their own branch's applications");
            }
            if (decision.approve() && amountUsd.compareTo(managerLimitUsd) > 0) {
                throw DomainException.forbidden("approval-limit", "US$" + amountUsd + " is above the branch manager limit of US$"
                        + managerLimitUsd + "; refer to the credit committee");
            }
            if (decision.approve() && ("E".equals(app.getGrade()) || app.isRelatedPartyFlag())) {
                throw DomainException.forbidden("committee-only", "Grade E and related-party applications need the credit committee");
            }
        } else {
            throw DomainException.forbidden("not-an-approver", "Only managers and the credit committee can decide");
        }

        if (decision.approve()) {
            app.approve(user.username());
            decisions.save(new ApprovalDecision(app.getId(), "APPROVE", null, decision.comment(), user.username(), role, limit, amountUsd));
            return issueOffer(app);
        }
        if (decision.reasonCode() == null || !DECLINE_REASONS.contains(decision.reasonCode())) {
            throw DomainException.rule("reason-required", "A decline needs one of the reason codes " + DECLINE_REASONS);
        }
        app.decline(user.username(), decision.reasonCode());
        decisions.save(new ApprovalDecision(app.getId(), "DECLINE", decision.reasonCode(), decision.comment(), user.username(), role, limit, amountUsd));
        return new OfferIssued(app, null);
    }

    private OfferIssued issueOffer(LoanApplication app) {
        var product = catalog.byCode(app.getProductCode());
        var today = businessDate.today();
        var s = catalog.schedule(product, app.amountMoney(), app.getInstalments(), app.getFrequency(), today);
        String otp = newOtp();
        app.offer(s.first().total().amount(), s.totalInterest().amount(), s.establishmentFee().amount(),
                s.totalCreditLife().amount(), s.totalRepayable().amount(), s.netDisbursed().amount(),
                s.effectiveAnnualRatePercent(), today.plusDays(7), hash(otp));
        log.info("SMS to {}: InsureHub Microfinance offer {} — your acceptance code is sent separately (simulated SMS gateway)",
                app.getMsisdn(), app.getApplicationNumber());
        return new OfferIssued(app, exposeOtp ? otp : null);
    }

    public OfferIssued reissueOtp(UUID id) {
        var app = get(id);
        String otp = newOtp();
        app.reissueOtp(hash(otp));
        return new OfferIssued(app, exposeOtp ? otp : null);
    }

    /** LH-13: the borrower accepts with the OTP sent by SMS. A wrong OTP still counts as an attempt. */
    @Transactional(noRollbackFor = DomainException.class)
    public LoanApplication accept(UUID id, String otp) {
        var app = get(id);
        if (!app.accept(hash(otp), businessDate.today())) {
            throw DomainException.rule("wrong-otp", "The acceptance code is wrong");
        }
        events.publishEvent(new OfferAccepted(app.getId(), app.getApplicationNumber(), app.getBorrowerId(), app.getCustomerRef(),
                app.getBorrowerName(), app.getMsisdn(), app.getBranch(), app.getProductCode(), app.getCurrency(), app.getAmount(),
                app.getInstalments(), app.getFrequency(), app.getCapturedBy(), app.getDecidedBy()));
        return app;
    }

    /** Called by the end-of-day batch: offers are valid for 7 days. */
    public int expireOffers() {
        var expired = applications.findByStatusAndOfferExpiresOnBefore(LoanApplication.Status.OFFERED, businessDate.today());
        expired.forEach(LoanApplication::expire);
        return expired.size();
    }

    @Transactional(readOnly = true)
    public LoanApplication get(UUID id) {
        var app = applications.findById(id).orElseThrow(() -> DomainException.notFound("Application", id));
        var user = CurrentUser.get();
        if (!user.seesAllBranches() && !app.getBranch().equals(user.branch())) {
            throw DomainException.notFound("Application", id);
        }
        return app;
    }

    @Transactional(readOnly = true)
    public List<LoanApplication> list() {
        var user = CurrentUser.get();
        return user.seesAllBranches() ? applications.findAllByOrderByCreatedAtDesc()
                : applications.findByBranchOrderByCreatedAtDesc(user.branch());
    }

    @Transactional(readOnly = true)
    public List<ApprovalDecision> decisions(UUID applicationId) {
        return decisions.findByApplicationIdOrderByDecidedAt(applicationId);
    }

    /** Insider-fraud check: same phone or borrower on another live application within 30 days. */
    private boolean relatedPartyExposure(LoanApplication app) {
        var samePhone = borrowers.findByMsisdn(app.getMsisdn()).stream().map(Borrower::getId).toList();
        var ids = new java.util.HashSet<>(samePhone);
        ids.add(app.getBorrowerId());
        return applications.countRelated(app.getId(), app.getMsisdn(), ids, Instant.now().minus(30, ChronoUnit.DAYS)) > 0
                || samePhone.size() > 1;
    }

    private String newOtp() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    static String hash(String otp) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(otp.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

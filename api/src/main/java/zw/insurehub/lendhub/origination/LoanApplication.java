package zw.insurehub.lendhub.origination;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import zw.insurehub.lendhub.products.schedule.Frequency;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;
import zw.insurehub.lendhub.shared.web.DomainException;

/**
 * A loan application from capture to accepted offer. Status changes are methods with guards, so illegal
 * transitions cannot happen (docs/03-architecture.md §4).
 */
@Entity
@Table(name = "loan_application", schema = "origination")
@Audited
public class LoanApplication {

    public enum Status { DRAFT, SUBMITTED, SCORED, APPROVED, DECLINED, OFFERED, ACCEPTED, EXPIRED }

    @Id
    private UUID id;
    @Column(unique = true, nullable = false)
    private String applicationNumber;
    private UUID borrowerId;
    private String customerRef;
    private String borrowerName;
    private String msisdn;
    private String branch;
    private String productCode;
    @Enumerated(EnumType.STRING)
    private CurrencyCode currency;
    private BigDecimal amount;
    private int instalments;
    @Enumerated(EnumType.STRING)
    private Frequency frequency;
    private String purpose;
    private BigDecimal monthlyIncome;
    private BigDecimal monthlyExpenses;
    private BigDecimal otherDebtRepayments;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String capturedBy;
    private Instant createdAt;
    private Instant submittedAt;

    // affordability (LH-10)
    private BigDecimal monthlyInstalmentEstimate;
    private BigDecimal disposableIncome;
    private BigDecimal affordabilityRatio;
    private boolean affordable;

    // scoring (LH-11)
    private Integer scorePoints;
    private String grade;
    @Column(length = 4000)
    private String scoreReasons;
    private String bureauStatus;
    private String bureauReference;
    private boolean relatedPartyFlag;
    private UUID groupId;

    // decision (LH-12)
    private String decidedBy;
    private Instant decidedAt;
    private String declineReason;

    // offer (LH-13)
    private BigDecimal offerInstalment;
    private BigDecimal offerTotalInterest;
    private BigDecimal offerEstablishmentFee;
    private BigDecimal offerTotalCreditLife;
    private BigDecimal offerTotalRepayable;
    private BigDecimal offerNetDisbursed;
    private BigDecimal offerEffectiveAnnualRate;
    private LocalDate offerExpiresOn;
    private String otpHash;
    private int otpAttempts;
    private Instant acceptedAt;

    @Version
    private Long version;

    protected LoanApplication() {
    }

    LoanApplication(String applicationNumber, UUID borrowerId, String customerRef, String borrowerName, String msisdn,
            String branch, String productCode, Money amount, int instalments, Frequency frequency, String purpose,
            BigDecimal monthlyIncome, BigDecimal monthlyExpenses, BigDecimal otherDebtRepayments, UUID groupId,
            String capturedBy) {
        this.id = UUID.randomUUID();
        this.applicationNumber = applicationNumber;
        this.borrowerId = borrowerId;
        this.customerRef = customerRef;
        this.borrowerName = borrowerName;
        this.msisdn = msisdn;
        this.branch = branch;
        this.productCode = productCode;
        this.currency = amount.currency();
        this.amount = amount.amount();
        this.instalments = instalments;
        this.frequency = frequency;
        this.purpose = purpose;
        this.monthlyIncome = monthlyIncome;
        this.monthlyExpenses = monthlyExpenses;
        this.otherDebtRepayments = otherDebtRepayments;
        this.groupId = groupId;
        this.capturedBy = capturedBy;
        this.status = Status.DRAFT;
        this.createdAt = Instant.now();
    }

    void submit(BigDecimal monthlyInstalment, BigDecimal disposable, BigDecimal ratio, boolean affordable) {
        require(Status.DRAFT);
        this.monthlyInstalmentEstimate = monthlyInstalment;
        this.disposableIncome = disposable;
        this.affordabilityRatio = ratio;
        this.affordable = affordable;
        this.submittedAt = Instant.now();
        this.status = Status.SUBMITTED;
    }

    void scored(int points, String grade, String reasons, String bureauStatus, String bureauReference, boolean relatedParty) {
        require(Status.SUBMITTED);
        this.scorePoints = points;
        this.grade = grade;
        this.scoreReasons = reasons;
        this.bureauStatus = bureauStatus;
        this.bureauReference = bureauReference;
        this.relatedPartyFlag = relatedParty;
        this.status = Status.SCORED;
    }

    void approve(String approver) {
        require(Status.SCORED);
        checkMakerChecker(approver);
        this.decidedBy = approver;
        this.decidedAt = Instant.now();
        this.status = Status.APPROVED;
    }

    void decline(String approver, String reasonCode) {
        require(Status.SCORED);
        checkMakerChecker(approver);
        this.decidedBy = approver;
        this.decidedAt = Instant.now();
        this.declineReason = reasonCode;
        this.status = Status.DECLINED;
    }

    void offer(BigDecimal instalment, BigDecimal totalInterest, BigDecimal fee, BigDecimal creditLife, BigDecimal totalRepayable,
            BigDecimal net, BigDecimal eir, LocalDate expiresOn, String otpHash) {
        require(Status.APPROVED);
        this.offerInstalment = instalment;
        this.offerTotalInterest = totalInterest;
        this.offerEstablishmentFee = fee;
        this.offerTotalCreditLife = creditLife;
        this.offerTotalRepayable = totalRepayable;
        this.offerNetDisbursed = net;
        this.offerEffectiveAnnualRate = eir;
        this.offerExpiresOn = expiresOn;
        this.otpHash = otpHash;
        this.otpAttempts = 0;
        this.status = Status.OFFERED;
    }

    /** Returns true when the OTP matched. Three wrong attempts invalidate the offer's OTP. */
    boolean accept(String otpHashAttempt, LocalDate today) {
        require(Status.OFFERED);
        if (today.isAfter(offerExpiresOn)) {
            throw DomainException.rule("offer-expired", "This offer expired on " + offerExpiresOn);
        }
        if (otpAttempts >= 3) {
            throw DomainException.rule("otp-locked", "Too many wrong OTP attempts; ask the branch for a new offer");
        }
        if (!java.security.MessageDigest.isEqual(otpHash.getBytes(), otpHashAttempt.getBytes())) {
            otpAttempts++;
            return false;
        }
        this.acceptedAt = Instant.now();
        this.status = Status.ACCEPTED;
        return true;
    }

    void expire() {
        require(Status.OFFERED);
        this.status = Status.EXPIRED;
    }

    void reissueOtp(String otpHash) {
        require(Status.OFFERED);
        this.otpHash = otpHash;
        this.otpAttempts = 0;
    }

    private void checkMakerChecker(String approver) {
        if (approver.equalsIgnoreCase(capturedBy)) {
            throw DomainException.forbidden("maker-checker", "The person who captured an application cannot decide on it");
        }
    }

    private void require(Status expected) {
        if (status != expected) {
            throw DomainException.rule("invalid-status", "Application " + applicationNumber + " is " + status
                    + ", expected " + expected);
        }
    }

    public Money amountMoney() {
        return Money.of(amount, currency);
    }

    public UUID getId() { return id; }
    public String getApplicationNumber() { return applicationNumber; }
    public UUID getBorrowerId() { return borrowerId; }
    public String getCustomerRef() { return customerRef; }
    public String getBorrowerName() { return borrowerName; }
    public String getMsisdn() { return msisdn; }
    public String getBranch() { return branch; }
    public String getProductCode() { return productCode; }
    public CurrencyCode getCurrency() { return currency; }
    public BigDecimal getAmount() { return amount; }
    public int getInstalments() { return instalments; }
    public Frequency getFrequency() { return frequency; }
    public String getPurpose() { return purpose; }
    public BigDecimal getMonthlyIncome() { return monthlyIncome; }
    public BigDecimal getMonthlyExpenses() { return monthlyExpenses; }
    public BigDecimal getOtherDebtRepayments() { return otherDebtRepayments; }
    public Status getStatus() { return status; }
    public String getCapturedBy() { return capturedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public BigDecimal getMonthlyInstalmentEstimate() { return monthlyInstalmentEstimate; }
    public BigDecimal getDisposableIncome() { return disposableIncome; }
    public BigDecimal getAffordabilityRatio() { return affordabilityRatio; }
    public boolean isAffordable() { return affordable; }
    public Integer getScorePoints() { return scorePoints; }
    public String getGrade() { return grade; }
    public String getScoreReasons() { return scoreReasons; }
    public String getBureauStatus() { return bureauStatus; }
    public String getBureauReference() { return bureauReference; }
    public boolean isRelatedPartyFlag() { return relatedPartyFlag; }
    public UUID getGroupId() { return groupId; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDeclineReason() { return declineReason; }
    public BigDecimal getOfferInstalment() { return offerInstalment; }
    public BigDecimal getOfferTotalInterest() { return offerTotalInterest; }
    public BigDecimal getOfferEstablishmentFee() { return offerEstablishmentFee; }
    public BigDecimal getOfferTotalCreditLife() { return offerTotalCreditLife; }
    public BigDecimal getOfferTotalRepayable() { return offerTotalRepayable; }
    public BigDecimal getOfferNetDisbursed() { return offerNetDisbursed; }
    public BigDecimal getOfferEffectiveAnnualRate() { return offerEffectiveAnnualRate; }
    public LocalDate getOfferExpiresOn() { return offerExpiresOn; }
    public Instant getAcceptedAt() { return acceptedAt; }
}

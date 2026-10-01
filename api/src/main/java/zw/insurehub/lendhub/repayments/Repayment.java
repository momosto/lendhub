package zw.insurehub.lendhub.repayments;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import zw.insurehub.lendhub.loans.domain.Component;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;

/** Money received for a loan. Unique on the provider reference, so a replayed callback cannot post twice (LH-40). */
@Entity
@Table(name = "repayment", schema = "repayments")
public class Repayment {

    public enum Channel { ECOCASH, ONEMONEY, PAYROLL, CASH, BANK }

    public enum Type { REPAYMENT, SETTLEMENT, RECOVERY }

    public enum Status { POSTED, REVERSED }

    @Id
    private UUID id;
    private UUID loanId;
    private String loanNumber;
    @Column(unique = true, nullable = false)
    private String providerReference;
    @Enumerated(EnumType.STRING)
    private Channel channel;
    @Enumerated(EnumType.STRING)
    private Type type;
    @Enumerated(EnumType.STRING)
    private CurrencyCode currency;
    private BigDecimal amount;
    private BigDecimal creditAdded;
    private LocalDate valueDate;
    private Instant receivedAt;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String postedBy;
    private String reversalReason;
    private String reversedBy;
    private Instant reversedAt;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "repayment_id")
    private List<Allocation> allocations = new ArrayList<>();

    @Entity(name = "RepaymentAllocation")
    @Table(name = "allocation", schema = "repayments")
    public static class Allocation {
        @Id
        private UUID id;
        private int instalmentSeq;
        @Enumerated(EnumType.STRING)
        private Component component;
        private BigDecimal amount;

        protected Allocation() {
        }

        Allocation(int seq, Component component, BigDecimal amount) {
            this.id = UUID.randomUUID();
            this.instalmentSeq = seq;
            this.component = component;
            this.amount = amount;
        }

        public int getInstalmentSeq() { return instalmentSeq; }
        public Component getComponent() { return component; }
        public BigDecimal getAmount() { return amount; }
    }

    protected Repayment() {
    }

    Repayment(UUID loanId, String loanNumber, String providerReference, Channel channel, Type type, Money amount,
            LocalDate valueDate, String postedBy) {
        this.id = UUID.randomUUID();
        this.loanId = loanId;
        this.loanNumber = loanNumber;
        this.providerReference = providerReference;
        this.channel = channel;
        this.type = type;
        this.currency = amount.currency();
        this.amount = amount.amount();
        this.creditAdded = BigDecimal.ZERO.setScale(2);
        this.valueDate = valueDate;
        this.receivedAt = Instant.now();
        this.status = Status.POSTED;
        this.postedBy = postedBy;
    }

    void allocated(List<Allocation> lines, Money creditAdded, boolean recovery) {
        allocations.clear();
        allocations.addAll(lines);
        this.creditAdded = creditAdded.amount();
        if (recovery) this.type = Type.RECOVERY;
    }

    void reversed(String reason, String user) {
        if (status == Status.REVERSED) {
            throw zw.insurehub.lendhub.shared.web.DomainException.rule("already-reversed", "This repayment was already reversed");
        }
        this.status = Status.REVERSED;
        this.reversalReason = reason;
        this.reversedBy = user;
        this.reversedAt = Instant.now();
    }

    public Money amountMoney() { return Money.of(amount, currency); }
    public UUID getId() { return id; }
    public UUID getLoanId() { return loanId; }
    public String getLoanNumber() { return loanNumber; }
    public String getProviderReference() { return providerReference; }
    public Channel getChannel() { return channel; }
    public Type getType() { return type; }
    public CurrencyCode getCurrency() { return currency; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getCreditAdded() { return creditAdded; }
    public LocalDate getValueDate() { return valueDate; }
    public Instant getReceivedAt() { return receivedAt; }
    public Status getStatus() { return status; }
    public String getPostedBy() { return postedBy; }
    public String getReversalReason() { return reversalReason; }
    public List<Allocation> getAllocations() { return List.copyOf(allocations); }
}

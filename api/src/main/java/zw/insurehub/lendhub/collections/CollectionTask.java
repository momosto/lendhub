package zw.insurehub.lendhub.collections;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import zw.insurehub.lendhub.shared.money.CurrencyCode;

/** A collections action on a loan in arrears (LH-60). */
@Entity
@Table(name = "collection_task", schema = "collections")
public class CollectionTask {

    public enum Type {
        CALL(7), VISIT(30), DEMAND_LETTER(60), LEGAL_REVIEW(90), FOLLOW_UP(0);

        final int dpdThreshold;

        Type(int dpdThreshold) {
            this.dpdThreshold = dpdThreshold;
        }
    }

    public enum Status { OPEN, DONE, CANCELLED }

    @Id
    private UUID id;
    private UUID loanId;
    private String loanNumber;
    private String customerRef;
    private String borrowerName;
    @Enumerated(EnumType.STRING)
    private Type type;
    private int dpdAtCreation;
    @Enumerated(EnumType.STRING)
    private CurrencyCode currency;
    private BigDecimal arrearsAmount;
    private BigDecimal priority;
    @Enumerated(EnumType.STRING)
    private Status status;
    private LocalDate createdOn;
    private String notes;
    private String completedBy;
    private Instant completedAt;

    protected CollectionTask() {
    }

    CollectionTask(UUID loanId, String loanNumber, String customerRef, String borrowerName, Type type, int dpd,
            CurrencyCode currency, BigDecimal arrearsAmount, LocalDate createdOn, String notes) {
        this.id = UUID.randomUUID();
        this.loanId = loanId;
        this.loanNumber = loanNumber;
        this.customerRef = customerRef;
        this.borrowerName = borrowerName;
        this.type = type;
        this.dpdAtCreation = dpd;
        this.currency = currency;
        this.arrearsAmount = arrearsAmount;
        // amount × DPD: the biggest, oldest arrears are worked first
        this.priority = arrearsAmount.multiply(BigDecimal.valueOf(Math.max(dpd, 1)));
        this.status = Status.OPEN;
        this.createdOn = createdOn;
        this.notes = notes;
    }

    void complete(String user, String note) {
        if (status != Status.OPEN) {
            throw zw.insurehub.lendhub.shared.web.DomainException.rule("task-closed", "Task is already " + status);
        }
        this.status = Status.DONE;
        this.completedBy = user;
        this.completedAt = Instant.now();
        if (note != null && !note.isBlank()) this.notes = (notes == null ? "" : notes + "\n") + note;
    }

    void cancel(String reason) {
        this.status = Status.CANCELLED;
        this.notes = (notes == null ? "" : notes + "\n") + reason;
        this.completedAt = Instant.now();
        this.completedBy = "system";
    }

    public UUID getId() { return id; }
    public UUID getLoanId() { return loanId; }
    public String getLoanNumber() { return loanNumber; }
    public String getCustomerRef() { return customerRef; }
    public String getBorrowerName() { return borrowerName; }
    public Type getType() { return type; }
    public int getDpdAtCreation() { return dpdAtCreation; }
    public CurrencyCode getCurrency() { return currency; }
    public BigDecimal getArrearsAmount() { return arrearsAmount; }
    public BigDecimal getPriority() { return priority; }
    public Status getStatus() { return status; }
    public LocalDate getCreatedOn() { return createdOn; }
    public String getNotes() { return notes; }
    public String getCompletedBy() { return completedBy; }
    public Instant getCompletedAt() { return completedAt; }
}

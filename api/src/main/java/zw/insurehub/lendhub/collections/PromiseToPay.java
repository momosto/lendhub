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

/** A borrower's promise to pay an amount by a date (LH-61). Checked by the end-of-day batch. */
@Entity
@Table(name = "promise_to_pay", schema = "collections")
public class PromiseToPay {

    public enum Status { OPEN, KEPT, BROKEN }

    @Id
    private UUID id;
    private UUID loanId;
    private String loanNumber;
    private UUID taskId;
    private LocalDate promisedOn;
    private LocalDate promisedDate;
    @Enumerated(EnumType.STRING)
    private CurrencyCode currency;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String createdBy;
    private Instant createdAt;

    protected PromiseToPay() {
    }

    PromiseToPay(UUID loanId, String loanNumber, UUID taskId, LocalDate promisedOn, LocalDate promisedDate,
            CurrencyCode currency, BigDecimal amount, String createdBy) {
        this.id = UUID.randomUUID();
        this.loanId = loanId;
        this.loanNumber = loanNumber;
        this.taskId = taskId;
        this.promisedOn = promisedOn;
        this.promisedDate = promisedDate;
        this.currency = currency;
        this.amount = amount;
        this.status = Status.OPEN;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    void resolve(boolean kept) {
        this.status = kept ? Status.KEPT : Status.BROKEN;
    }

    public UUID getId() { return id; }
    public UUID getLoanId() { return loanId; }
    public String getLoanNumber() { return loanNumber; }
    public UUID getTaskId() { return taskId; }
    public LocalDate getPromisedOn() { return promisedOn; }
    public LocalDate getPromisedDate() { return promisedDate; }
    public CurrencyCode getCurrency() { return currency; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
}

package zw.insurehub.lendhub.repayments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import zw.insurehub.lendhub.shared.money.CurrencyCode;

/** A repayment request started from a channel (EcoCash push via the Payments hub), idempotent on its key (LH-90). */
@Entity
@Table(name = "payment_request", schema = "repayments")
public class PaymentRequest {

    @Id
    private UUID id;
    @Column(unique = true, nullable = false)
    private String idempotencyKey;
    private UUID loanId;
    private String loanNumber;
    private String msisdn;
    @Enumerated(EnumType.STRING)
    private CurrencyCode currency;
    private BigDecimal amount;
    private String paymentId;
    private String status;
    private String requestedBy;
    private Instant requestedAt;

    protected PaymentRequest() {
    }

    PaymentRequest(String idempotencyKey, UUID loanId, String loanNumber, String msisdn, CurrencyCode currency, BigDecimal amount,
            String requestedBy) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.loanId = loanId;
        this.loanNumber = loanNumber;
        this.msisdn = msisdn;
        this.currency = currency;
        this.amount = amount;
        this.requestedBy = requestedBy;
        this.requestedAt = Instant.now();
        this.status = "PENDING";
    }

    void started(String paymentId, String status) {
        this.paymentId = paymentId;
        this.status = status;
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getLoanId() { return loanId; }
    public String getLoanNumber() { return loanNumber; }
    public String getMsisdn() { return msisdn; }
    public CurrencyCode getCurrency() { return currency; }
    public BigDecimal getAmount() { return amount; }
    public String getPaymentId() { return paymentId; }
    public String getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
}

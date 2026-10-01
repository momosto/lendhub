package zw.insurehub.lendhub.origination;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Append-only record of every approval decision: who, when, with which role and limit, and why (non-repudiation). */
@Entity
@Table(name = "approval_decision", schema = "origination")
@Immutable
public class ApprovalDecision {

    @Id
    private UUID id;
    private UUID applicationId;
    private String decision;
    private String reasonCode;
    private String comment;
    private String decidedBy;
    private String role;
    private BigDecimal limitAppliedUsd;
    private BigDecimal amountUsd;
    private Instant decidedAt;

    protected ApprovalDecision() {
    }

    ApprovalDecision(UUID applicationId, String decision, String reasonCode, String comment, String decidedBy, String role,
            BigDecimal limitAppliedUsd, BigDecimal amountUsd) {
        this.id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.decision = decision;
        this.reasonCode = reasonCode;
        this.comment = comment;
        this.decidedBy = decidedBy;
        this.role = role;
        this.limitAppliedUsd = limitAppliedUsd;
        this.amountUsd = amountUsd;
        this.decidedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public String getDecision() { return decision; }
    public String getReasonCode() { return reasonCode; }
    public String getComment() { return comment; }
    public String getDecidedBy() { return decidedBy; }
    public String getRole() { return role; }
    public BigDecimal getLimitAppliedUsd() { return limitAppliedUsd; }
    public BigDecimal getAmountUsd() { return amountUsd; }
    public Instant getDecidedAt() { return decidedAt; }
}

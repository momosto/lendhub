package zw.insurehub.lendhub.loans;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import zw.insurehub.lendhub.shared.web.DomainException;

/** Maker-checker request for a restructure or write-off; the credit committee decides (LH-62, LH-63). */
@Entity
@Table(name = "loan_change_request", schema = "loans")
public class LoanChangeRequest {

    public enum Type { RESTRUCTURE, WRITE_OFF }

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    private UUID id;
    private UUID loanId;
    private String loanNumber;
    @Enumerated(EnumType.STRING)
    private Type type;
    private Integer newInstalments;
    private String reason;
    private String requestedBy;
    private Instant requestedAt;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String decidedBy;
    private Instant decidedAt;

    protected LoanChangeRequest() {
    }

    LoanChangeRequest(LoanAccount loan, Type type, Integer newInstalments, String reason, String requestedBy) {
        this.id = UUID.randomUUID();
        this.loanId = loan.getId();
        this.loanNumber = loan.getLoanNumber();
        this.type = type;
        this.newInstalments = newInstalments;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.requestedAt = Instant.now();
        this.status = Status.PENDING;
    }

    void decide(boolean approve, String user) {
        if (status != Status.PENDING) {
            throw DomainException.rule("already-decided", "This request was already " + status);
        }
        if (user.equalsIgnoreCase(requestedBy)) {
            throw DomainException.forbidden("maker-checker", "The requester cannot approve their own request");
        }
        this.status = approve ? Status.APPROVED : Status.REJECTED;
        this.decidedBy = user;
        this.decidedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getLoanId() { return loanId; }
    public String getLoanNumber() { return loanNumber; }
    public Type getType() { return type; }
    public Integer getNewInstalments() { return newInstalments; }
    public String getReason() { return reason; }
    public String getRequestedBy() { return requestedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public Status getStatus() { return status; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
}

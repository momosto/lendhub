package zw.insurehub.lendhub.borrowers;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/** A solidarity group of 5–10 borrowers with joint liability (LH-02). */
@Entity
@Table(name = "loan_group", schema = "borrowers")
public class LoanGroup {

    @Id
    private UUID id;
    private String name;
    private String branch;
    private UUID chairpersonId;
    private boolean active;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "group_member", schema = "borrowers", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "borrower_id")
    private Set<UUID> memberIds = new HashSet<>();
    private String createdBy;
    private Instant createdAt;

    protected LoanGroup() {
    }

    LoanGroup(String name, String branch, UUID chairpersonId, Set<UUID> memberIds, String createdBy) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.branch = branch;
        this.chairpersonId = chairpersonId;
        this.memberIds = new HashSet<>(memberIds);
        this.active = true;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getBranch() { return branch; }
    public UUID getChairpersonId() { return chairpersonId; }
    public boolean isActive() { return active; }
    public Set<UUID> getMemberIds() { return Set.copyOf(memberIds); }
    public Instant getCreatedAt() { return createdAt; }
}

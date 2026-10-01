package zw.insurehub.lendhub.provisioning;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import zw.insurehub.lendhub.provisioning.domain.Ifrs9;
import zw.insurehub.lendhub.shared.money.CurrencyCode;

@Entity
@Table(name = "provision_run", schema = "provisioning")
public class ProvisionRun {

    @Id
    private UUID id;
    private UUID runGroup;
    private LocalDate asOf;
    @Enumerated(EnumType.STRING)
    private CurrencyCode currency;
    private int loans;
    @jakarta.persistence.Column(name = "stage1_ecl")
    private BigDecimal stage1Ecl;
    @jakarta.persistence.Column(name = "stage2_ecl")
    private BigDecimal stage2Ecl;
    @jakarta.persistence.Column(name = "stage3_ecl")
    private BigDecimal stage3Ecl;
    private BigDecimal totalEcl;
    private BigDecimal previousTotal;
    private BigDecimal movement;
    private String runBy;
    private Instant createdAt;

    protected ProvisionRun() {
    }

    ProvisionRun(UUID runGroup, LocalDate asOf, CurrencyCode currency, int loans, BigDecimal s1, BigDecimal s2, BigDecimal s3,
            BigDecimal previousTotal, String runBy) {
        this.id = UUID.randomUUID();
        this.runGroup = runGroup;
        this.asOf = asOf;
        this.currency = currency;
        this.loans = loans;
        this.stage1Ecl = s1;
        this.stage2Ecl = s2;
        this.stage3Ecl = s3;
        this.totalEcl = s1.add(s2).add(s3);
        this.previousTotal = previousTotal;
        this.movement = totalEcl.subtract(previousTotal);
        this.runBy = runBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRunGroup() { return runGroup; }
    public LocalDate getAsOf() { return asOf; }
    public CurrencyCode getCurrency() { return currency; }
    public int getLoans() { return loans; }
    public BigDecimal getStage1Ecl() { return stage1Ecl; }
    public BigDecimal getStage2Ecl() { return stage2Ecl; }
    public BigDecimal getStage3Ecl() { return stage3Ecl; }
    public BigDecimal getTotalEcl() { return totalEcl; }
    public BigDecimal getPreviousTotal() { return previousTotal; }
    public BigDecimal getMovement() { return movement; }
    public String getRunBy() { return runBy; }
    public Instant getCreatedAt() { return createdAt; }

    @Entity
    @Table(name = "stage_assignment", schema = "provisioning")
    public static class StageAssignment {
        @Id
        private UUID id;
        private UUID runId;
        private UUID loanId;
        private String loanNumber;
        @Enumerated(EnumType.STRING)
        private Ifrs9.Stage stage;
        private int daysPastDue;
        private boolean restructured;
        private BigDecimal ead;
        private BigDecimal pd;
        private BigDecimal lgd;
        private BigDecimal ecl;

        protected StageAssignment() {
        }

        StageAssignment(UUID runId, UUID loanId, String loanNumber, Ifrs9.Stage stage, int dpd, boolean restructured,
                BigDecimal ead, BigDecimal pd, BigDecimal lgd, BigDecimal ecl) {
            this.id = UUID.randomUUID();
            this.runId = runId;
            this.loanId = loanId;
            this.loanNumber = loanNumber;
            this.stage = stage;
            this.daysPastDue = dpd;
            this.restructured = restructured;
            this.ead = ead;
            this.pd = pd;
            this.lgd = lgd;
            this.ecl = ecl;
        }

        public UUID getLoanId() { return loanId; }
        public String getLoanNumber() { return loanNumber; }
        public Ifrs9.Stage getStage() { return stage; }
        public int getDaysPastDue() { return daysPastDue; }
        public boolean isRestructured() { return restructured; }
        public BigDecimal getEad() { return ead; }
        public BigDecimal getPd() { return pd; }
        public BigDecimal getLgd() { return lgd; }
        public BigDecimal getEcl() { return ecl; }
    }
}

package zw.insurehub.lendhub.repayments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** An employer's payroll deduction file (LH-41). Unmatched lines are kept for finance to review. */
@Entity
@Table(name = "payroll_batch", schema = "repayments")
public class PayrollBatch {

    @Id
    private UUID id;
    private String fileName;
    private String uploadedBy;
    private Instant uploadedAt;
    private int matched;
    private int unmatched;
    private BigDecimal totalPosted;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id")
    @OrderBy("lineNo")
    private List<Line> lines = new ArrayList<>();

    @Entity(name = "PayrollLine")
    @Table(name = "payroll_line", schema = "repayments")
    public static class Line {
        public enum Status { POSTED, UNMATCHED, REJECTED }

        @Id
        private UUID id;
        private int lineNo;
        private String employer;
        private String nationalIdMasked;
        private BigDecimal amount;
        private String period;
        @Enumerated(EnumType.STRING)
        private Status status;
        private String loanNumber;
        private String message;

        protected Line() {
        }

        Line(int lineNo, String employer, String nationalIdMasked, BigDecimal amount, String period, Status status,
                String loanNumber, String message) {
            this.id = UUID.randomUUID();
            this.lineNo = lineNo;
            this.employer = employer;
            this.nationalIdMasked = nationalIdMasked;
            this.amount = amount;
            this.period = period;
            this.status = status;
            this.loanNumber = loanNumber;
            this.message = message;
        }

        public int getLineNo() { return lineNo; }
        public String getEmployer() { return employer; }
        public String getNationalIdMasked() { return nationalIdMasked; }
        public BigDecimal getAmount() { return amount; }
        public String getPeriod() { return period; }
        public Status getStatus() { return status; }
        public String getLoanNumber() { return loanNumber; }
        public String getMessage() { return message; }
    }

    protected PayrollBatch() {
    }

    PayrollBatch(String fileName, String uploadedBy) {
        this.id = UUID.randomUUID();
        this.fileName = fileName;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
        this.totalPosted = BigDecimal.ZERO.setScale(2);
    }

    void add(Line line) {
        lines.add(line);
        if (line.getStatus() == Line.Status.POSTED) {
            matched++;
            totalPosted = totalPosted.add(line.getAmount());
        } else {
            unmatched++;
        }
    }

    public UUID getId() { return id; }
    public String getFileName() { return fileName; }
    public String getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
    public int getMatched() { return matched; }
    public int getUnmatched() { return unmatched; }
    public BigDecimal getTotalPosted() { return totalPosted; }
    public List<Line> getLines() { return List.copyOf(lines); }
}

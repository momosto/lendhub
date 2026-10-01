package zw.insurehub.lendhub.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import zw.insurehub.lendhub.shared.money.CurrencyCode;

/** A balanced journal entry. Posted entries are never updated (LH-72); corrections are reversals. */
@Entity
@Table(name = "journal_entry", schema = "ledger")
@Immutable
public class JournalEntry {

    @Id
    private UUID id;
    private String sourceType;
    private String sourceRef;
    private LocalDate businessDate;
    private Instant postedAt;
    @Enumerated(EnumType.STRING)
    private CurrencyCode currency;
    private String loanNumber;
    private String description;
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "entry_id", nullable = false, updatable = false)
    private List<JournalLine> lines = new ArrayList<>();

    @Entity
    @Table(name = "journal_line", schema = "ledger")
    @Immutable
    public static class JournalLine {
        @Id
        private UUID id;
        private String accountCode;
        private BigDecimal debit;
        private BigDecimal credit;
        private Instant postedAt;

        protected JournalLine() {
        }

        JournalLine(String accountCode, BigDecimal debit, BigDecimal credit, Instant postedAt) {
            this.id = UUID.randomUUID();
            this.accountCode = accountCode;
            this.debit = debit;
            this.credit = credit;
            this.postedAt = postedAt;
        }

        public String getAccountCode() { return accountCode; }
        public BigDecimal getDebit() { return debit; }
        public BigDecimal getCredit() { return credit; }
    }

    protected JournalEntry() {
    }

    JournalEntry(String sourceType, String sourceRef, LocalDate businessDate, CurrencyCode currency, String loanNumber,
            String description, List<JournalLine> lines) {
        this.id = UUID.randomUUID();
        this.sourceType = sourceType;
        this.sourceRef = sourceRef;
        this.businessDate = businessDate;
        this.postedAt = Instant.now();
        this.currency = currency;
        this.loanNumber = loanNumber;
        this.description = description;
        this.lines = new ArrayList<>(lines);
        BigDecimal dr = lines.stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cr = lines.stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (dr.compareTo(cr) != 0) {
            throw new IllegalStateException("Unbalanced journal " + sourceType + "/" + sourceRef + ": Dr " + dr + " ≠ Cr " + cr);
        }
    }

    public UUID getId() { return id; }
    public String getSourceType() { return sourceType; }
    public String getSourceRef() { return sourceRef; }
    public LocalDate getBusinessDate() { return businessDate; }
    public Instant getPostedAt() { return postedAt; }
    public CurrencyCode getCurrency() { return currency; }
    public String getLoanNumber() { return loanNumber; }
    public String getDescription() { return description; }
    public List<JournalLine> getLines() { return List.copyOf(lines); }
}

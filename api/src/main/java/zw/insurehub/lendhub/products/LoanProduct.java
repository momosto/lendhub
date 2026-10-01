package zw.insurehub.lendhub.products;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import zw.insurehub.lendhub.products.schedule.Frequency;
import zw.insurehub.lendhub.products.schedule.RepaymentMethod;

/** A loan product and its pricing parameters (docs/02-requirements.md §2). Limits are in USD. */
@Entity
@Table(name = "loan_product", schema = "products")
public class LoanProduct {

    @Id
    private UUID id;
    @Column(unique = true, nullable = false)
    private String code;
    private String name;
    @Enumerated(EnumType.STRING)
    private RepaymentMethod method;
    /** Comma-separated {@link Frequency} names. */
    private String frequencies;
    private BigDecimal monthlyInterestRate;
    private BigDecimal establishmentFeeRate;
    private BigDecimal creditLifeMonthlyRate;
    private BigDecimal penaltyMonthlyRate;
    private BigDecimal settlementFeeRate;
    private int graceDays;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private int minTermMonths;
    private int maxTermMonths;
    private boolean groupLending;
    private boolean inDuplumEnabled;
    private BigDecimal maxInstalmentToDisposableIncome;

    protected LoanProduct() {
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public RepaymentMethod getMethod() { return method; }
    public BigDecimal getMonthlyInterestRate() { return monthlyInterestRate; }
    public BigDecimal getEstablishmentFeeRate() { return establishmentFeeRate; }
    public BigDecimal getCreditLifeMonthlyRate() { return creditLifeMonthlyRate; }
    public BigDecimal getPenaltyMonthlyRate() { return penaltyMonthlyRate; }
    public BigDecimal getSettlementFeeRate() { return settlementFeeRate; }
    public int getGraceDays() { return graceDays; }
    public BigDecimal getMinAmount() { return minAmount; }
    public BigDecimal getMaxAmount() { return maxAmount; }
    public int getMinTermMonths() { return minTermMonths; }
    public int getMaxTermMonths() { return maxTermMonths; }
    public boolean isGroupLending() { return groupLending; }
    public boolean isInDuplumEnabled() { return inDuplumEnabled; }
    public BigDecimal getMaxInstalmentToDisposableIncome() { return maxInstalmentToDisposableIncome; }

    public Set<Frequency> getFrequencies() {
        return Arrays.stream(frequencies.split(",")).map(String::trim).map(Frequency::valueOf).collect(Collectors.toSet());
    }
}

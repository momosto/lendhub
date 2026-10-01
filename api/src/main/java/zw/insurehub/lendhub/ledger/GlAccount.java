package zw.insurehub.lendhub.ledger;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Chart of accounts (seeded by Flyway). */
@Entity
@Table(name = "gl_account", schema = "ledger")
public class GlAccount {

    public enum Type { ASSET, LIABILITY, EQUITY, INCOME, EXPENSE }

    public static final String CASH = "1000";
    public static final String LOANS_PRINCIPAL = "1100";
    public static final String INTEREST_RECEIVABLE = "1110";
    public static final String PENALTY_RECEIVABLE = "1120";
    public static final String LOAN_LOSS_PROVISION = "1190";
    public static final String CREDIT_LIFE_PAYABLE = "2100";
    public static final String CUSTOMER_CREDIT = "2200";
    public static final String INTEREST_INCOME = "4000";
    public static final String FEE_INCOME = "4100";
    public static final String PENALTY_INCOME = "4200";
    public static final String RECOVERIES = "4300";
    public static final String IMPAIRMENT_EXPENSE = "5000";

    @Id
    private String code;
    private String name;
    @Enumerated(EnumType.STRING)
    private Type type;

    protected GlAccount() {
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public Type getType() { return type; }
}

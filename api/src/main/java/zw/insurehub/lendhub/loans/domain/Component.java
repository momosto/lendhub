package zw.insurehub.lendhub.loans.domain;

import java.util.List;

/** Parts of an instalment. The declaration order is the default allocation order (LH-42). */
public enum Component {
    PENALTY, FEE, CREDIT_LIFE, INTEREST, PRINCIPAL;

    public static final List<Component> DEFAULT_ORDER = List.of(values());
}

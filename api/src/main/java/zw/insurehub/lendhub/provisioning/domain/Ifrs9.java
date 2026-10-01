package zw.insurehub.lendhub.provisioning.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import zw.insurehub.lendhub.shared.money.Money;

/**
 * IFRS 9 staging and expected credit loss (LH-70, LH-71). Staging uses the standard's rebuttable presumptions:
 * more than 30 days past due → Stage 2 (significant increase in credit risk), more than 90 → Stage 3 (credit-impaired).
 * Restructured loans in probation are at least Stage 2. PD and LGD values are illustrative configuration.
 */
public final class Ifrs9 {

    public enum Stage { STAGE_1, STAGE_2, STAGE_3 }

    public static Stage stage(int daysPastDue, boolean restructured, boolean writtenOff) {
        if (writtenOff || daysPastDue > 90) return Stage.STAGE_3;
        if (daysPastDue > 30 || restructured) return Stage.STAGE_2;
        return Stage.STAGE_1;
    }

    /** ECL = PD × LGD × EAD, rounded HALF_UP to the cent. */
    public static Money ecl(BigDecimal pd, BigDecimal lgd, Money ead) {
        return ead.times(pd.multiply(lgd), RoundingMode.HALF_UP);
    }

    private Ifrs9() {
    }
}

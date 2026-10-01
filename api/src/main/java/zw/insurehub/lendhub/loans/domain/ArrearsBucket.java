package zw.insurehub.lendhub.loans.domain;

/** Days-past-due buckets used for PAR reporting and collections (LH-52). */
public enum ArrearsBucket {
    CURRENT, DPD_1_30, DPD_31_60, DPD_61_90, DPD_90_PLUS;

    public static ArrearsBucket of(int dpd) {
        if (dpd <= 0) return CURRENT;
        if (dpd <= 30) return DPD_1_30;
        if (dpd <= 60) return DPD_31_60;
        if (dpd <= 90) return DPD_61_90;
        return DPD_90_PLUS;
    }
}

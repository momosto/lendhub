package zw.insurehub.lendhub.borrowers;

import java.util.regex.Pattern;

import zw.insurehub.lendhub.shared.web.DomainException;

/** Zimbabwean KYC formats (LH-01). Pure functions, unit tested. */
public final class KycRules {

    /** e.g. 63-123456A78 or 63-1234567A78: district code, serial, check letter, district of origin. */
    private static final Pattern NATIONAL_ID = Pattern.compile("^(\\d{2})-?(\\d{6,7})([A-Z])(\\d{2})$");
    private static final Pattern MOBILE = Pattern.compile("^2637[1378]\\d{7}$");

    /** Upper-cases, strips spaces and inserts the dash: "63 123456 a 78" → "63-123456A78". */
    public static String normaliseNationalId(String raw) {
        if (raw == null) throw DomainException.rule("invalid-national-id", "National ID is required");
        String compact = raw.replaceAll("[\\s-]", "").toUpperCase();
        var m = NATIONAL_ID.matcher(compact);
        if (!m.matches()) {
            throw DomainException.rule("invalid-national-id", "National ID must look like 63-123456A78");
        }
        return m.group(1) + "-" + m.group(2) + m.group(3) + m.group(4);
    }

    /** Normalises local or international formats to 2637XXXXXXXX (Econet 077/078, NetOne 071, Telecel 073). */
    public static String normaliseMsisdn(String raw) {
        if (raw == null) throw DomainException.rule("invalid-msisdn", "Mobile number is required");
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("00263")) digits = digits.substring(2);
        else if (digits.startsWith("0")) digits = "263" + digits.substring(1);
        else if (digits.startsWith("7") && digits.length() == 9) digits = "263" + digits;
        if (!MOBILE.matcher(digits).matches()) {
            throw DomainException.rule("invalid-msisdn", "Mobile number must be a Zimbabwean mobile number, e.g. 0771234567");
        }
        return digits;
    }

    /** Lists show only the last characters: 63-123456A78 → ******6A78. */
    public static String mask(String nationalId) {
        if (nationalId == null || nationalId.length() < 4) return "****";
        return "******" + nationalId.substring(nationalId.length() - 4);
    }

    private KycRules() {
    }
}

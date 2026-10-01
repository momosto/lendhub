package zw.insurehub.lendhub.shared.security;

/** Role names (without the ROLE_ prefix) from docs/04-security-and-compliance.md. */
public final class Roles {

    public static final String OFFICER = "OFFICER";
    public static final String MANAGER = "MANAGER";
    public static final String COMMITTEE = "COMMITTEE";
    public static final String FINANCE = "FINANCE";
    public static final String COLLECTIONS = "COLLECTIONS";
    public static final String AUDITOR = "AUDITOR";
    /** Service role for group channels (InsureAssist MCP server, USSD gateway). */
    public static final String CHANNEL = "CHANNEL";

    private Roles() {
    }
}

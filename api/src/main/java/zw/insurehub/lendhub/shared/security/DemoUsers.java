package zw.insurehub.lendhub.shared.security;

import java.util.List;
import java.util.Optional;

/** Demo staff accounts from the README. The password for all of them is {@link #PASSWORD}. */
public final class DemoUsers {

    public record DemoUser(String username, String displayName, List<String> roles, String branch) {
    }

    public static final String PASSWORD = "Demo123!";

    public static final List<DemoUser> ALL = List.of(
            new DemoUser("officer@lendhub.demo", "Rudo Moyo (Loan officer)", List.of(Roles.OFFICER), "MBARE"),
            new DemoUser("officer2@lendhub.demo", "Tafadzwa Ncube (Loan officer)", List.of(Roles.OFFICER), "MBARE"),
            new DemoUser("manager@lendhub.demo", "Chipo Dube (Branch manager)", List.of(Roles.MANAGER), "MBARE"),
            new DemoUser("committee@lendhub.demo", "Farai Mutasa (Credit committee)", List.of(Roles.COMMITTEE), null),
            new DemoUser("collections@lendhub.demo", "Nyasha Banda (Collections)", List.of(Roles.COLLECTIONS), null),
            new DemoUser("finance@lendhub.demo", "Tendai Shumba (Finance)", List.of(Roles.FINANCE), null),
            new DemoUser("finance2@lendhub.demo", "Kuda Marufu (Finance)", List.of(Roles.FINANCE), null),
            new DemoUser("auditor@lendhub.demo", "Rumbi Chikore (Auditor)", List.of(Roles.AUDITOR), null),
            new DemoUser("channel@lendhub.demo", "Channel service (InsureAssist / USSD)", List.of(Roles.CHANNEL), null));

    public static Optional<DemoUser> find(String username) {
        return ALL.stream().filter(u -> u.username().equalsIgnoreCase(username)).findFirst();
    }

    private DemoUsers() {
    }
}

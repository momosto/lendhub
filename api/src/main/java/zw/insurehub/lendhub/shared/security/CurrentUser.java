package zw.insurehub.lendhub.shared.security;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** The signed-in staff member, read from the JWT. Identity never comes from request bodies. */
public record CurrentUser(String username, Set<String> roles, String branch) {

    public static CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return new CurrentUser("system", Set.of(), null);
        }
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .collect(Collectors.toSet());
        String branch = auth instanceof JwtAuthenticationToken jwt ? jwt.getToken().getClaimAsString("branch") : null;
        return new CurrentUser(auth.getName(), roles, branch);
    }

    public boolean has(String role) {
        return roles.contains(role);
    }

    /** Head-office roles see every branch; officers and managers are scoped to their own branch. */
    public boolean seesAllBranches() {
        return branch == null || has(Roles.FINANCE) || has(Roles.COMMITTEE) || has(Roles.AUDITOR)
                || has(Roles.CHANNEL) || has(Roles.COLLECTIONS);
    }
}

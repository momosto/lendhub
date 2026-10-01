package zw.insurehub.lendhub.shared.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
class AuthController {

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    record LoginResponse(String accessToken, String tokenType, long expiresIn, String username, String displayName,
            List<String> roles, String branch) {
    }

    private final JwtEncoder encoder;
    private final SecurityConfig.SecurityProperties props;
    private final String passwordHash;
    private final PasswordEncoder passwords;

    AuthController(JwtEncoder encoder, SecurityConfig.SecurityProperties props, PasswordEncoder passwords) {
        this.encoder = encoder;
        this.props = props;
        this.passwords = passwords;
        this.passwordHash = passwords.encode(DemoUsers.PASSWORD);
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in as a demo user and receive a bearer token")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var user = DemoUsers.find(request.username())
                .filter(u -> passwords.matches(request.password(), passwordHash))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("lendhub")
                .subject(user.username())
                .issuedAt(now)
                .expiresAt(now.plus(props.tokenTtlMinutes(), ChronoUnit.MINUTES))
                .claim("roles", user.roles())
                .claim("name", user.displayName());
        if (user.branch() != null) {
            claims.claim("branch", user.branch());
        }
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new LoginResponse(token, "Bearer", props.tokenTtlMinutes() * 60, user.username(), user.displayName(),
                user.roles(), user.branch());
    }

    @GetMapping("/demo-users")
    @Operation(summary = "List the demo accounts (demo only)")
    List<Map<String, Object>> demoUsers() {
        return DemoUsers.ALL.stream()
                .map(u -> Map.<String, Object>of("username", u.username(), "displayName", u.displayName(), "roles", u.roles()))
                .toList();
    }
}

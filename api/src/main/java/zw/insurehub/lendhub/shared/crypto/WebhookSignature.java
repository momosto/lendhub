package zw.insurehub.lendhub.shared.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Stripe-style signed webhooks, byte-compatible with InsureHub Integrations:
 * header {@code X-Signature: t=<unix>,v1=<hex(HMAC-SHA256(secret, t + "." + body))>}.
 */
public final class WebhookSignature {

    public static final String HEADER = "X-Signature";

    public static String create(String secret, String body, Instant timestamp) {
        long t = timestamp.getEpochSecond();
        return "t=" + t + ",v1=" + hmac(secret, t + "." + body);
    }

    /** Constant-time check that also rejects stale timestamps (replay protection). */
    public static boolean verify(String secret, String body, String header, Instant now, Duration tolerance) {
        if (header == null || header.isBlank()) return false;
        Map<String, String> parts = new HashMap<>();
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) parts.put(kv[0], kv[1]);
        }
        long unix;
        try {
            unix = Long.parseLong(parts.getOrDefault("t", ""));
        } catch (NumberFormatException e) {
            return false;
        }
        String sig = parts.get("v1");
        if (sig == null) return false;
        if (Duration.between(Instant.ofEpochSecond(unix), now).abs().compareTo(tolerance) > 0) return false;
        byte[] expected = HexFormat.of().parseHex(hmac(secret, unix + "." + body));
        byte[] actual;
        try {
            actual = HexFormat.of().parseHex(sig);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }

    private static String hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private WebhookSignature() {
    }
}

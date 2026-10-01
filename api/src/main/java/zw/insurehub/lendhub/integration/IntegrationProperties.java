package zw.insurehub.lendhub.integration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Endpoints of the other group systems. When a base URL is blank, the simulated adapter is used, so LendHub
 * runs on its own for demos and tests (the same approach as ClaimGuard's offline mode).
 */
@ConfigurationProperties("lendhub.integration")
public record IntegrationProperties(
        String insurehubBaseUrl,
        String insurehubApiKey,
        String paymentsBaseUrl,
        String paymentsApiKey,
        String callbackSecret,
        String callbackUrl,
        Duration signatureTolerance,
        Duration simulatedPaymentDelay,
        boolean amqpEnabled,
        String exchange) {

    public IntegrationProperties {
        if (callbackSecret == null || callbackSecret.isBlank()) callbackSecret = "dev-insurehub-callback-secret";
        if (signatureTolerance == null) signatureTolerance = Duration.ofMinutes(5);
        if (simulatedPaymentDelay == null) simulatedPaymentDelay = Duration.ofSeconds(2);
        if (exchange == null || exchange.isBlank()) exchange = "insurehub.events";
    }

    boolean insurehubConfigured() {
        return insurehubBaseUrl != null && !insurehubBaseUrl.isBlank();
    }

    boolean paymentsConfigured() {
        return paymentsBaseUrl != null && !paymentsBaseUrl.isBlank();
    }
}

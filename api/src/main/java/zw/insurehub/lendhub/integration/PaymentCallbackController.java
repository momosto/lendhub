package zw.insurehub.lendhub.integration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import zw.insurehub.lendhub.repayments.RepaymentService;
import zw.insurehub.lendhub.shared.crypto.WebhookSignature;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;

/**
 * I4: signed callback from the Payments hub when a loan repayment succeeds. Same body as the merchant callback
 * InsureHub receives; {@code policyNumber} carries the LN- loan number.
 */
@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Integrations")
class PaymentCallbackController {

    record MerchantCallback(UUID paymentId, String policyNumber, BigDecimal amount, String currency, String method,
            String providerReference, String paidAt) {
    }

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackController.class);

    private final RepaymentService repayments;
    private final IntegrationProperties props;
    private final ObjectMapper json;

    PaymentCallbackController(RepaymentService repayments, IntegrationProperties props, ObjectMapper json) {
        this.repayments = repayments;
        this.props = props;
        this.json = json;
    }

    @PostMapping("/payments")
    @Operation(summary = "Payments hub callback (HMAC-signed X-Signature; replays are acknowledged and ignored)")
    ResponseEntity<Map<String, Object>> callback(@RequestHeader(value = WebhookSignature.HEADER, required = false) String signature,
            @RequestBody String body) throws Exception {
        if (!WebhookSignature.verify(props.callbackSecret(), body, signature, Instant.now(), props.signatureTolerance())) {
            log.warn("Rejected payment callback with an invalid or stale signature");
            return ResponseEntity.status(401).body(Map.of("error", "invalid signature"));
        }
        var cb = json.readValue(body, MerchantCallback.class);
        if (cb.policyNumber() == null || !cb.policyNumber().startsWith("LN-")) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", "not a LendHub loan reference"));
        }
        var receipt = repayments.receive(new RepaymentService.ReceivePayment(cb.policyNumber(),
                Money.of(cb.amount(), CurrencyCode.valueOf(cb.currency())), PaymentChannels.of(cb.method()), cb.providerReference()));
        return ResponseEntity.ok(Map.of("repaymentId", receipt.repayment().getId(), "duplicate", receipt.duplicate()));
    }
}

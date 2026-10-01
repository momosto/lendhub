package zw.insurehub.lendhub.integration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import zw.insurehub.lendhub.loans.LoanPorts;
import zw.insurehub.lendhub.repayments.PaymentCollector;
import zw.insurehub.lendhub.scoring.CreditBureau;

/** Chooses real HTTP adapters when the other systems are configured, simulated ones otherwise. */
@Configuration
class OutboundAdapters {

    private static final Logger log = LoggerFactory.getLogger(OutboundAdapters.class);

    /** Raised by the simulated Payments hub when the customer "approves" the EcoCash prompt. */
    record SimulatedPaymentApproved(String loanNumber, String paymentId, java.math.BigDecimal amount, String currency) {
    }

    @Bean
    LoanPorts.CreditLifeProvider creditLifeProvider(IntegrationProperties props) {
        if (props.insurehubConfigured()) {
            RestClient client = RestClient.builder().baseUrl(props.insurehubBaseUrl())
                    .defaultHeader("X-Api-Key", props.insurehubApiKey() == null ? "" : props.insurehubApiKey()).build();
            return request -> {
                // I5: idempotent on loanNumber, so a retry after a timeout returns the same policy
                var body = Map.of("loanNumber", request.loanNumber(), "customerRef", request.customerRef(),
                        "insuredName", request.borrowerName(), "sumInsured", request.sumInsured().amount(),
                        "currency", request.sumInsured().currency().name(), "termMonths", request.termMonths(),
                        "startDate", request.startDate().toString());
                var response = client.post().uri("/api/partners/credit-life/policies").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", request.loanNumber()).body(body).retrieve().body(Map.class);
                return String.valueOf(response.get("policyNumber"));
            };
        }
        AtomicLong seq = new AtomicLong(1000);
        return request -> {
            String policy = "CL-" + request.loanNumber().substring(3) + "-" + seq.incrementAndGet();
            log.info("Simulated InsureHub: credit life policy {} issued for {} (sum insured {})", policy,
                    request.loanNumber(), request.sumInsured());
            return policy;
        };
    }

    @Bean
    LoanPorts.DisbursementGateway disbursementGateway() {
        // B2C disbursement is not in the Payments hub yet; simulated until it is (tracked in docs/07-traceability.md)
        return (loanNumber, msisdn, amount) -> {
            String ref = "B2C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.info("Simulated EcoCash B2C: {} sent to {} for {} (ref {})", amount, msisdn, loanNumber, ref);
            return ref;
        };
    }

    @Bean
    PaymentCollector paymentCollector(IntegrationProperties props, ApplicationEventPublisher events) {
        if (props.paymentsConfigured()) {
            RestClient client = RestClient.builder().baseUrl(props.paymentsBaseUrl())
                    .defaultHeader("X-Api-Key", props.paymentsApiKey() == null ? "" : props.paymentsApiKey()).build();
            return (loanNumber, msisdn, amount, key) -> {
                // Payments' "policyNumber" field is a generic merchant reference; LN- prefixes route callbacks to LendHub
                var body = new java.util.HashMap<String, Object>(Map.of("policyNumber", loanNumber, "amount", amount.amount(),
                        "currency", amount.currency().name(), "method", "EcoCash", "msisdn", msisdn));
                if (props.callbackUrl() != null) body.put("callbackUrl", props.callbackUrl());
                var response = client.post().uri("/payments").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key).body(body).retrieve().body(Map.class);
                return new PaymentCollector.Started(String.valueOf(response.get("id")), String.valueOf(response.get("status")));
            };
        }
        return (loanNumber, msisdn, amount, key) -> {
            String paymentId = UUID.nameUUIDFromBytes(key.getBytes()).toString();
            log.info("Simulated Payments hub: EcoCash prompt sent to {} for {} on {}", msisdn, amount, loanNumber);
            events.publishEvent(new SimulatedPaymentApproved(loanNumber, paymentId, amount.amount(), amount.currency().name()));
            return new PaymentCollector.Started(paymentId, "PENDING");
        };
    }

    /**
     * Deterministic simulated bureau: IDs whose serial contains "99" have an adverse listing, serials ending in "00"
     * have a thin file, everyone else is clear. Lets the demo show every scorecard path.
     */
    @Bean
    CreditBureau creditBureau() {
        return nationalId -> {
            String serial = nationalId.replaceAll("[^0-9]", "");
            String ref = "CRB-" + Integer.toHexString(nationalId.hashCode()).toUpperCase();
            if (serial.length() > 4 && serial.substring(2, serial.length() - 2).contains("99")) {
                return new CreditBureau.BureauReport(CreditBureau.Status.ADVERSE, 3, ref);
            }
            if (serial.length() > 4 && serial.substring(2, serial.length() - 2).endsWith("00")) {
                return new CreditBureau.BureauReport(CreditBureau.Status.THIN_FILE, 0, ref);
            }
            return new CreditBureau.BureauReport(CreditBureau.Status.CLEAR, 1, ref);
        };
    }
}

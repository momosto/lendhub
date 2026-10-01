package zw.insurehub.lendhub;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Real PostgreSQL via Testcontainers (no H2, docs/05-test-strategy.md). One container for the whole run; tests use
 * fresh borrowers and work relative to the current business date, so they do not depend on each other.
 */
@SpringBootTest(properties = { "lendhub.integration.simulated-payment-delay=100ms", "logging.level.root=WARN" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;

    protected String token(String user) {
        return TOKENS.computeIfAbsent(user, u -> {
            try {
                var result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "@lendhub.demo\",\"password\":\"Demo123!\"}")).andReturn();
                return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    protected record Response(int status, JsonNode body) {
    }

    protected Response call(MockHttpServletRequestBuilder request, String user) throws Exception {
        if (user != null) request.header("Authorization", "Bearer " + token(user));
        MvcResult r = mvc.perform(request).andReturn();
        String content = r.getResponse().getContentAsString();
        return new Response(r.getResponse().getStatus(), content.isBlank() ? null : json.readTree(content));
    }

    protected Response postJson(String path, String user, Object body) throws Exception {
        return call(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), user);
    }

    protected Response getJson(String path, String user) throws Exception {
        return call(get(path), user);
    }

    protected JsonNode ok(Response r) {
        if (r.status() >= 300) throw new AssertionError("HTTP " + r.status() + ": " + r.body());
        return r.body();
    }

    protected LocalDate businessDate() throws Exception {
        return LocalDate.parse(ok(getJson("/api/v1/eod/business-date", "finance")).get("businessDate").asText());
    }

    /** A national ID whose serial avoids the simulated bureau's adverse ("99") and thin-file ("00") patterns. */
    protected static String randomNationalId() {
        var r = ThreadLocalRandom.current();
        String serial;
        do {
            serial = String.valueOf(100000 + r.nextInt(899999));
        } while (serial.contains("99") || serial.endsWith("00"));
        return "63-" + serial + "A" + (10 + r.nextInt(80));
    }

    protected static String randomMobile() {
        return "077" + (1000000 + ThreadLocalRandom.current().nextInt(8999999));
    }

    protected JsonNode registerBorrower(String firstName, String employmentType) throws Exception {
        return ok(postJson("/api/v1/borrowers", "officer", Map.of("firstName", firstName, "lastName", "Moyo",
                "nationalId", randomNationalId(), "msisdn", randomMobile(), "dateOfBirth", "1988-04-12",
                "address", "Mbare Musika, Harare", "employmentType", employmentType, "employer", "Mbare Musika traders",
                "yearsInEmployment", 4, "bureauConsent", true)));
    }

    /** Officer captures and submits, manager approves, borrower accepts; returns the accepted application. */
    protected JsonNode acceptedApplication(JsonNode borrower, String product, int amount, int instalments, String frequency)
            throws Exception {
        var app = ok(postJson("/api/v1/applications", "officer", Map.of("borrowerId", borrower.get("id").asText(),
                "productCode", product, "amount", amount, "currency", "USD", "instalments", instalments,
                "frequency", frequency, "purpose", "Stock for vegetable stall", "monthlyIncome", 1200,
                "monthlyExpenses", 300, "otherDebtRepayments", 0)));
        String id = app.get("id").asText();
        ok(postJson("/api/v1/applications/" + id + "/submit", "officer", Map.of()));
        var offered = ok(postJson("/api/v1/applications/" + id + "/decisions", "manager", Map.of("approve", true, "comment", "ok")));
        return ok(postJson("/api/v1/applications/" + id + "/offer/accept", "officer", Map.of("otp", offered.get("demoOtp").asText())));
    }

    @FunctionalInterface
    protected interface Check {
        boolean ok() throws Exception;
    }

    /** Polls on the test thread (MockMvc and the security context stay on one thread) for up to 10 seconds. */
    protected static void eventually(Check check) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!check.ok()) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("Condition not met within 10 seconds");
            Thread.sleep(100);
        }
    }

    protected JsonNode loanForApplication(String applicationNumber) throws Exception {
        for (JsonNode loan : ok(getJson("/api/v1/loans", "finance"))) {
            if (applicationNumber.equals(loan.get("applicationNumber").asText())) return loan;
        }
        return null;
    }

    /** Waits for the asynchronous booking + credit life listener, then disburses as finance. */
    protected JsonNode activeLoan(String product, int amount, int instalments, String frequency) throws Exception {
        var borrower = registerBorrower("Chipo", "SELF_EMPLOYED");
        var app = acceptedApplication(borrower, product, amount, instalments, frequency);
        String appNumber = app.get("applicationNumber").asText();
        eventually(() -> {
            var l = loanForApplication(appNumber);
            return l != null && "COVERED".equals(l.get("status").asText());
        });
        var loan = loanForApplication(appNumber);
        return ok(postJson("/api/v1/loans/" + loan.get("id").asText() + "/disburse", "finance", Map.of()));
    }
}

package zw.insurehub.lendhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.JsonNode;

import zw.insurehub.lendhub.shared.crypto.WebhookSignature;

/** End-to-end journeys through the REST API on a real database (the demo script in docs/06-delivery-plan.md). */
class LoanLifecycleIntegrationTest extends IntegrationTestBase {

    @Test
    void origination_enforces_kyc_affordability_scoring_and_maker_checker() throws Exception {
        // KYC: bad ID rejected, duplicate ID rejected
        var bad = postJson("/api/v1/borrowers", "officer", Map.of("firstName", "A", "lastName", "B", "nationalId", "123",
                "msisdn", "0771234567", "dateOfBirth", "1990-01-01", "employmentType", "SALARIED", "bureauConsent", true));
        assertThat(bad.status()).isEqualTo(422);
        String id = randomNationalId();
        var first = Map.<String, Object>of("firstName", "Tendai", "lastName", "Moyo", "nationalId", id, "msisdn", randomMobile(),
                "dateOfBirth", "1985-02-02", "employmentType", "SALARIED", "employer", "Ministry of Health",
                "yearsInEmployment", 6, "bureauConsent", true);
        var borrower = ok(postJson("/api/v1/borrowers", "officer", first));
        assertThat(borrower.get("customerRef").asText()).startsWith("CUS-");
        assertThat(borrower.get("nationalIdMasked").asText()).startsWith("******");
        assertThat(postJson("/api/v1/borrowers", "officer", first).status()).isEqualTo(409);

        // product limits
        var tooBig = postJson("/api/v1/applications", "officer", Map.of("borrowerId", borrower.get("id").asText(),
                "productCode", "SALARY_ADVANCE", "amount", 5000, "instalments", 12, "frequency", "MONTHLY",
                "monthlyIncome", 900, "monthlyExpenses", 300));
        assertThat(tooBig.status()).isEqualTo(422);
        assertThat(tooBig.body().get("title").asText()).isEqualTo("amount-out-of-range");

        // US$1,500 salary advance: scored with reasons
        var app = ok(postJson("/api/v1/applications", "officer", Map.of("borrowerId", borrower.get("id").asText(),
                "productCode", "SALARY_ADVANCE", "amount", 1500, "instalments", 12, "frequency", "MONTHLY",
                "monthlyIncome", 900, "monthlyExpenses", 300)));
        String appId = app.get("id").asText();
        var scored = ok(postJson("/api/v1/applications/" + appId + "/submit", "officer", Map.of()));
        assertThat(scored.get("status").asText()).isEqualTo("SCORED");
        assertThat(scored.get("grade").asText()).isIn("A", "B", "C");
        assertThat(scored.get("scoreReasons")).isNotEmpty();
        assertThat(scored.get("affordable").asBoolean()).isTrue();

        // officers cannot approve; manager limit is US$1,000 → committee needed
        assertThat(postJson("/api/v1/applications/" + appId + "/decisions", "officer", Map.of("approve", true)).status()).isEqualTo(403);
        var overLimit = postJson("/api/v1/applications/" + appId + "/decisions", "manager", Map.of("approve", true));
        assertThat(overLimit.status()).isEqualTo(403);
        assertThat(overLimit.body().get("title").asText()).isEqualTo("approval-limit");

        // a decline needs a reason code
        assertThat(postJson("/api/v1/applications/" + appId + "/decisions", "committee", Map.of("approve", false)).status()).isEqualTo(422);

        var offered = ok(postJson("/api/v1/applications/" + appId + "/decisions", "committee", Map.of("approve", true)));
        assertThat(offered.get("status").asText()).isEqualTo("OFFERED");
        JsonNode offer = offered.get("offer");
        assertThat(offer.get("effectiveAnnualRatePercent").decimalValue()).isGreaterThan(BigDecimal.valueOf(50));
        assertThat(offer.get("totalRepayable").decimalValue()).isGreaterThan(BigDecimal.valueOf(1500));

        // wrong OTP counts as an attempt and is rejected
        var wrong = postJson("/api/v1/applications/" + appId + "/offer/accept", "officer", Map.of("otp", "000000"));
        assertThat(wrong.status()).isEqualTo(422);
        var accepted = ok(postJson("/api/v1/applications/" + appId + "/offer/accept", "officer",
                Map.of("otp", offered.get("demoOtp").asText())));
        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");

        // decision history is append-only and attributable
        var decisions = ok(getJson("/api/v1/applications/" + appId + "/decisions", "auditor"));
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).get("decidedBy").asText()).isEqualTo("committee@lendhub.demo");
    }

    @Test
    void disbursement_repayment_callback_and_ledger_balance() throws Exception {
        var loan = activeLoan("TRADER", 600, 4, "MONTHLY");
        String loanId = loan.get("id").asText();
        String loanNumber = loan.get("loanNumber").asText();
        assertThat(loan.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(loan.get("creditLifePolicyNumber").asText()).startsWith("CL-");
        assertThat(loan.get("schedule")).hasSize(4);
        assertThat(loan.get("netDisbursed").decimalValue()).isEqualByComparingTo("576.00"); // 4% fee deducted

        // HMAC callback from the Payments hub: bad signature rejected, good one posted, replay ignored
        String body = json.writeValueAsString(Map.of("paymentId", UUID.randomUUID(), "policyNumber", loanNumber,
                "amount", 100, "currency", "USD", "method", "EcoCash", "providerReference", "MP" + System.nanoTime(),
                "paidAt", Instant.now().toString()));
        var unsigned = call(post("/api/v1/integrations/payments").contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", "t=1,v1=00").content(body), null);
        assertThat(unsigned.status()).isEqualTo(401);
        String sig = WebhookSignature.create("dev-insurehub-callback-secret", body, Instant.now());
        var posted = ok(call(post("/api/v1/integrations/payments").contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", sig).content(body), null));
        assertThat(posted.get("duplicate").asBoolean()).isFalse();
        var replay = ok(call(post("/api/v1/integrations/payments").contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", sig).content(body), null));
        assertThat(replay.get("duplicate").asBoolean()).isTrue();

        var repayments = ok(getJson("/api/v1/loans/" + loanId + "/repayments", "finance"));
        assertThat(repayments).hasSize(1);
        // paid before the first due date: held as a credit balance
        assertThat(ok(getJson("/api/v1/loans/" + loanId, "finance")).get("creditBalance").decimalValue()).isEqualByComparingTo("100.00");

        // channel-initiated EcoCash repayment (simulated Payments hub completes it)
        var req = call(post("/api/v1/loans/" + loanId + "/repayment-requests").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "test-" + UUID.randomUUID()).content("{\"amount\": 50}"), "channel");
        assertThat(req.status()).isEqualTo(202);
        eventually(() -> ok(getJson("/api/v1/loans/" + loanId + "/repayments", "finance")).size() == 2);

        // ledger: disbursement and repayments are journalled and the trial balance balances
        var tb = ok(getJson("/api/v1/reports/trial-balance", "finance"));
        assertThat(tb.get("balanced").asBoolean()).isTrue();
        var journals = ok(getJson("/api/v1/ledger/journals?loanNumber=" + loanNumber, "finance"));
        assertThat(journals).extracting(j -> j.get("sourceType").asText()).contains("DISBURSEMENT", "REPAYMENT");

        // channel summary for InsureAssist / USSD
        String customerRef = loan.get("customerRef").asText();
        var summary = ok(getJson("/api/v1/customers/" + customerRef + "/loans", "channel"));
        assertThat(summary.get(0).get("loanNumber").asText()).isEqualTo(loanNumber);
        assertThat(summary.get(0).get("nextDueDate").asText()).isNotBlank();
        // channels cannot browse staff endpoints
        assertThat(getJson("/api/v1/loans", "channel").status()).isEqualTo(403);
    }

    @Test
    void reversal_and_early_settlement() throws Exception {
        var loan = activeLoan("SALARY_ADVANCE", 500, 6, "MONTHLY");
        String loanId = loan.get("id").asText();
        var receipt = ok(postJson("/api/v1/loans/" + loanId + "/repayments", "finance",
                Map.of("amount", 40, "channel", "CASH", "reference", "CASH-" + System.nanoTime())));
        var reversed = ok(postJson("/api/v1/repayments/" + receipt.get("id").asText() + "/reversal", "finance", Map.of("reason", "bounced")));
        assertThat(reversed.get("status").asText()).isEqualTo("REVERSED");
        assertThat(postJson("/api/v1/repayments/" + receipt.get("id").asText() + "/reversal", "finance", Map.of("reason", "again")).status())
                .isEqualTo(422);

        var quote = ok(getJson("/api/v1/loans/" + loanId + "/settlement-quote", "finance"));
        assertThat(quote.get("outstandingPrincipal").get("amount").decimalValue()).isEqualByComparingTo("500.00");
        var settled = postJson("/api/v1/loans/" + loanId + "/settlements", "finance", Map.of("amount",
                quote.get("total").get("amount").decimalValue(), "channel", "BANK", "reference", "RTGS-" + System.nanoTime()));
        assertThat(settled.status()).isEqualTo(201);
        assertThat(ok(getJson("/api/v1/loans/" + loanId, "finance")).get("status").asText()).isEqualTo("CLOSED");
        assertThat(ok(getJson("/api/v1/reports/trial-balance", "auditor")).get("balanced").asBoolean()).isTrue();
    }

    @Test
    void payroll_file_matches_by_national_id_and_lists_unmatched_lines() throws Exception {
        String nationalId = randomNationalId();
        var borrower = ok(postJson("/api/v1/borrowers", "officer", Map.of("firstName", "Rufaro", "lastName", "Ncube",
                "nationalId", nationalId, "msisdn", randomMobile(), "dateOfBirth", "1980-05-05", "employmentType", "SALARIED",
                "employer", "Ministry of Education", "yearsInEmployment", 10, "bureauConsent", true)));
        var app = acceptedApplication(borrower, "SALARY_ADVANCE", 800, 6, "MONTHLY");
        String appNumber = app.get("applicationNumber").asText();
        eventually(() -> loanForApplication(appNumber) != null
                && "COVERED".equals(loanForApplication(appNumber).get("status").asText()));
        ok(postJson("/api/v1/loans/" + loanForApplication(appNumber).get("id").asText() + "/disburse", "finance2", Map.of()));

        String csv = "employer,national_id,amount,period\n"
                + "Ministry of Education," + nationalId + ",120.00,2026-10\n"
                + "Ministry of Education,63-777777Z11,90.00,2026-10\n"
                + "Ministry of Education,not-an-id,90.00,2026-10\n";
        var file = new MockMultipartFile("file", "salaries-oct.csv", "text/csv", csv.getBytes());
        var batch = ok(call(multipart("/api/v1/payroll-batches").file(file), "finance"));
        assertThat(batch.get("matched").asInt()).isEqualTo(1);
        assertThat(batch.get("unmatched").asInt()).isEqualTo(2);
        assertThat(batch.get("lines").get(0).get("status").asText()).isEqualTo("POSTED");

        // uploading the same file again does not double-post
        var again = ok(call(multipart("/api/v1/payroll-batches").file(file), "finance"));
        assertThat(again.get("matched").asInt()).isZero();
    }

    @Test
    void fast_forward_45_days_drives_arrears_collections_staging_and_restructure() throws Exception {
        var loan = activeLoan("TRADER", 600, 12, "WEEKLY");
        String loanId = loan.get("id").asText();
        String loanNumber = loan.get("loanNumber").asText();

        var runs = ok(postJson("/api/v1/eod/runs", "finance", Map.of("days", 45)));
        assertThat(runs).hasSize(45);
        assertThat(runs).allMatch(r -> "COMPLETED".equals(r.get("status").asText()));

        var aged = ok(getJson("/api/v1/loans/" + loanId, "collections"));
        assertThat(aged.get("status").asText()).isEqualTo("IN_ARREARS");
        assertThat(aged.get("daysPastDue").asInt()).isBetween(31, 60);
        assertThat(aged.get("arrearsBucket").asText()).isEqualTo("DPD_31_60");
        assertThat(aged.get("interestReceivable").decimalValue()).isPositive();

        // collections worklist has the visit task (DPD ≥ 30)
        var tasks = ok(getJson("/api/v1/collections/loans/" + loanId + "/tasks", "collections"));
        assertThat(tasks).extracting(t -> t.get("type").asText()).contains("CALL", "VISIT");

        // PAR30 shows up and IFRS 9 moves the loan to Stage 2 on a provisioning run
        var par = ok(getJson("/api/v1/reports/par?currency=USD", "manager"));
        assertThat(par.get("total").get("par30").decimalValue()).isPositive();
        var provisionRuns = ok(postJson("/api/v1/provisioning/runs", "finance", Map.of()));
        String runId = provisionRuns.get(0).get("id").asText();
        var assignments = ok(getJson("/api/v1/provisioning/runs/" + runId + "/assignments", "finance"));
        JsonNode ours = null;
        for (JsonNode a : assignments) if (a.get("loanNumber").asText().equals(loanNumber)) ours = a;
        assertThat(ours).isNotNull();
        assertThat(ours.get("stage").asText()).isEqualTo("STAGE_2");

        // events went to the group bus (log mode in tests) and the ledger still balances
        var events = ok(getJson("/api/v1/integration/outbound-events", "finance"));
        assertThat(events).extracting(e -> e.get("routingKey").asText()).contains("loan.disbursed", "loan.arrears-changed");
        assertThat(ok(getJson("/api/v1/reports/trial-balance", "finance")).get("balanced").asBoolean()).isTrue();

        // restructure: maker (collections) requests, checker (committee) approves
        var request = ok(postJson("/api/v1/loans/" + loanId + "/restructure", "collections",
                Map.of("newInstalments", 16, "reason", "Stall lost stock in the rains; can pay smaller weekly amounts")));
        assertThat(postJson("/api/v1/loan-change-requests/" + request.get("id").asText() + "/decision", "collections",
                Map.of("approve", true)).status()).isEqualTo(403);
        ok(postJson("/api/v1/loan-change-requests/" + request.get("id").asText() + "/decision", "committee", Map.of("approve", true)));
        var restructured = ok(getJson("/api/v1/loans/" + loanId, "collections"));
        assertThat(restructured.get("status").asText()).isEqualTo("RESTRUCTURED");
        assertThat(restructured.get("restructured").asBoolean()).isTrue();

        // audit trail shows who changed the loan
        var audit = ok(getJson("/api/v1/audit/loans/" + loanId, "auditor"));
        assertThat(audit).extracting(a -> a.get("user").asText()).contains("finance@lendhub.demo", "committee@lendhub.demo");

        // portfolio dashboard and the regulatory-style return
        var portfolio = ok(getJson("/api/v1/reports/portfolio?currency=USD", "manager"));
        assertThat(portfolio.get("activeLoans").asInt()).isPositive();
        String csv = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/reports/regulatory-return")
                .header("Authorization", "Bearer " + token("finance"))).andReturn().getResponse().getContentAsString();
        assertThat(csv).startsWith("institution,return,as_of,currency,classification").contains("SUBSTANDARD");
    }
}

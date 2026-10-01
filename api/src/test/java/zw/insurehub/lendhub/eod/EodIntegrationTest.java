package zw.insurehub.lendhub.eod;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import zw.insurehub.lendhub.IntegrationTestBase;

/** LH-54: the EOD batch is restartable, never runs twice for a date, and only advances the date on success. */
class EodIntegrationTest extends IntegrationTestBase {

    @Autowired
    EodJobConfig.FaultInjector faults;
    @Autowired
    JdbcTemplate jdbc;

    private int accrualJournalsFor(java.time.LocalDate date) {
        return jdbc.queryForObject("select count(*) from ledger.journal_entry where source_type = 'ACCRUAL' and business_date = ?",
                Integer.class, date);
    }

    @Test
    void a_failed_run_keeps_the_date_and_a_restart_resumes_without_double_posting() throws Exception {
        activeLoan("TRADER", 400, 3, "MONTHLY");
        // interest starts accruing the day after disbursement
        ok(postJson("/api/v1/eod/runs", "finance", Map.of("days", 1)));
        var date = businessDate();

        faults.failOnce("penalties"); // accrual step commits, penalties step blows up
        var failed = ok(postJson("/api/v1/eod/runs", "finance", Map.of("days", 1)));
        assertThat(failed.get(0).get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.get(0).get("error").asText()).contains("Injected failure");
        assertThat(businessDate()).isEqualTo(date);
        int accruals = accrualJournalsFor(date);
        assertThat(accruals).isPositive();

        var restarted = ok(postJson("/api/v1/eod/runs", "finance", Map.of("days", 1)));
        assertThat(restarted.get(0).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(restarted.get(0).get("businessDate").asText()).isEqualTo(date.toString());
        assertThat(businessDate()).isEqualTo(date.plusDays(1));
        // the completed accrual step was skipped on restart: still one accrual per loan for that date
        assertThat(accrualJournalsFor(date)).isEqualTo(accruals);
        assertThat(ok(getJson("/api/v1/reports/trial-balance", "finance")).get("balanced").asBoolean()).isTrue();
    }

    @Test
    void a_run_already_in_progress_for_the_date_is_refused() throws Exception {
        var date = businessDate();
        jdbc.update("insert into eod.eod_run (id, business_date, status, started_by, started_at) values (?, ?, 'RUNNING', 'other', now())",
                UUID.randomUUID(), date);
        try {
            var r = postJson("/api/v1/eod/runs", "finance", Map.of("days", 1));
            assertThat(r.status()).isEqualTo(409);
            assertThat(r.body().get("title").asText()).isEqualTo("eod-running");
        } finally {
            jdbc.update("delete from eod.eod_run where business_date = ? and started_by = 'other'", date);
        }
    }

    @Test
    void only_finance_can_run_eod() throws Exception {
        assertThat(postJson("/api/v1/eod/runs", "officer", Map.of("days", 1)).status()).isEqualTo(403);
        assertThat(postJson("/api/v1/eod/runs", null, Map.of("days", 1)).status()).isEqualTo(401);
    }
}

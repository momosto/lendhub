package zw.insurehub.lendhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import zw.insurehub.lendhub.borrowers.KycRules;
import zw.insurehub.lendhub.loans.domain.ArrearsBucket;
import zw.insurehub.lendhub.provisioning.domain.Ifrs9;
import zw.insurehub.lendhub.scoring.CreditBureau;
import zw.insurehub.lendhub.scoring.RepaymentHistoryProvider.RepaymentHistory;
import zw.insurehub.lendhub.scoring.Scorecard;
import zw.insurehub.lendhub.shared.crypto.WebhookSignature;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;
import zw.insurehub.lendhub.shared.web.DomainException;

/** Small pure rules: KYC formats, buckets, IFRS 9 staging, scorecard, Money and webhook signatures. */
class RulesTest {

    @ParameterizedTest
    @CsvSource({ "63-123456A78,63-123456A78", "63 123456 a 78,63-123456A78", "631234567B12,63-1234567B12" })
    void national_ids_are_normalised(String raw, String expected) {
        assertThat(KycRules.normaliseNationalId(raw)).isEqualTo(expected);
    }

    @Test
    void bad_national_ids_are_rejected() {
        assertThatThrownBy(() -> KycRules.normaliseNationalId("12345")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> KycRules.normaliseNationalId("63-123456-78")).isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @CsvSource({ "0771234567,263771234567", "+263 78 123 4567,263781234567", "00263712345678,263712345678",
            "731234567,263731234567" })
    void mobile_numbers_are_normalised(String raw, String expected) {
        assertThat(KycRules.normaliseMsisdn(raw)).isEqualTo(expected);
    }

    @Test
    void landlines_and_foreign_numbers_are_rejected() {
        assertThatThrownBy(() -> KycRules.normaliseMsisdn("0242123456")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> KycRules.normaliseMsisdn("+27821234567")).isInstanceOf(DomainException.class);
    }

    @Test
    void national_id_is_masked_in_lists() {
        assertThat(KycRules.mask("63-123456A78")).isEqualTo("******6A78");
    }

    @ParameterizedTest
    @CsvSource({ "0,CURRENT", "1,DPD_1_30", "30,DPD_1_30", "31,DPD_31_60", "60,DPD_31_60", "61,DPD_61_90", "90,DPD_61_90", "91,DPD_90_PLUS" })
    void arrears_buckets(int dpd, ArrearsBucket bucket) {
        assertThat(ArrearsBucket.of(dpd)).isEqualTo(bucket);
    }

    @ParameterizedTest
    @CsvSource({ "0,false,STAGE_1", "30,false,STAGE_1", "31,false,STAGE_2", "90,false,STAGE_2", "91,false,STAGE_3",
            "0,true,STAGE_2" })
    void ifrs9_staging_uses_the_30_and_90_day_presumptions(int dpd, boolean restructured, Ifrs9.Stage stage) {
        assertThat(Ifrs9.stage(dpd, restructured, false)).isEqualTo(stage);
    }

    @Test
    void written_off_loans_are_stage_3_and_ecl_is_pd_times_lgd_times_ead() {
        assertThat(Ifrs9.stage(0, false, true)).isEqualTo(Ifrs9.Stage.STAGE_3);
        // 0.25 × 0.45 × 1,000 = 112.50
        assertThat(Ifrs9.ecl(new BigDecimal("0.25"), new BigDecimal("0.45"), Money.of("1000", CurrencyCode.USD)).amount())
                .isEqualByComparingTo("112.50");
    }

    @Test
    void scorecard_gives_reasons_and_forces_grade_e_for_adverse_bureau_or_unaffordable() {
        var clear = new CreditBureau.BureauReport(CreditBureau.Status.CLEAR, 0, "x");
        var good = Scorecard.score(new Scorecard.Input(true, new BigDecimal("4"), clear, new RepaymentHistory(2, 0, 3), false,
                new BigDecimal("0.20"), new BigDecimal("0.40")));
        assertThat(good.points()).isEqualTo(100);
        assertThat(good.grade()).isEqualTo(Scorecard.Grade.A);
        assertThat(good.reasons()).hasSize(5);

        var adverse = Scorecard.score(new Scorecard.Input(true, new BigDecimal("4"),
                new CreditBureau.BureauReport(CreditBureau.Status.ADVERSE, 3, "x"), RepaymentHistory.NONE, false,
                new BigDecimal("0.20"), new BigDecimal("0.40")));
        assertThat(adverse.grade()).isEqualTo(Scorecard.Grade.E);
        assertThat(adverse.declineRecommended()).isTrue();

        var unaffordable = Scorecard.score(new Scorecard.Input(true, new BigDecimal("4"), clear, RepaymentHistory.NONE, false,
                new BigDecimal("0.55"), new BigDecimal("0.40")));
        assertThat(unaffordable.grade()).isEqualTo(Scorecard.Grade.E);
        assertThat(unaffordable.reasons()).anyMatch(r -> r.contains("55%"));
    }

    @Test
    void trader_with_short_tenure_and_thin_file_is_a_mid_grade() {
        var r = Scorecard.score(new Scorecard.Input(false, new BigDecimal("0.5"),
                new CreditBureau.BureauReport(CreditBureau.Status.THIN_FILE, 0, "x"), RepaymentHistory.NONE, true,
                new BigDecimal("0.30"), new BigDecimal("0.40")));
        // 15 + 0 + 10 + 5 + 5 + 5 = 40 → D
        assertThat(r.points()).isEqualTo(40);
        assertThat(r.grade()).isEqualTo(Scorecard.Grade.D);
    }

    @Test
    void money_rejects_mixed_currencies_and_keeps_scale_2() {
        assertThat(Money.of("1.5", CurrencyCode.USD).amount().scale()).isEqualTo(2);
        assertThatThrownBy(() -> Money.of("1", CurrencyCode.USD).plus(Money.of("1", CurrencyCode.ZWG)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void webhook_signature_matches_the_integrations_format_and_rejects_replays() {
        String body = "{\"policyNumber\":\"LN-2610-000001\"}";
        Instant t = Instant.ofEpochSecond(1790000000L);
        String header = WebhookSignature.create("secret", body, t);
        // same bytes as .NET: hex(HMACSHA256("secret", "1790000000." + body)), lower case
        assertThat(header).startsWith("t=1790000000,v1=").hasSize("t=1790000000,v1=".length() + 64);
        assertThat(WebhookSignature.verify("secret", body, header, t.plusSeconds(10), Duration.ofMinutes(5))).isTrue();
        assertThat(WebhookSignature.verify("secret", body + " ", header, t, Duration.ofMinutes(5))).isFalse();
        assertThat(WebhookSignature.verify("other", body, header, t, Duration.ofMinutes(5))).isFalse();
        assertThat(WebhookSignature.verify("secret", body, header, t.plusSeconds(600), Duration.ofMinutes(5))).isFalse();
        assertThat(WebhookSignature.verify("secret", body, null, t, Duration.ofMinutes(5))).isFalse();
    }
}

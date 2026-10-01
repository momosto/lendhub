package zw.insurehub.lendhub.provisioning;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import zw.insurehub.lendhub.provisioning.domain.Ifrs9;

/**
 * Illustrative ECL parameters: 12-month PD for Stage 1, lifetime PD for Stages 2–3, one LGD for unsecured
 * microloans. Configurable so risk can calibrate them; they are not a validated model.
 */
@ConfigurationProperties("lendhub.provisioning")
public record EclParameters(BigDecimal pdStage1, BigDecimal pdStage2, BigDecimal pdStage3, BigDecimal lgd) {

    public EclParameters {
        if (pdStage1 == null) pdStage1 = new BigDecimal("0.02");
        if (pdStage2 == null) pdStage2 = new BigDecimal("0.25");
        if (pdStage3 == null) pdStage3 = BigDecimal.ONE;
        if (lgd == null) lgd = new BigDecimal("0.45");
    }

    public BigDecimal pd(Ifrs9.Stage stage) {
        return switch (stage) {
            case STAGE_1 -> pdStage1;
            case STAGE_2 -> pdStage2;
            case STAGE_3 -> pdStage3;
        };
    }
}

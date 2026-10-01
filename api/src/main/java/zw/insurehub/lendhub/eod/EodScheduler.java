package zw.insurehub.lendhub.eod;

import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly trigger at 00:30 Africa/Harare (in Kubernetes a CronJob can call the API instead). Catches up if the
 * business date is behind the calendar. This is the only place that reads the wall clock.
 */
@Component
@ConditionalOnProperty(name = "lendhub.eod.schedule-enabled", havingValue = "true")
class EodScheduler {

    private static final Logger log = LoggerFactory.getLogger(EodScheduler.class);
    private static final ZoneId HARARE = ZoneId.of("Africa/Harare");

    private final EodService service;

    EodScheduler(EodService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 30 0 * * *", zone = "Africa/Harare")
    void nightly() {
        LocalDate calendar = LocalDate.now(HARARE);
        int behind = (int) java.time.temporal.ChronoUnit.DAYS.between(service.businessDate(), calendar);
        if (behind <= 0) return;
        log.info("Nightly EOD: business date is {} day(s) behind", behind);
        service.run(Math.min(behind, 31));
    }
}

package zw.insurehub.lendhub.eod;

import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import zw.insurehub.lendhub.shared.time.BusinessDateProvider;

/** The bank's business date, stored in {@code eod.business_date}; only a successful EOD advances it (LH-54). */
@Service
class BusinessDateService implements BusinessDateProvider {

    private final JdbcTemplate jdbc;

    BusinessDateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public LocalDate today() {
        return jdbc.queryForObject("select business_date from eod.business_date where id = 1", LocalDate.class);
    }

    void advanceFrom(LocalDate expected) {
        int updated = jdbc.update("update eod.business_date set business_date = business_date + 1 where id = 1 and business_date = ?",
                expected);
        if (updated != 1) {
            throw new IllegalStateException("Business date moved while EOD for " + expected + " was running");
        }
    }
}

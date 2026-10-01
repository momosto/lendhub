package zw.insurehub.lendhub.products;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import zw.insurehub.lendhub.products.schedule.BusinessDays;

/**
 * Weekends plus the Zimbabwe public-holiday table ({@code products.public_holiday}), loaded once at startup.
 * Due dates that fall on a non-business day move to the next business day (LH-32).
 */
@Component
public class HolidayCalendar implements BusinessDays {

    private final Set<LocalDate> holidays;

    HolidayCalendar(JdbcTemplate jdbc) {
        this.holidays = jdbc.queryForList("select holiday_date from products.public_holiday", java.sql.Date.class)
                .stream().map(java.sql.Date::toLocalDate).collect(Collectors.toUnmodifiableSet());
    }

    public boolean isBusinessDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY
                && !holidays.contains(date);
    }

    @Override
    public LocalDate nextBusinessDay(LocalDate date) {
        LocalDate d = date;
        while (!isBusinessDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }
}

package zw.insurehub.lendhub.products.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Rolls a date forward to the next business day (weekends and public holidays are skipped). */
@FunctionalInterface
public interface BusinessDays {

    LocalDate nextBusinessDay(LocalDate date);

    /** Weekend-only calendar, for tests and quotes without a holiday table. */
    BusinessDays WEEKENDS_ONLY = date -> {
        LocalDate d = date;
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    };
}

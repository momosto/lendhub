package zw.insurehub.lendhub.shared.time;

import java.time.LocalDate;

/**
 * The bank's business date. Domain code asks this, never {@code LocalDate.now()} (enforced by an ArchUnit rule),
 * so the end-of-day batch can advance time and tests can fast-forward.
 */
public interface BusinessDateProvider {

    LocalDate today();
}

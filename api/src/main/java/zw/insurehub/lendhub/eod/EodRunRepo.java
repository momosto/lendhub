package zw.insurehub.lendhub.eod;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface EodRunRepo extends JpaRepository<EodRun, UUID> {

    Optional<EodRun> findByBusinessDate(LocalDate date);

    List<EodRun> findTop60ByOrderByBusinessDateDesc();
}

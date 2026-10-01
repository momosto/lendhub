package zw.insurehub.lendhub.ledger;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    boolean existsBySourceTypeAndSourceRef(String sourceType, String sourceRef);

    List<JournalEntry> findByLoanNumberOrderByPostedAt(String loanNumber);

    List<JournalEntry> findAllByOrderByPostedAtDesc(Pageable page);
}

interface GlAccountRepository extends JpaRepository<GlAccount, String> {
}

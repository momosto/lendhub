package zw.insurehub.lendhub.origination;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    List<LoanApplication> findAllByOrderByCreatedAtDesc();

    List<LoanApplication> findByBranchOrderByCreatedAtDesc(String branch);

    List<LoanApplication> findByStatusAndOfferExpiresOnBefore(LoanApplication.Status status, LocalDate date);

    @Query("""
            select count(a) from LoanApplication a
            where a.id <> :id and a.createdAt >= :since
              and (a.msisdn = :msisdn or a.borrowerId in :borrowerIds)
              and a.status not in ('DECLINED', 'EXPIRED')""")
    long countRelated(UUID id, String msisdn, Collection<UUID> borrowerIds, Instant since);

    @Query(value = "select nextval('origination.application_seq')", nativeQuery = true)
    long nextNumber();
}

interface ApprovalDecisionRepository extends JpaRepository<ApprovalDecision, UUID> {

    List<ApprovalDecision> findByApplicationIdOrderByDecidedAt(UUID applicationId);
}

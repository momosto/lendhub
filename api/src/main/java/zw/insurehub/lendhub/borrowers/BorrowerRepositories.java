package zw.insurehub.lendhub.borrowers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface BorrowerRepository extends JpaRepository<Borrower, UUID> {

    Optional<Borrower> findByNationalIdHash(String hash);

    Optional<Borrower> findByCustomerRef(String customerRef);

    List<Borrower> findByMsisdn(String msisdn);

    List<Borrower> findByBranchOrderByCreatedAtDesc(String branch);

    List<Borrower> findAllByOrderByCreatedAtDesc();

    @Query(value = "select nextval('borrowers.customer_ref_seq')", nativeQuery = true)
    long nextCustomerNumber();
}

interface LoanGroupRepository extends JpaRepository<LoanGroup, UUID> {

    @Query("select count(g) from LoanGroup g join g.memberIds m where g.active = true and m = :borrowerId")
    long countActiveGroupsWithMember(UUID borrowerId);

    @Query("select g from LoanGroup g join g.memberIds m where g.active = true and m = :borrowerId")
    Optional<LoanGroup> findActiveGroupOf(UUID borrowerId);
}

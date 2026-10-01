package zw.insurehub.lendhub.loans;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface LoanAccountRepository extends JpaRepository<LoanAccount, UUID> {

    Optional<LoanAccount> findByLoanNumber(String loanNumber);

    Optional<LoanAccount> findByApplicationId(UUID applicationId);

    List<LoanAccount> findByCustomerRefOrderByCreatedAtDesc(String customerRef);

    List<LoanAccount> findByBorrowerId(UUID borrowerId);

    List<LoanAccount> findAllByOrderByCreatedAtDesc();

    List<LoanAccount> findByBranchOrderByCreatedAtDesc(String branch);

    List<LoanAccount> findByStatusIn(Collection<LoanAccount.Status> statuses);

    @Query("select l.id from LoanAccount l where l.status in :statuses order by l.id")
    List<UUID> findIdsByStatusIn(Collection<LoanAccount.Status> statuses);

    @Query(value = "select nextval('loans.loan_number_seq')", nativeQuery = true)
    long nextNumber();
}

interface LoanChangeRequestRepository extends JpaRepository<LoanChangeRequest, UUID> {

    List<LoanChangeRequest> findByStatusOrderByRequestedAt(LoanChangeRequest.Status status);

    List<LoanChangeRequest> findByLoanIdOrderByRequestedAt(UUID loanId);
}

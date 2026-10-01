package zw.insurehub.lendhub.collections;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CollectionTaskRepository extends JpaRepository<CollectionTask, UUID> {

    List<CollectionTask> findByStatusOrderByPriorityDesc(CollectionTask.Status status);

    List<CollectionTask> findAllByOrderByCreatedOnDesc();

    List<CollectionTask> findByLoanIdOrderByCreatedOnDesc(UUID loanId);

    List<CollectionTask> findByLoanIdAndStatus(UUID loanId, CollectionTask.Status status);

    boolean existsByLoanIdAndTypeAndCreatedOnGreaterThanEqual(UUID loanId, CollectionTask.Type type, LocalDate since);
}

interface PromiseToPayRepository extends JpaRepository<PromiseToPay, UUID> {

    List<PromiseToPay> findByStatusAndPromisedDateBefore(PromiseToPay.Status status, LocalDate date);

    List<PromiseToPay> findByLoanIdOrderByPromisedDateDesc(UUID loanId);
}

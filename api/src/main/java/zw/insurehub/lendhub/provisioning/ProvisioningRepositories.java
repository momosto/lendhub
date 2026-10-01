package zw.insurehub.lendhub.provisioning;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import zw.insurehub.lendhub.shared.money.CurrencyCode;

interface ProvisionRunRepo extends JpaRepository<ProvisionRun, UUID> {

    Optional<ProvisionRun> findFirstByCurrencyOrderByCreatedAtDesc(CurrencyCode currency);

    List<ProvisionRun> findAllByOrderByCreatedAtDesc();
}

interface StageAssignmentRepo extends JpaRepository<ProvisionRun.StageAssignment, UUID> {

    List<ProvisionRun.StageAssignment> findByRunIdOrderByLoanNumber(UUID runId);

    List<ProvisionRun.StageAssignment> findByLoanId(UUID loanId);
}

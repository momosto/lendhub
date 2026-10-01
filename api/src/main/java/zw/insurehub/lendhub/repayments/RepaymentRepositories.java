package zw.insurehub.lendhub.repayments;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface RepaymentRepository extends JpaRepository<Repayment, UUID> {

    Optional<Repayment> findByProviderReference(String providerReference);

    List<Repayment> findByLoanIdOrderByReceivedAtDesc(UUID loanId);

    @Query("""
            select coalesce(sum(r.amount), 0) from Repayment r
            where r.loanId = :loanId and r.status = 'POSTED' and r.valueDate >= :since""")
    BigDecimal totalReceivedSince(UUID loanId, LocalDate since);

    @Query("""
            select coalesce(sum(r.amount), 0) from Repayment r
            where r.status = 'POSTED' and r.valueDate between :from and :to and r.currency = :currency""")
    BigDecimal totalReceivedBetween(LocalDate from, LocalDate to, zw.insurehub.lendhub.shared.money.CurrencyCode currency);
}

interface PayrollBatchRepository extends JpaRepository<PayrollBatch, UUID> {

    List<PayrollBatch> findAllByOrderByUploadedAtDesc();
}

interface PaymentRequestRepository extends JpaRepository<PaymentRequest, UUID> {

    Optional<PaymentRequest> findByIdempotencyKey(String key);
}

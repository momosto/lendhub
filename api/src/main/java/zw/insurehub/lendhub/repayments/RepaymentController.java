package zw.insurehub.lendhub.repayments;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.insurehub.lendhub.loans.LoanServicing;
import zw.insurehub.lendhub.shared.money.Money;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Repayments")
class RepaymentController {

    record ManualReceipt(@NotNull @DecimalMin("0.01") BigDecimal amount, @NotNull Repayment.Channel channel,
            @NotBlank String reference) {
    }

    record ReversalRequest(@NotBlank String reason) {
    }

    record PaymentRequestBody(@NotNull @DecimalMin("0.01") BigDecimal amount, String msisdn) {
    }

    private final RepaymentService service;
    private final LoanServicing loans;

    RepaymentController(RepaymentService service, LoanServicing loans) {
        this.service = service;
        this.loans = loans;
    }

    @GetMapping("/loans/{loanId}/repayments")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER','COMMITTEE','FINANCE','COLLECTIONS','AUDITOR')")
    List<Repayment> forLoan(@PathVariable UUID loanId) {
        loans.getScoped(loanId);
        return service.forLoan(loanId);
    }

    @PostMapping("/loans/{loanId}/repayments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FINANCE')")
    @Operation(summary = "Record a cash or bank receipt (idempotent on reference)")
    Repayment manual(@PathVariable UUID loanId, @Valid @RequestBody ManualReceipt r) {
        var loan = loans.get(loanId);
        return service.receive(new RepaymentService.ReceivePayment(loan.getLoanNumber(), Money.of(r.amount(), loan.getCurrency()),
                r.channel(), r.reference())).repayment();
    }

    @PostMapping("/loans/{loanId}/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FINANCE')")
    @Operation(summary = "Early settlement: pays the settlement quote and closes the loan")
    Repayment settle(@PathVariable UUID loanId, @Valid @RequestBody ManualReceipt r) {
        var loan = loans.get(loanId);
        return service.settle(loanId, Money.of(r.amount(), loan.getCurrency()), r.channel(), r.reference());
    }

    @PostMapping("/repayments/{id}/reversal")
    @PreAuthorize("hasRole('FINANCE')")
    @Operation(summary = "Reverse a repayment with compensating entries")
    Repayment reverse(@PathVariable UUID id, @Valid @RequestBody ReversalRequest r) {
        return service.reverse(id, r.reason());
    }

    @PostMapping("/loans/{loanId}/repayment-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('CHANNEL','OFFICER','COLLECTIONS')")
    @Operation(summary = "Start an EcoCash repayment via the Payments hub (Idempotency-Key required) — LH-90")
    PaymentRequest requestPayment(@PathVariable UUID loanId, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequestBody body) {
        var loan = loans.get(loanId);
        return service.requestPayment(loanId, Money.of(body.amount(), loan.getCurrency()), body.msisdn(), idempotencyKey);
    }

    @PostMapping(value = "/payroll-batches", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FINANCE')")
    @Operation(summary = "Upload an employer payroll deduction CSV: employer,national_id,amount,period")
    PayrollBatch uploadPayroll(@RequestParam("file") MultipartFile file) throws IOException {
        return service.importPayroll(file.getOriginalFilename(), file.getInputStream());
    }

    @GetMapping("/payroll-batches")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR')")
    List<PayrollBatch> payrollBatches() {
        return service.payrollBatches();
    }

    @GetMapping("/payroll-batches/{id}")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR')")
    PayrollBatch payrollBatch(@PathVariable UUID id) {
        return service.payrollBatch(id);
    }
}

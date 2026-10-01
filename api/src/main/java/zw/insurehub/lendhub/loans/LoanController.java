package zw.insurehub.lendhub.loans;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import zw.insurehub.lendhub.loans.domain.ArrearsBucket;
import zw.insurehub.lendhub.loans.domain.Component;
import zw.insurehub.lendhub.products.schedule.Frequency;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;
import zw.insurehub.lendhub.shared.time.BusinessDateProvider;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Loans")
class LoanController {

    record InstalmentDto(int seq, LocalDate dueDate, Instalment.State state, BigDecimal principalDue, BigDecimal interestDue,
            BigDecimal creditLifeDue, BigDecimal feesDue, BigDecimal penaltyDue, BigDecimal totalDue, BigDecimal totalPaid,
            BigDecimal outstanding, BigDecimal interestAccrued, LocalDate paidOn) {

        static InstalmentDto from(Instalment i) {
            return new InstalmentDto(i.getSeq(), i.getDueDate(), i.getState(), i.due(Component.PRINCIPAL).amount(),
                    i.due(Component.INTEREST).amount(), i.due(Component.CREDIT_LIFE).amount(), i.due(Component.FEE).amount(),
                    i.due(Component.PENALTY).amount(), i.totalDue().amount(), i.totalPaid().amount(),
                    i.totalOutstanding().amount(), i.getInterestAccrued().amount(), i.getPaidOn());
        }
    }

    record LoanDto(UUID id, String loanNumber, String applicationNumber, UUID borrowerId, String customerRef,
            String borrowerName, String branch, String productCode, CurrencyCode currency, BigDecimal principal,
            Frequency frequency, int instalmentCount, BigDecimal establishmentFee, BigDecimal netDisbursed,
            LoanAccount.Status status, String creditLifePolicyNumber, String coverError, LocalDate disbursedOn,
            LocalDate maturityDate, int daysPastDue, ArrearsBucket arrearsBucket, boolean restructured,
            BigDecimal outstandingPrincipal, BigDecimal interestReceivable, BigDecimal arrearsAmount, BigDecimal creditBalance,
            String capturedBy, String approvedBy, String disbursedBy, List<InstalmentDto> schedule) {

        static LoanDto from(LoanAccount l, LocalDate today, boolean withSchedule) {
            return new LoanDto(l.getId(), l.getLoanNumber(), l.getApplicationNumber(), l.getBorrowerId(), l.getCustomerRef(),
                    l.getBorrowerName(), l.getBranch(), l.getProductCode(), l.getCurrency(), l.getPrincipal(), l.getFrequency(),
                    l.getInstalmentCount(), l.getEstablishmentFee(), l.getNetDisbursed(), l.getStatus(),
                    l.getCreditLifePolicyNumber(), l.getCoverError(), l.getDisbursedOn(), l.getMaturityDate(),
                    l.getDaysPastDue(), l.getArrearsBucket(), l.isRestructured(), l.outstandingPrincipal().amount(),
                    l.interestReceivable().amount(), l.arrearsAmount(today).amount(), l.getCreditBalance().amount(),
                    l.getCapturedBy(), l.getApprovedBy(), l.getDisbursedBy(),
                    withSchedule ? l.getInstalments().stream().map(InstalmentDto::from).toList() : null);
        }
    }

    /** Customer-facing summary for channels (InsureAssist, USSD): no staff data, no national ID. */
    record CustomerLoanDto(String loanNumber, String productCode, CurrencyCode currency, LoanAccount.Status status,
            BigDecimal principal, BigDecimal outstandingPrincipal, BigDecimal arrearsAmount, int daysPastDue,
            LocalDate nextDueDate, BigDecimal nextAmountDue, BigDecimal settlementAmount, UUID loanId) {
    }

    record ChangeRequestBody(@Min(1) Integer newInstalments, @NotBlank @Size(max = 500) String reason) {
    }

    record ChangeDecisionBody(boolean approve) {
    }

    private final LoanServicing servicing;
    private final BusinessDateProvider businessDate;

    LoanController(LoanServicing servicing, BusinessDateProvider businessDate) {
        this.servicing = servicing;
        this.businessDate = businessDate;
    }

    @GetMapping("/loans")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER','COMMITTEE','FINANCE','COLLECTIONS','AUDITOR')")
    List<LoanDto> list() {
        LocalDate today = businessDate.today();
        return servicing.list().stream().map(l -> LoanDto.from(l, today, false)).toList();
    }

    @GetMapping("/loans/{id}")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER','COMMITTEE','FINANCE','COLLECTIONS','AUDITOR')")
    LoanDto get(@PathVariable UUID id) {
        return LoanDto.from(servicing.getScoped(id), businessDate.today(), true);
    }

    @GetMapping("/loans/{id}/schedule")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER','COMMITTEE','FINANCE','COLLECTIONS','AUDITOR')")
    List<InstalmentDto> schedule(@PathVariable UUID id) {
        return servicing.getScoped(id).getInstalments().stream().map(InstalmentDto::from).toList();
    }

    @GetMapping("/loans/{id}/settlement-quote")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER','FINANCE','COLLECTIONS','CHANNEL')")
    @Operation(summary = "Early settlement quote: principal + accrued interest + charges + settlement fee − credit")
    LoanAccount.SettlementQuote settlementQuote(@PathVariable UUID id, @RequestParam(required = false) LocalDate asOf) {
        return servicing.settlementQuote(id, asOf != null ? asOf : businessDate.today());
    }

    @PostMapping("/loans/{id}/credit-life")
    @PreAuthorize("hasRole('FINANCE')")
    @Operation(summary = "Retry the credit life cover request to InsureHub")
    LoanDto retryCover(@PathVariable UUID id) {
        return LoanDto.from(servicing.retryCover(id), businessDate.today(), false);
    }

    @PostMapping("/loans/{id}/disburse")
    @PreAuthorize("hasRole('FINANCE')")
    @Operation(summary = "Disburse to EcoCash (needs credit life cover; releaser ≠ capturer/approver)")
    LoanDto disburse(@PathVariable UUID id) {
        return LoanDto.from(servicing.disburse(id), businessDate.today(), true);
    }

    @PostMapping("/loans/{id}/restructure")
    @PreAuthorize("hasAnyRole('MANAGER','COLLECTIONS')")
    @Operation(summary = "Request a restructure (credit committee approves)")
    LoanChangeRequest requestRestructure(@PathVariable UUID id, @Valid @RequestBody ChangeRequestBody body) {
        return servicing.requestChange(id, LoanChangeRequest.Type.RESTRUCTURE, body.newInstalments(), body.reason());
    }

    @PostMapping("/loans/{id}/write-off")
    @PreAuthorize("hasAnyRole('MANAGER','COLLECTIONS')")
    @Operation(summary = "Request a write-off (credit committee approves)")
    LoanChangeRequest requestWriteOff(@PathVariable UUID id, @Valid @RequestBody ChangeRequestBody body) {
        return servicing.requestChange(id, LoanChangeRequest.Type.WRITE_OFF, null, body.reason());
    }

    @GetMapping("/loan-change-requests")
    @PreAuthorize("hasAnyRole('COMMITTEE','MANAGER','COLLECTIONS','AUDITOR')")
    List<LoanChangeRequest> pendingChanges() {
        return servicing.pendingChanges();
    }

    @GetMapping("/loans/{id}/change-requests")
    @PreAuthorize("hasAnyRole('COMMITTEE','MANAGER','COLLECTIONS','AUDITOR','FINANCE')")
    List<LoanChangeRequest> changesFor(@PathVariable UUID id) {
        return servicing.changesFor(id);
    }

    @PostMapping("/loan-change-requests/{requestId}/decision")
    @PreAuthorize("hasRole('COMMITTEE')")
    @Operation(summary = "Credit committee approves or rejects a restructure / write-off")
    LoanChangeRequest decide(@PathVariable UUID requestId, @RequestBody ChangeDecisionBody body) {
        return servicing.decideChange(requestId, body.approve());
    }

    @GetMapping("/customers/{customerRef}/loans")
    @PreAuthorize("hasAnyRole('CHANNEL','OFFICER','MANAGER','COLLECTIONS','AUDITOR')")
    @Operation(summary = "Loan summary for group channels (InsureAssist MCP tools, USSD) — LH-90")
    List<CustomerLoanDto> customerLoans(@PathVariable String customerRef) {
        LocalDate today = businessDate.today();
        return servicing.byCustomer(customerRef).stream().filter(l -> l.getDisbursedOn() != null).map(l -> {
            var next = l.nextInstalment(today);
            Money settlement = l.isServicing() ? l.settlementQuote(today).total() : Money.zero(l.getCurrency());
            return new CustomerLoanDto(l.getLoanNumber(), l.getProductCode(), l.getCurrency(), l.getStatus(), l.getPrincipal(),
                    l.outstandingPrincipal().amount(), l.arrearsAmount(today).amount(), l.getDaysPastDue(),
                    next.map(Instalment::getDueDate).orElse(null),
                    next.map(i -> i.totalOutstanding().amount()).orElse(BigDecimal.ZERO), settlement.amount(), l.getId());
        }).toList();
    }
}

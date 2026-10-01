package zw.insurehub.lendhub.origination;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import zw.insurehub.lendhub.products.schedule.Frequency;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;

@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Applications")
class ApplicationController {

    record CaptureRequest(@NotNull UUID borrowerId, @NotBlank String productCode, @NotNull @DecimalMin("1") BigDecimal amount,
            CurrencyCode currency, @Min(1) int instalments, @NotNull Frequency frequency, @Size(max = 200) String purpose,
            @NotNull @PositiveOrZero BigDecimal monthlyIncome, @NotNull @PositiveOrZero BigDecimal monthlyExpenses,
            @PositiveOrZero BigDecimal otherDebtRepayments) {
    }

    record DecisionRequest(boolean approve, String reasonCode, @Size(max = 500) String comment) {
    }

    record AcceptRequest(@NotBlank @Pattern(regexp = "\\d{6}") String otp) {
    }

    record OfferDto(BigDecimal instalment, BigDecimal totalInterest, BigDecimal establishmentFee, BigDecimal totalCreditLife,
            BigDecimal totalRepayable, BigDecimal netDisbursed, BigDecimal effectiveAnnualRatePercent, LocalDate expiresOn) {
    }

    record ApplicationDto(UUID id, String applicationNumber, UUID borrowerId, String customerRef, String borrowerName,
            String branch, String productCode, CurrencyCode currency, BigDecimal amount, int instalments, Frequency frequency,
            String purpose, BigDecimal monthlyIncome, BigDecimal monthlyExpenses, BigDecimal otherDebtRepayments,
            LoanApplication.Status status, String capturedBy, Instant createdAt, BigDecimal monthlyInstalmentEstimate,
            BigDecimal disposableIncome, BigDecimal affordabilityRatio, boolean affordable, Integer scorePoints, String grade,
            List<String> scoreReasons, String bureauStatus, boolean relatedPartyFlag, String decidedBy, Instant decidedAt,
            String declineReason, OfferDto offer, Instant acceptedAt, String demoOtp) {

        static ApplicationDto from(LoanApplication a, String demoOtp) {
            OfferDto offer = a.getOfferExpiresOn() == null ? null
                    : new OfferDto(a.getOfferInstalment(), a.getOfferTotalInterest(), a.getOfferEstablishmentFee(),
                            a.getOfferTotalCreditLife(), a.getOfferTotalRepayable(), a.getOfferNetDisbursed(),
                            a.getOfferEffectiveAnnualRate(), a.getOfferExpiresOn());
            List<String> reasons = a.getScoreReasons() == null ? List.of() : Arrays.asList(a.getScoreReasons().split("\n"));
            return new ApplicationDto(a.getId(), a.getApplicationNumber(), a.getBorrowerId(), a.getCustomerRef(),
                    a.getBorrowerName(), a.getBranch(), a.getProductCode(), a.getCurrency(), a.getAmount(), a.getInstalments(),
                    a.getFrequency(), a.getPurpose(), a.getMonthlyIncome(), a.getMonthlyExpenses(), a.getOtherDebtRepayments(),
                    a.getStatus(), a.getCapturedBy(), a.getCreatedAt(), a.getMonthlyInstalmentEstimate(), a.getDisposableIncome(),
                    a.getAffordabilityRatio(), a.isAffordable(), a.getScorePoints(), a.getGrade(), reasons, a.getBureauStatus(),
                    a.isRelatedPartyFlag(), a.getDecidedBy(), a.getDecidedAt(), a.getDeclineReason(), offer, a.getAcceptedAt(),
                    demoOtp);
        }
    }

    private final ApplicationService service;

    ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OFFICER')")
    @Operation(summary = "Capture an application (draft)")
    ApplicationDto capture(@Valid @RequestBody CaptureRequest r) {
        var currency = r.currency() == null ? CurrencyCode.USD : r.currency();
        return ApplicationDto.from(service.capture(new ApplicationService.CaptureApplication(r.borrowerId(), r.productCode(),
                Money.of(r.amount(), currency), r.instalments(), r.frequency(), r.purpose(), r.monthlyIncome(),
                r.monthlyExpenses(), r.otherDebtRepayments())), null);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('OFFICER')")
    @Operation(summary = "Submit: runs affordability and the scorecard")
    ApplicationDto submit(@PathVariable UUID id) {
        return ApplicationDto.from(service.submit(id), null);
    }

    @PostMapping("/{id}/decisions")
    @PreAuthorize("hasAnyRole('MANAGER','COMMITTEE')")
    @Operation(summary = "Approve or decline (maker ≠ checker; manager limit US$1,000)")
    ApplicationDto decide(@PathVariable UUID id, @Valid @RequestBody DecisionRequest r) {
        var issued = service.decide(id, new ApplicationService.Decision(r.approve(), r.reasonCode(), r.comment()));
        return ApplicationDto.from(issued.application(), issued.demoOtp());
    }

    @PostMapping("/{id}/offer/otp")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER')")
    @Operation(summary = "Re-send the offer acceptance code")
    ApplicationDto reissueOtp(@PathVariable UUID id) {
        var issued = service.reissueOtp(id);
        return ApplicationDto.from(issued.application(), issued.demoOtp());
    }

    @PostMapping("/{id}/offer/accept")
    @PreAuthorize("hasAnyRole('OFFICER','CHANNEL')")
    @Operation(summary = "Borrower accepts the offer with the OTP sent by SMS")
    ApplicationDto accept(@PathVariable UUID id, @Valid @RequestBody AcceptRequest r) {
        return ApplicationDto.from(service.accept(id, r.otp()), null);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER','COMMITTEE','FINANCE','AUDITOR')")
    List<ApplicationDto> list() {
        return service.list().stream().map(a -> ApplicationDto.from(a, null)).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER','COMMITTEE','FINANCE','AUDITOR')")
    ApplicationDto get(@PathVariable UUID id) {
        return ApplicationDto.from(service.get(id), null);
    }

    @GetMapping("/{id}/decisions")
    @PreAuthorize("hasAnyRole('MANAGER','COMMITTEE','AUDITOR')")
    @Operation(summary = "Append-only decision history")
    List<ApprovalDecision> decisions(@PathVariable UUID id) {
        service.get(id);
        return service.decisions(id);
    }

    @GetMapping("/decline-reasons")
    List<String> declineReasons() {
        return ApplicationService.DECLINE_REASONS;
    }
}

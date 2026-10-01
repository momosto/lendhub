package zw.insurehub.lendhub.borrowers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Borrowers")
class BorrowerController {

    record RegisterRequest(@NotBlank @Size(max = 60) String firstName, @NotBlank @Size(max = 60) String lastName,
            @NotBlank String nationalId, @NotBlank String msisdn, @NotNull @Past LocalDate dateOfBirth,
            @Size(max = 200) String address, @NotNull Borrower.EmploymentType employmentType, @Size(max = 100) String employer,
            @PositiveOrZero BigDecimal yearsInEmployment, boolean bureauConsent) {
    }

    record BorrowerDto(UUID id, String customerRef, String firstName, String lastName, String nationalIdMasked,
            String msisdn, LocalDate dateOfBirth, String address, String branch, Borrower.EmploymentType employmentType,
            String employer, BigDecimal yearsInEmployment, Instant bureauConsentAt, String bureauConsentVersion,
            Instant createdAt) {

        static BorrowerDto from(Borrower b) {
            return new BorrowerDto(b.getId(), b.getCustomerRef(), b.getFirstName(), b.getLastName(),
                    KycRules.mask(b.getNationalId()), b.getMsisdn(), b.getDateOfBirth(), b.getAddress(), b.getBranch(),
                    b.getEmploymentType(), b.getEmployer(), b.getYearsInEmployment(), b.getBureauConsentAt(),
                    b.getBureauConsentVersion(), b.getCreatedAt());
        }
    }

    record GroupRequest(@NotBlank String name, @NotNull UUID chairpersonId, @NotEmpty Set<UUID> memberIds) {
    }

    record GroupDto(UUID id, String name, String branch, UUID chairpersonId, Set<UUID> memberIds, boolean active) {
        static GroupDto from(LoanGroup g) {
            return new GroupDto(g.getId(), g.getName(), g.getBranch(), g.getChairpersonId(), g.getMemberIds(), g.isActive());
        }
    }

    private final BorrowerService service;

    BorrowerController(BorrowerService service) {
        this.service = service;
    }

    @PostMapping("/borrowers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OFFICER')")
    @Operation(summary = "Register a borrower (KYC, bureau consent)")
    BorrowerDto register(@Valid @RequestBody RegisterRequest r) {
        return BorrowerDto.from(service.register(new BorrowerService.RegisterBorrower(r.firstName(), r.lastName(),
                r.nationalId(), r.msisdn(), r.dateOfBirth(), r.address(), r.employmentType(), r.employer(),
                r.yearsInEmployment(), r.bureauConsent(), null)));
    }

    @GetMapping("/borrowers")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER','COMMITTEE','FINANCE','COLLECTIONS','AUDITOR')")
    @Operation(summary = "List borrowers (branch-scoped for officers and managers; national ID masked)")
    List<BorrowerDto> list() {
        return service.list().stream().map(BorrowerDto::from).toList();
    }

    @GetMapping("/borrowers/{id}")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER','COMMITTEE','FINANCE','COLLECTIONS','AUDITOR')")
    BorrowerDto get(@PathVariable UUID id) {
        return BorrowerDto.from(service.get(id));
    }

    record CustomerLookupDto(String customerRef, String firstName, String branch) {
    }

    @GetMapping("/customers")
    @PreAuthorize("hasRole('CHANNEL')")
    @Operation(summary = "Channel lookup by mobile number (USSD session / WhatsApp sender) → customer reference")
    List<CustomerLookupDto> lookup(@org.springframework.web.bind.annotation.RequestParam String msisdn) {
        return service.findByMsisdn(msisdn).stream()
                .map(b -> new CustomerLookupDto(b.getCustomerRef(), b.getFirstName(), b.getBranch())).toList();
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OFFICER')")
    @Operation(summary = "Form a solidarity group (5–10 members)")
    GroupDto formGroup(@Valid @RequestBody GroupRequest r) {
        return GroupDto.from(service.formGroup(r.name(), r.chairpersonId(), r.memberIds()));
    }

    @GetMapping("/groups")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER','COMMITTEE','AUDITOR')")
    List<GroupDto> groups() {
        return service.groups().stream().map(GroupDto::from).toList();
    }
}

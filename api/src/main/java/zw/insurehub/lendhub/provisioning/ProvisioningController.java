package zw.insurehub.lendhub.provisioning;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import zw.insurehub.lendhub.shared.time.BusinessDateProvider;

@RestController
@RequestMapping("/api/v1/provisioning")
@Tag(name = "Provisioning")
class ProvisioningController {

    private final ProvisioningService service;
    private final BusinessDateProvider businessDate;

    ProvisioningController(ProvisioningService service, BusinessDateProvider businessDate) {
        this.service = service;
        this.businessDate = businessDate;
    }

    @PostMapping("/runs")
    @PreAuthorize("hasRole('FINANCE')")
    @Operation(summary = "Run IFRS 9 staging and ECL now (EOD runs it automatically at month-end)")
    List<ProvisionRun> run() {
        return service.run(businessDate.today());
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','MANAGER','COMMITTEE')")
    List<ProvisionRun> runs() {
        return service.runs();
    }

    @GetMapping("/runs/{id}/assignments")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','MANAGER','COMMITTEE')")
    List<ProvisionRun.StageAssignment> assignments(@PathVariable UUID id) {
        return service.assignments(id);
    }

    @GetMapping("/loans/{loanId}")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR','MANAGER','COMMITTEE','COLLECTIONS')")
    List<ProvisionRun.StageAssignment> forLoan(@PathVariable UUID loanId) {
        return service.latestFor(loanId);
    }
}

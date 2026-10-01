package zw.insurehub.lendhub.eod;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/eod")
@Tag(name = "End of day")
class EodController {

    record RunRequest(@Min(1) @Max(366) Integer days) {
    }

    private final EodService service;

    EodController(EodService service) {
        this.service = service;
    }

    @GetMapping("/business-date")
    Map<String, LocalDate> businessDate() {
        return Map.of("businessDate", service.businessDate());
    }

    @PostMapping("/runs")
    @PreAuthorize("hasRole('FINANCE')")
    @Operation(summary = "Run end-of-day for the business date (days > 1 fast-forwards the demo)")
    List<EodRun> run(@Valid @RequestBody(required = false) RunRequest request) {
        return service.run(request == null || request.days() == null ? 1 : request.days());
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR')")
    List<EodRun> history() {
        return service.history();
    }
}

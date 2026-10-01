package zw.insurehub.lendhub.integration;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/integration")
@Tag(name = "Integrations")
class IntegrationController {

    private final EventExternalizer externalizer;

    IntegrationController(EventExternalizer externalizer) {
        this.externalizer = externalizer;
    }

    @GetMapping("/outbound-events")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR')")
    @Operation(summary = "Last 50 events published to the group bus (demo visibility)")
    List<EventExternalizer.Sent> recent() {
        return externalizer.recent();
    }
}

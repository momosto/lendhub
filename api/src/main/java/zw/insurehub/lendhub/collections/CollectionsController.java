package zw.insurehub.lendhub.collections;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/collections")
@Tag(name = "Collections")
@PreAuthorize("hasAnyRole('COLLECTIONS','MANAGER','AUDITOR')")
class CollectionsController {

    record CompleteRequest(@Size(max = 500) String note) {
    }

    record PromiseRequest(@NotNull LocalDate promisedDate, @NotNull @DecimalMin("0.01") BigDecimal amount) {
    }

    private final CollectionsService service;

    CollectionsController(CollectionsService service) {
        this.service = service;
    }

    @GetMapping("/tasks")
    @Operation(summary = "Daily worklist, highest priority (arrears × DPD) first")
    List<CollectionTask> tasks(@RequestParam(defaultValue = "true") boolean open) {
        return service.worklist(open);
    }

    @GetMapping("/loans/{loanId}/tasks")
    List<CollectionTask> forLoan(@PathVariable UUID loanId) {
        return service.forLoan(loanId);
    }

    @GetMapping("/loans/{loanId}/promises")
    List<PromiseToPay> promises(@PathVariable UUID loanId) {
        return service.promisesFor(loanId);
    }

    @PostMapping("/tasks/{taskId}/complete")
    @PreAuthorize("hasRole('COLLECTIONS')")
    CollectionTask complete(@PathVariable UUID taskId, @Valid @RequestBody CompleteRequest r) {
        return service.complete(taskId, r.note());
    }

    @PostMapping("/tasks/{taskId}/promises")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COLLECTIONS')")
    @Operation(summary = "Record a promise to pay; a broken promise creates a follow-up task")
    PromiseToPay promise(@PathVariable UUID taskId, @Valid @RequestBody PromiseRequest r) {
        return service.promise(taskId, r.promisedDate(), r.amount());
    }
}

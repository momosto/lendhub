package zw.insurehub.lendhub.ledger;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/ledger")
@Tag(name = "Ledger")
@PreAuthorize("hasAnyRole('FINANCE','AUDITOR')")
class LedgerController {

    private final LedgerService ledger;

    LedgerController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @GetMapping("/accounts")
    List<GlAccount> accounts() {
        return ledger.chartOfAccounts();
    }

    @GetMapping("/journals")
    @Operation(summary = "Journal entries, optionally for one loan")
    List<JournalEntry> journals(@RequestParam(required = false) String loanNumber, @RequestParam(defaultValue = "100") int limit) {
        return ledger.journals(loanNumber, Math.min(limit, 500));
    }

    @GetMapping("/trial-balance")
    @Operation(summary = "Trial balance per currency; Σ debits = Σ credits")
    LedgerService.TrialBalance trialBalance() {
        return ledger.trialBalance();
    }
}

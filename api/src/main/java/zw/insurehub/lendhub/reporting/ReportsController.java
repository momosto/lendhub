package zw.insurehub.lendhub.reporting;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import zw.insurehub.lendhub.ledger.JournalEntry;
import zw.insurehub.lendhub.ledger.LedgerService;
import zw.insurehub.lendhub.loans.LoanAccount;
import zw.insurehub.lendhub.loans.LoanServicing;
import zw.insurehub.lendhub.repayments.Repayment;
import zw.insurehub.lendhub.repayments.RepaymentService;
import zw.insurehub.lendhub.shared.audit.RevisionInfo;
import zw.insurehub.lendhub.shared.money.CurrencyCode;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reports")
class ReportsController {

    record Statement(String loanNumber, String borrowerName, String customerRef, CurrencyCode currency, LoanAccount.Status status,
            Object loan, List<Repayment> repayments, List<JournalEntry> journals) {
    }

    record AuditRevision(long revision, java.time.Instant at, String user, String type, LoanAccount.Status status,
            int daysPastDue, String creditLifePolicyNumber) {
    }

    private final ReportingService reports;
    private final LedgerService ledger;
    private final LoanServicing loans;
    private final RepaymentService repayments;
    private final EntityManager em;

    ReportsController(ReportingService reports, LedgerService ledger, LoanServicing loans, RepaymentService repayments,
            EntityManager em) {
        this.reports = reports;
        this.ledger = ledger;
        this.loans = loans;
        this.repayments = repayments;
        this.em = em;
    }

    @GetMapping("/reports/par")
    @PreAuthorize("hasAnyRole('MANAGER','COMMITTEE','FINANCE','AUDITOR','COLLECTIONS')")
    @Operation(summary = "Portfolio at risk (PAR 1/30/60/90) by branch, product and officer")
    ReportingService.ParReport par(@RequestParam(defaultValue = "USD") CurrencyCode currency) {
        return reports.par(currency);
    }

    @GetMapping("/reports/portfolio")
    @PreAuthorize("hasAnyRole('MANAGER','COMMITTEE','FINANCE','AUDITOR','COLLECTIONS','OFFICER')")
    @Operation(summary = "Portfolio dashboard: GLP, disbursements MTD, collection efficiency, PAR30")
    ReportingService.Portfolio portfolio(@RequestParam(defaultValue = "USD") CurrencyCode currency) {
        return reports.portfolio(currency);
    }

    @GetMapping("/reports/trial-balance")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR')")
    LedgerService.TrialBalance trialBalance() {
        return ledger.trialBalance();
    }

    @GetMapping("/reports/regulatory-return")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR')")
    @Operation(summary = "Illustrative month-end classification and provisions return (CSV)")
    ResponseEntity<String> regulatoryReturn(@RequestParam(defaultValue = "USD") CurrencyCode currency) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=regulatory-return-" + currency + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(reports.regulatoryReturnCsv(currency));
    }

    @GetMapping("/reports/regulatory-return/lines")
    @PreAuthorize("hasAnyRole('FINANCE','AUDITOR')")
    List<ReportingService.ReturnLine> regulatoryReturnLines(@RequestParam(defaultValue = "USD") CurrencyCode currency) {
        return reports.regulatoryReturn(currency);
    }

    @GetMapping("/loans/{id}/statement")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER','COMMITTEE','FINANCE','COLLECTIONS','AUDITOR')")
    @Operation(summary = "Loan statement: schedule vs actual, repayments and allocations, journals")
    Statement statement(@PathVariable UUID id) {
        var loan = loans.getScoped(id);
        return new Statement(loan.getLoanNumber(), loan.getBorrowerName(), loan.getCustomerRef(), loan.getCurrency(),
                loan.getStatus(), Map.of("principal", loan.getPrincipal(), "outstandingPrincipal", loan.outstandingPrincipal().amount(),
                        "disbursedOn", String.valueOf(loan.getDisbursedOn()), "maturityDate", String.valueOf(loan.getMaturityDate())),
                repayments.forLoan(id), ledger.journals(loan.getLoanNumber(), 0));
    }

    @GetMapping("/audit/loans/{id}")
    @PreAuthorize("hasAnyRole('AUDITOR','COMMITTEE')")
    @Transactional(readOnly = true)
    @Operation(summary = "Envers audit trail of a loan account: every state change with its time")
    List<AuditRevision> audit(@PathVariable UUID id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = AuditReaderFactory.get(em).createQuery()
                .forRevisionsOfEntity(LoanAccount.class, false, true)
                .add(org.hibernate.envers.query.AuditEntity.id().eq(id))
                .getResultList();
        return rows.stream().map(r -> {
            var loan = (LoanAccount) r[0];
            var rev = (RevisionInfo) r[1];
            return new AuditRevision(rev.getRev(), rev.getTimestamp(), rev.getUsername(), ((RevisionType) r[2]).name(),
                    loan.getStatus(), loan.getDaysPastDue(), loan.getCreditLifePolicyNumber());
        }).toList();
    }
}

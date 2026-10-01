package zw.insurehub.lendhub.borrowers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import zw.insurehub.lendhub.shared.crypto.FieldEncryptor;
import zw.insurehub.lendhub.shared.security.CurrentUser;
import zw.insurehub.lendhub.shared.web.DomainException;

/** Public API of the borrowers module. */
@Service
@Transactional
public class BorrowerService {

    public static final String CONSENT_VERSION = "bureau-consent-v1";

    public record RegisterBorrower(String firstName, String lastName, String nationalId, String msisdn, LocalDate dateOfBirth,
            String address, Borrower.EmploymentType employmentType, String employer, BigDecimal yearsInEmployment,
            boolean bureauConsent, String branch) {
    }

    private final BorrowerRepository borrowers;
    private final LoanGroupRepository groups;
    private final FieldEncryptor encryptor;

    BorrowerService(BorrowerRepository borrowers, LoanGroupRepository groups, FieldEncryptor encryptor) {
        this.borrowers = borrowers;
        this.groups = groups;
        this.encryptor = encryptor;
    }

    public Borrower register(RegisterBorrower cmd) {
        var user = CurrentUser.get();
        String nationalId = KycRules.normaliseNationalId(cmd.nationalId());
        String msisdn = KycRules.normaliseMsisdn(cmd.msisdn());
        if (!cmd.bureauConsent()) {
            throw DomainException.rule("consent-required", "The borrower must consent to a credit bureau check");
        }
        String hash = encryptor.blindIndex(nationalId);
        if (borrowers.findByNationalIdHash(hash).isPresent()) {
            throw DomainException.conflict("duplicate-national-id", "A borrower with this national ID already exists");
        }
        String branch = user.branch() != null ? user.branch() : (cmd.branch() != null ? cmd.branch() : "HEAD_OFFICE");
        String customerRef = "CUS-%06d".formatted(borrowers.nextCustomerNumber());
        var borrower = new Borrower(customerRef, cmd.firstName().trim(), cmd.lastName().trim(), nationalId, hash, msisdn,
                cmd.dateOfBirth(), cmd.address(), branch, cmd.employmentType(), cmd.employer(),
                cmd.yearsInEmployment() == null ? BigDecimal.ZERO : cmd.yearsInEmployment(),
                Instant.now(), CONSENT_VERSION, user.username());
        return borrowers.save(borrower);
    }

    @Transactional(readOnly = true)
    public Borrower get(UUID id) {
        var borrower = borrowers.findById(id).orElseThrow(() -> DomainException.notFound("Borrower", id));
        checkBranch(borrower);
        return borrower;
    }

    /** Lookup without branch scoping, for system processes (scoring, payroll matching). */
    @Transactional(readOnly = true)
    public Optional<Borrower> find(UUID id) {
        return borrowers.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Borrower> findByCustomerRef(String customerRef) {
        return borrowers.findByCustomerRef(customerRef);
    }

    @Transactional(readOnly = true)
    public Optional<Borrower> findByNationalId(String rawNationalId) {
        return borrowers.findByNationalIdHash(encryptor.blindIndex(KycRules.normaliseNationalId(rawNationalId)));
    }

    @Transactional(readOnly = true)
    public List<Borrower> findByMsisdn(String rawMsisdn) {
        return borrowers.findByMsisdn(KycRules.normaliseMsisdn(rawMsisdn));
    }

    @Transactional(readOnly = true)
    public List<Borrower> list() {
        var user = CurrentUser.get();
        return user.seesAllBranches() ? borrowers.findAllByOrderByCreatedAtDesc()
                : borrowers.findByBranchOrderByCreatedAtDesc(user.branch());
    }

    /** Forms a solidarity group (LH-02): 5–10 registered members, one chairperson, nobody in two active groups. */
    public LoanGroup formGroup(String name, UUID chairpersonId, Set<UUID> memberIds) {
        var members = new HashSet<>(memberIds);
        members.add(chairpersonId);
        if (members.size() < 5 || members.size() > 10) {
            throw DomainException.rule("group-size", "A group needs 5 to 10 members including the chairperson");
        }
        String branch = null;
        for (UUID memberId : members) {
            var member = get(memberId);
            branch = member.getBranch();
            if (groups.countActiveGroupsWithMember(memberId) > 0) {
                throw DomainException.rule("member-in-active-group", member.getFullName() + " is already in an active group");
            }
        }
        return groups.save(new LoanGroup(name, branch, chairpersonId, members, CurrentUser.get().username()));
    }

    @Transactional(readOnly = true)
    public List<LoanGroup> groups() {
        return groups.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<LoanGroup> activeGroupOf(UUID borrowerId) {
        return groups.findActiveGroupOf(borrowerId);
    }

    private void checkBranch(Borrower borrower) {
        var user = CurrentUser.get();
        if (!user.seesAllBranches() && !borrower.getBranch().equals(user.branch())) {
            throw DomainException.notFound("Borrower", borrower.getId());
        }
    }
}

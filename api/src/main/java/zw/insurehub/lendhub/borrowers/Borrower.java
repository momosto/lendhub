package zw.insurehub.lendhub.borrowers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import zw.insurehub.lendhub.shared.crypto.EncryptedStringConverter;

/** A registered borrower. The national ID is encrypted at rest with a keyed hash for duplicate checks. */
@Entity
@Table(name = "borrower", schema = "borrowers")
@Audited
public class Borrower {

    public enum EmploymentType { SALARIED, SELF_EMPLOYED }

    @Id
    private UUID id;
    @Column(unique = true, nullable = false)
    private String customerRef;
    private String firstName;
    private String lastName;
    @Convert(converter = EncryptedStringConverter.class)
    private String nationalId;
    @Column(unique = true, nullable = false)
    private String nationalIdHash;
    private String msisdn;
    private LocalDate dateOfBirth;
    private String address;
    private String branch;
    @Enumerated(EnumType.STRING)
    private EmploymentType employmentType;
    private String employer;
    private BigDecimal yearsInEmployment;
    private Instant bureauConsentAt;
    private String bureauConsentVersion;
    private String createdBy;
    private Instant createdAt;
    @Version
    private Long version;

    protected Borrower() {
    }

    Borrower(String customerRef, String firstName, String lastName, String nationalId, String nationalIdHash, String msisdn,
            LocalDate dateOfBirth, String address, String branch, EmploymentType employmentType, String employer,
            BigDecimal yearsInEmployment, Instant consentAt, String consentVersion, String createdBy) {
        this.id = UUID.randomUUID();
        this.customerRef = customerRef;
        this.firstName = firstName;
        this.lastName = lastName;
        this.nationalId = nationalId;
        this.nationalIdHash = nationalIdHash;
        this.msisdn = msisdn;
        this.dateOfBirth = dateOfBirth;
        this.address = address;
        this.branch = branch;
        this.employmentType = employmentType;
        this.employer = employer;
        this.yearsInEmployment = yearsInEmployment;
        this.bureauConsentAt = consentAt;
        this.bureauConsentVersion = consentVersion;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCustomerRef() { return customerRef; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getFullName() { return firstName + " " + lastName; }
    public String getNationalId() { return nationalId; }
    public String getMsisdn() { return msisdn; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getAddress() { return address; }
    public String getBranch() { return branch; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public String getEmployer() { return employer; }
    public BigDecimal getYearsInEmployment() { return yearsInEmployment; }
    public Instant getBureauConsentAt() { return bureauConsentAt; }
    public String getBureauConsentVersion() { return bureauConsentVersion; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}

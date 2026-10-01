package zw.insurehub.lendhub.shared.audit;

import java.time.Instant;

import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionListener;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import zw.insurehub.lendhub.shared.security.CurrentUser;

/** Envers revision with the user who made the change, so the audit trail answers "who and when". */
@Entity
@Table(name = "revinfo", schema = "audit")
@RevisionEntity(RevisionInfo.UserListener.class)
public class RevisionInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "revinfo_seq")
    @SequenceGenerator(name = "revinfo_seq", schema = "audit", sequenceName = "revinfo_seq", allocationSize = 1)
    @RevisionNumber
    private long rev;

    @RevisionTimestamp
    private long revtstmp;

    private String username;

    public long getRev() { return rev; }
    public Instant getTimestamp() { return Instant.ofEpochMilli(revtstmp); }
    public String getUsername() { return username; }

    public static class UserListener implements RevisionListener {
        @Override
        public void newRevision(Object revisionEntity) {
            ((RevisionInfo) revisionEntity).username = CurrentUser.get().username();
        }
    }
}

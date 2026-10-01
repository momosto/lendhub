package zw.insurehub.lendhub.eod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One end-of-day run per business date (unique), with the step counts the finance team sees. */
@Entity
@Table(name = "eod_run", schema = "eod")
public class EodRun {

    public enum Status { RUNNING, COMPLETED, FAILED }

    @Id
    private UUID id;
    @Column(unique = true, nullable = false)
    private LocalDate businessDate;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String startedBy;
    private Instant startedAt;
    private Instant finishedAt;
    private Long jobExecutionId;
    private int loansProcessed;
    private int arrearsChanges;
    private int tasksCreated;
    private int remindersSent;
    private int offersExpired;
    private int promisesBroken;
    private boolean monthEnd;
    @Column(length = 2000)
    private String error;

    protected EodRun() {
    }

    EodRun(LocalDate businessDate, String startedBy) {
        this.id = UUID.randomUUID();
        this.businessDate = businessDate;
        this.status = Status.RUNNING;
        this.startedBy = startedBy;
        this.startedAt = Instant.now();
    }

    void restarted() {
        this.status = Status.RUNNING;
        this.error = null;
        this.startedAt = Instant.now();
    }

    void finished(Status status, Long jobExecutionId, String error) {
        this.status = status;
        this.jobExecutionId = jobExecutionId;
        this.error = error;
        this.finishedAt = Instant.now();
    }

    void stats(int loans, int arrearsChanges, int tasks, int reminders, int offers, int promisesBroken, boolean monthEnd) {
        this.loansProcessed = loans;
        this.arrearsChanges = arrearsChanges;
        this.tasksCreated = tasks;
        this.remindersSent = reminders;
        this.offersExpired = offers;
        this.promisesBroken = promisesBroken;
        this.monthEnd = monthEnd;
    }

    public UUID getId() { return id; }
    public LocalDate getBusinessDate() { return businessDate; }
    public Status getStatus() { return status; }
    public String getStartedBy() { return startedBy; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Long getJobExecutionId() { return jobExecutionId; }
    public int getLoansProcessed() { return loansProcessed; }
    public int getArrearsChanges() { return arrearsChanges; }
    public int getTasksCreated() { return tasksCreated; }
    public int getRemindersSent() { return remindersSent; }
    public int getOffersExpired() { return offersExpired; }
    public int getPromisesBroken() { return promisesBroken; }
    public boolean isMonthEnd() { return monthEnd; }
    public String getError() { return error; }
}

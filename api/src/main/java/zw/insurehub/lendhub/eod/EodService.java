package zw.insurehub.lendhub.eod;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import zw.insurehub.lendhub.shared.security.CurrentUser;
import zw.insurehub.lendhub.shared.web.DomainException;

/**
 * Runs the EOD job for the current business date (LH-54): one run per date, a failed run is restarted rather than
 * re-run, and the date advances only after every step succeeded.
 */
@Service
public class EodService {

    private static final Logger log = LoggerFactory.getLogger(EodService.class);

    private final EodRunRepo runs;
    private final BusinessDateService businessDates;
    private final JobLauncher launcher;
    private final Job eodJob;
    private final TransactionTemplate tx;
    private final EodJobConfig.EodStats stats;

    EodService(EodRunRepo runs, BusinessDateService businessDates, JobLauncher launcher, Job eodJob, TransactionTemplate tx,
            EodJobConfig.EodStats stats) {
        this.runs = runs;
        this.businessDates = businessDates;
        this.launcher = launcher;
        this.eodJob = eodJob;
        this.tx = tx;
        this.stats = stats;
    }

    /** Runs EOD for {@code days} consecutive business dates (the demo's "fast-forward"). Stops at the first failure. */
    public synchronized List<EodRun> run(int days) {
        if (days < 1 || days > 366) throw DomainException.rule("invalid-days", "Run between 1 and 366 days");
        List<EodRun> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            EodRun run = runOne();
            out.add(run);
            if (run.getStatus() != EodRun.Status.COMPLETED) break;
        }
        return out;
    }

    private EodRun runOne() {
        LocalDate date = businessDates.today();
        String user = CurrentUser.get().username();
        EodRun run = tx.execute(s -> {
            var existing = runs.findByBusinessDate(date);
            if (existing.isPresent()) {
                var r = existing.get();
                switch (r.getStatus()) {
                    case COMPLETED -> throw DomainException.conflict("eod-already-run", "EOD for " + date + " already completed");
                    case RUNNING -> throw DomainException.conflict("eod-running", "EOD for " + date + " is already running");
                    case FAILED -> {
                        log.info("Restarting failed EOD for {}", date);
                        r.restarted();
                        return r;
                    }
                }
            }
            try {
                return runs.saveAndFlush(new EodRun(date, user));
            } catch (DataIntegrityViolationException e) {
                throw DomainException.conflict("eod-running", "EOD for " + date + " is already running");
            }
        });

        BatchStatus status;
        Long executionId = null;
        String error = null;
        try {
            var params = new JobParametersBuilder().addLocalDate("businessDate", date).toJobParameters();
            var execution = launcher.run(eodJob, params);
            status = execution.getStatus();
            executionId = execution.getId();
            if (status != BatchStatus.COMPLETED) {
                error = execution.getAllFailureExceptions().stream().map(Throwable::getMessage).findFirst().orElse(status.name());
            }
        } catch (Exception e) {
            status = BatchStatus.FAILED;
            error = e.getMessage();
        }
        final BatchStatus finalStatus = status;
        final Long finalExecutionId = executionId;
        final String finalError = error;
        return tx.execute(s -> {
            var r = runs.findById(run.getId()).orElseThrow();
            r.stats(stats.get(date, "loans"), stats.get(date, "arrearsChanges"), stats.get(date, "tasks"),
                    stats.get(date, "reminders"), stats.get(date, "offersExpired"), stats.get(date, "promisesBroken"),
                    stats.get(date, "monthEnd") > 0);
            r.finished(finalStatus == BatchStatus.COMPLETED ? EodRun.Status.COMPLETED : EodRun.Status.FAILED,
                    finalExecutionId, finalError);
            if (finalStatus != BatchStatus.COMPLETED) log.error("EOD for {} failed: {}", date, finalError);
            return r;
        });
    }

    public LocalDate businessDate() {
        return businessDates.today();
    }

    public List<EodRun> history() {
        return runs.findTop60ByOrderByBusinessDateDesc();
    }
}

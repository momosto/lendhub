package zw.insurehub.lendhub.eod;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import zw.insurehub.lendhub.collections.CollectionsService;
import zw.insurehub.lendhub.ledger.LedgerService;
import zw.insurehub.lendhub.loans.LoanServicing;
import zw.insurehub.lendhub.origination.ApplicationService;
import zw.insurehub.lendhub.provisioning.ProvisioningService;

/**
 * End-of-day job (docs/03-architecture.md §6). Loan-level steps are chunk-oriented over loan IDs (paged reader,
 * one transaction per chunk), so a failed run restarts from the last committed chunk; finished steps are skipped.
 */
@Configuration
class EodJobConfig {

    static final String JOB_NAME = "eodJob";
    static final int CHUNK = 100;

    /** Per-date counters shown on the EOD screen. */
    static class EodStats {
        final Map<LocalDate, Map<String, AtomicInteger>> byDate = new ConcurrentHashMap<>();

        void add(LocalDate date, String key, int n) {
            byDate.computeIfAbsent(date, d -> new ConcurrentHashMap<>()).computeIfAbsent(key, k -> new AtomicInteger()).addAndGet(n);
        }

        int get(LocalDate date, String key) {
            var m = byDate.get(date);
            return m == null || !m.containsKey(key) ? 0 : m.get(key).get();
        }
    }

    /** Test hook to prove restartability: fails the named step once. Never armed in production. */
    static class FaultInjector {
        private volatile String failOnce;

        void failOnce(String step) {
            this.failOnce = step;
        }

        void check(String step) {
            if (step.equals(failOnce)) {
                failOnce = null;
                throw new IllegalStateException("Injected failure in step " + step);
            }
        }
    }

    @Bean
    EodStats eodStats() {
        return new EodStats();
    }

    @Bean
    FaultInjector eodFaultInjector() {
        return new FaultInjector();
    }

    @Bean
    @StepScope
    JdbcPagingItemReader<UUID> servicingLoanReader(DataSource dataSource) {
        return new JdbcPagingItemReaderBuilder<UUID>()
                .name("servicingLoans")
                .dataSource(dataSource)
                .selectClause("select id")
                .fromClause("from loans.loan_account")
                .whereClause("where status in ('ACTIVE', 'IN_ARREARS', 'RESTRUCTURED')")
                .sortKeys(Map.of("id", Order.ASCENDING))
                .rowMapper((rs, i) -> rs.getObject(1, UUID.class))
                .pageSize(500)
                .build();
    }

    @Bean
    @StepScope
    ItemWriter<UUID> accrualWriter(LoanServicing loans, EodStats stats, FaultInjector faults,
            @Value("#{jobParameters['businessDate']}") LocalDate date) {
        return chunk -> {
            faults.check("accrual");
            for (UUID id : chunk) loans.accrue(id, date);
            stats.add(date, "loans", chunk.size());
        };
    }

    @Bean
    @StepScope
    ItemWriter<UUID> penaltyWriter(LoanServicing loans, FaultInjector faults,
            @Value("#{jobParameters['businessDate']}") LocalDate date) {
        return chunk -> {
            faults.check("penalties");
            for (UUID id : chunk) loans.chargePenalty(id, date);
        };
    }

    @Bean
    @StepScope
    ItemWriter<UUID> ageingWriter(LoanServicing loans, CollectionsService collections, EodStats stats,
            @Value("#{jobParameters['businessDate']}") LocalDate date) {
        return chunk -> {
            for (UUID id : chunk) {
                var before = loans.get(id).getArrearsBucket();
                var result = loans.applyCreditAndAge(id, date);
                if (loans.get(id).getArrearsBucket() != before) stats.add(date, "arrearsChanges", 1);
                if (collections.onAgeing(result, date).isPresent()) stats.add(date, "tasks", 1);
            }
        };
    }

    private Step loanStep(String name, JobRepository repo, PlatformTransactionManager tx, JdbcPagingItemReader<UUID> reader,
            ItemWriter<UUID> writer) {
        return new StepBuilder(name, repo).<UUID, UUID>chunk(CHUNK, tx).reader(reader).writer(writer).build();
    }

    @Bean
    Step accrualStep(JobRepository repo, PlatformTransactionManager tx, JdbcPagingItemReader<UUID> servicingLoanReader,
            ItemWriter<UUID> accrualWriter) {
        return loanStep("accrueInterest", repo, tx, servicingLoanReader, accrualWriter);
    }

    @Bean
    Step penaltyStep(JobRepository repo, PlatformTransactionManager tx, JdbcPagingItemReader<UUID> servicingLoanReader,
            ItemWriter<UUID> penaltyWriter) {
        return loanStep("applyPenalties", repo, tx, servicingLoanReader, penaltyWriter);
    }

    @Bean
    Step ageingStep(JobRepository repo, PlatformTransactionManager tx, JdbcPagingItemReader<UUID> servicingLoanReader,
            ItemWriter<UUID> ageingWriter) {
        return loanStep("ageArrearsAndCollections", repo, tx, servicingLoanReader, ageingWriter);
    }

    @Bean
    Step housekeepingStep(JobRepository repo, PlatformTransactionManager tx, LoanServicing loans,
            CollectionsService collections, ApplicationService applications, EodStats stats) {
        return new StepBuilder("remindersPromisesOffers", repo).tasklet((contribution, ctx) -> {
            LocalDate date = (LocalDate) ctx.getStepContext().getJobParameters().get("businessDate");
            stats.add(date, "reminders", loans.sendReminders(date));
            stats.add(date, "promisesBroken", collections.reviewPromises(date));
            stats.add(date, "offersExpired", applications.expireOffers());
            return RepeatStatus.FINISHED;
        }, tx).build();
    }

    @Bean
    Step provisioningStep(JobRepository repo, PlatformTransactionManager tx, ProvisioningService provisioning, EodStats stats) {
        return new StepBuilder("monthEndProvisioning", repo).tasklet((contribution, ctx) -> {
            LocalDate date = (LocalDate) ctx.getStepContext().getJobParameters().get("businessDate");
            if (date.equals(date.with(TemporalAdjusters.lastDayOfMonth()))) {
                provisioning.run(date);
                stats.add(date, "monthEnd", 1);
            }
            return RepeatStatus.FINISHED;
        }, tx).build();
    }

    @Bean
    Step trialBalanceStep(JobRepository repo, PlatformTransactionManager tx, LedgerService ledger) {
        return new StepBuilder("trialBalanceCheck", repo).tasklet((contribution, ctx) -> {
            if (!ledger.trialBalance().balanced()) {
                throw new IllegalStateException("Trial balance does not balance; business date not advanced");
            }
            return RepeatStatus.FINISHED;
        }, tx).build();
    }

    @Bean
    Step advanceDateStep(JobRepository repo, PlatformTransactionManager tx, BusinessDateService businessDates) {
        return new StepBuilder("advanceBusinessDate", repo).tasklet((contribution, ctx) -> {
            LocalDate date = (LocalDate) ctx.getStepContext().getJobParameters().get("businessDate");
            businessDates.advanceFrom(date);
            return RepeatStatus.FINISHED;
        }, tx).build();
    }

    @Bean
    Job eodJob(JobRepository repo, Step accrualStep, Step penaltyStep, Step ageingStep, Step housekeepingStep,
            Step provisioningStep, Step trialBalanceStep, Step advanceDateStep) {
        return new JobBuilder(JOB_NAME, repo)
                .start(accrualStep)
                .next(penaltyStep)
                .next(ageingStep)
                .next(housekeepingStep)
                .next(provisioningStep)
                .next(trialBalanceStep)
                .next(advanceDateStep)
                .build();
    }
}

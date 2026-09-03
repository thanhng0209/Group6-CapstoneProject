package com.uwa.printerfarm.job;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobLifecycleServiceTest {

    private final JobLifecycleService lifecycleService = new JobLifecycleService(5);

    private Job newQueuedJob() {
        return new Job("22345678", "prusa-xl-1", "PLA", new BigDecimal("50"), new BigDecimal("120"));
    }

    @Test
    void allowsQueuedToPrintingTransition() {
        Job job = newQueuedJob();

        lifecycleService.transition(job, JobStatus.PRINTING);

        assertThat(job.getStatus()).isEqualTo(JobStatus.PRINTING);
    }

    @Test
    void allowsPrintingToCompletedAndStampsCompletedAt() {
        Job job = newQueuedJob();
        lifecycleService.transition(job, JobStatus.PRINTING);

        lifecycleService.transition(job, JobStatus.COMPLETED);

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    void rejectsTransitionFromTerminalState() {
        Job job = newQueuedJob();
        lifecycleService.transition(job, JobStatus.PRINTING);
        lifecycleService.transition(job, JobStatus.COMPLETED);

        assertThatThrownBy(() -> lifecycleService.transition(job, JobStatus.PRINTING))
                .isInstanceOf(InvalidJobStatusTransitionException.class);
    }

    @Test
    void rejectsSkippingQueuedStraightToCompleted() {
        Job job = newQueuedJob();

        assertThatThrownBy(() -> lifecycleService.transition(job, JobStatus.COMPLETED))
                .isInstanceOf(InvalidJobStatusTransitionException.class);
    }

    @Test
    void cancellingWithinWindowAutoRefunds() {
        Job job = newQueuedJob();

        RefundDecision decision = lifecycleService.cancel(job, job.getQueuedAt().plusSeconds(60));

        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(decision).isEqualTo(RefundDecision.AUTO_REFUNDED);
    }

    @Test
    void cancellingOutsideWindowRequiresApproval() {
        Job job = newQueuedJob();

        RefundDecision decision = lifecycleService.cancel(job, job.getQueuedAt().plus(java.time.Duration.ofMinutes(10)));

        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(decision).isEqualTo(RefundDecision.REQUIRES_APPROVAL);
    }

    @Test
    void cannotCancelAJobThatIsAlreadyPrinting() {
        Job job = newQueuedJob();
        lifecycleService.transition(job, JobStatus.PRINTING);

        assertThatThrownBy(() -> lifecycleService.cancel(job, Instant.now()))
                .isInstanceOf(InvalidJobStatusTransitionException.class);
    }
}

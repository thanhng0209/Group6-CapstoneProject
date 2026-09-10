package com.uwa.printerfarm.job;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Enforces the queued/printing/completed/failed/cancelled job state machine
 * from the MVP acceptance criteria (Scenarios 1, 4, 5, 6).
 */
@Service
public class JobLifecycleService {

    private static final Map<JobStatus, Set<JobStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(JobStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(JobStatus.QUEUED, EnumSet.of(JobStatus.PRINTING, JobStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(JobStatus.PRINTING, EnumSet.of(JobStatus.COMPLETED, JobStatus.FAILED));
        ALLOWED_TRANSITIONS.put(JobStatus.COMPLETED, EnumSet.noneOf(JobStatus.class));
        ALLOWED_TRANSITIONS.put(JobStatus.FAILED, EnumSet.noneOf(JobStatus.class));
        ALLOWED_TRANSITIONS.put(JobStatus.CANCELLED, EnumSet.noneOf(JobStatus.class));
    }

    // Placeholder pending client confirmation of the exact cancellation window (client agreement table
    // marks cancellation/refund as "Must confirm").
    private final Duration cancellationWindow;

    public JobLifecycleService(@Value("${printerfarm.job.cancellation-window-minutes:5}") long cancellationWindowMinutes) {
        this.cancellationWindow = Duration.ofMinutes(cancellationWindowMinutes);
    }

    public void transition(Job job, JobStatus target) {
        JobStatus current = job.getStatus();
        Set<JobStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidJobStatusTransitionException(current, target);
        }
        job.setStatus(target);
        if (target == JobStatus.COMPLETED || target == JobStatus.FAILED) {
            job.setCompletedAt(Instant.now());
        }
    }

    /**
     * Cancels a queued job and decides whether the refund is automatic or needs
     * secondary approval, based on how long the job has been queued.
     */
    public RefundDecision cancel(Job job, Instant now) {
        transition(job, JobStatus.CANCELLED);

        Duration waited = Duration.between(job.getQueuedAt(), now);
        return waited.compareTo(cancellationWindow) <= 0
                ? RefundDecision.AUTO_REFUNDED
                : RefundDecision.REQUIRES_APPROVAL;
    }
}

package com.uwa.printerfarm.job;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A refund that fell outside the auto-refund cancellation window and is
 * awaiting farm-manager approval (MVP acceptance criteria: out-of-window
 * cancellations must be routed to a second-approval workflow rather than
 * refunded directly).
 */
public class RefundRequest {

    private final long id;
    private final Long jobId;
    private final String ownerUniId;
    private final BigDecimal amount;
    private final Instant requestedAt;
    private RefundStatus status = RefundStatus.PENDING_APPROVAL;
    private Instant decidedAt;

    RefundRequest(long id, Long jobId, String ownerUniId, BigDecimal amount, Instant requestedAt) {
        this.id = id;
        this.jobId = jobId;
        this.ownerUniId = ownerUniId;
        this.amount = amount;
        this.requestedAt = requestedAt;
    }

    public long getId() {
        return id;
    }

    public Long getJobId() {
        return jobId;
    }

    public String getOwnerUniId() {
        return ownerUniId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    void approve(Instant now) {
        requirePending();
        status = RefundStatus.APPROVED;
        decidedAt = now;
    }

    void reject(Instant now) {
        requirePending();
        status = RefundStatus.REJECTED;
        decidedAt = now;
    }

    private void requirePending() {
        if (status != RefundStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Refund request " + id + " has already been decided: " + status);
        }
    }
}

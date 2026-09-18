package com.uwa.printerfarm.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A refund that fell outside the auto-refund cancellation window and is
 * awaiting farm-manager approval.
 * Mapped to the 'refund_requests' table in PostgreSQL.
 */
@Entity
@Table(name = "refund_requests")
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "owner_uni_id", nullable = false, length = 20)
    private String ownerUniId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundStatus status = RefundStatus.PENDING_APPROVAL;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected RefundRequest() {
        // required by JPA
    }

    public RefundRequest(Long jobId, String ownerUniId, BigDecimal amount, Instant requestedAt) {
        this.jobId = jobId;
        this.ownerUniId = ownerUniId;
        this.amount = amount;
        this.requestedAt = requestedAt != null ? requestedAt : Instant.now();
        this.status = RefundStatus.PENDING_APPROVAL;
    }

    public RefundRequest(Long id, Long jobId, String ownerUniId, BigDecimal amount, Instant requestedAt) {
        this(jobId, ownerUniId, amount, requestedAt);
        this.id = id;
    }

    public Long getId() {
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

    public void approve(Instant now) {
        requirePending();
        status = RefundStatus.APPROVED;
        decidedAt = now;
    }

    public void reject(Instant now) {
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

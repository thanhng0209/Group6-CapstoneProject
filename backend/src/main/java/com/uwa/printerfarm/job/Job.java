package com.uwa.printerfarm.job;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ownerUniId;
    private String printerId;
    private String material;
    private BigDecimal estimatedGrams;
    private BigDecimal estimatedMinutes;
    private BigDecimal cost;

    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.QUEUED;

    private Instant queuedAt = Instant.now();
    private Instant completedAt;

    protected Job() {
        // required by JPA
    }

    public Job(String ownerUniId, String printerId, String material,
               BigDecimal estimatedGrams, BigDecimal estimatedMinutes) {
        this.ownerUniId = ownerUniId;
        this.printerId = printerId;
        this.material = material;
        this.estimatedGrams = estimatedGrams;
        this.estimatedMinutes = estimatedMinutes;
    }

    public Long getId() {
        return id;
    }

    public String getOwnerUniId() {
        return ownerUniId;
    }

    public String getPrinterId() {
        return printerId;
    }

    public String getMaterial() {
        return material;
    }

    public BigDecimal getEstimatedGrams() {
        return estimatedGrams;
    }

    public BigDecimal getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public JobStatus getStatus() {
        return status;
    }

    void setStatus(JobStatus status) {
        this.status = status;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}

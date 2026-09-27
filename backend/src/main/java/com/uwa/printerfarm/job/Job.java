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

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_uni_id", nullable = false, length = 20)
    private String ownerUniId;

    @Column(name = "printer_id", nullable = false, length = 50)
    private String printerId;

    @Column(name = "file_name")
    private String fileName;
    @Column(nullable = false, length = 30)
    private String colour = "Unknown";

    @Column(nullable = false, length = 30)
    private String material;

    @Column(name = "estimated_grams", nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedGrams = BigDecimal.ZERO;

    @Column(name = "estimated_minutes", nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedMinutes = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cost = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobStatus status = JobStatus.QUEUED;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Job() {
        // required by JPA
    }

    public Job(String ownerUniId, String printerId, String material,
               BigDecimal estimatedGrams, BigDecimal estimatedMinutes) {
        this(ownerUniId, printerId, null, material, estimatedGrams, estimatedMinutes);
    }

    public Job(String ownerUniId, String printerId, String fileName, String material,
               BigDecimal estimatedGrams, BigDecimal estimatedMinutes) {
        this.ownerUniId = ownerUniId;
        this.printerId = printerId;
        this.fileName = fileName;
        this.material = material;
        this.estimatedGrams = estimatedGrams != null ? estimatedGrams : BigDecimal.ZERO;
        this.estimatedMinutes = estimatedMinutes != null ? estimatedMinutes : BigDecimal.ZERO;
        this.colour = "Unknown";
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

    public void setPrinterId(String printerId) {
        this.printerId = printerId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMaterial() {
        return material;
    }

    public String getColour() {
        return colour;
    }

    public void setColour(String colour) {
        this.colour = colour != null && !colour.isBlank() ? colour : "Unknown";
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

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "paused_duration_seconds", nullable = false)
    private long pausedDurationSeconds;

    @Column(name = "progress_percent", nullable = false)
    private int progressPercent;

    @Column(name = "remaining_seconds", nullable = false)
    private long remainingSeconds;

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public void setPausedAt(Instant pausedAt) {
        this.pausedAt = pausedAt;
    }

    public long getPausedDurationSeconds() {
        return pausedDurationSeconds;
    }

    public void setPausedDurationSeconds(long pausedDurationSeconds) {
        this.pausedDurationSeconds = Math.max(0, pausedDurationSeconds);
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = Math.max(0, Math.min(100, progressPercent));
    }

    public long getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(long remainingSeconds) {
        this.remainingSeconds = Math.max(0, remainingSeconds);
    }

    public void assignId(long id) {
        this.id = id;
    }
}

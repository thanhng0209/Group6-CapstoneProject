package com.uwa.printerfarm.wallet;

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
 * A single, permanent ledger entry.
 * Mapped to the 'transactions' table in PostgreSQL.
 * Immutable transaction history: corrections must be made by recording
 * a new, offsetting transaction rather than mutating existing entries.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_uni_id", nullable = false, length = 20)
    private String ownerUniId;

    @Column(name = "job_id")
    private Long jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(columnDefinition = "TEXT")
    private String description;

    protected Transaction() {
        // required by JPA
    }

    public Transaction(String ownerUniId, Long jobId, TransactionType type,
                       BigDecimal amount, BigDecimal balanceAfter, Instant occurredAt, String description) {
        this.ownerUniId = ownerUniId;
        this.jobId = jobId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.occurredAt = occurredAt != null ? occurredAt : Instant.now();
        this.description = description;
    }

    public Transaction(Long id, String ownerUniId, Long jobId, TransactionType type,
                       BigDecimal amount, BigDecimal balanceAfter, Instant occurredAt, String description) {
        this(ownerUniId, jobId, type, amount, balanceAfter, occurredAt, description);
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getOwnerUniId() {
        return ownerUniId;
    }

    public Long getJobId() {
        return jobId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getDescription() {
        return description;
    }
}

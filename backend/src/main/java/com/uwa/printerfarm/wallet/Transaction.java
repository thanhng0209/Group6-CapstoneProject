package com.uwa.printerfarm.wallet;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single, permanent ledger entry. There is no setter and no way to edit or
 * delete a transaction after it is recorded: the MVP acceptance criteria
 * require an immutable transaction history, so corrections must be made by
 * recording a new, offsetting transaction rather than changing this one.
 */
public final class Transaction {

    private final long id;
    private final String ownerUniId;
    private final Long jobId;
    private final TransactionType type;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final Instant occurredAt;
    private final String description;

    Transaction(long id, String ownerUniId, Long jobId, TransactionType type,
                BigDecimal amount, BigDecimal balanceAfter, Instant occurredAt, String description) {
        this.id = id;
        this.ownerUniId = ownerUniId;
        this.jobId = jobId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.occurredAt = occurredAt;
        this.description = description;
    }

    public long getId() {
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

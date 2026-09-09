package com.uwa.printerfarm.wallet;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Append-only ledger of wallet transactions. Deliberately exposes no update or
 * delete operation: the MVP requires an immutable transaction history, so once
 * a transaction is recorded here it is permanent.
 */
@Service
public class TransactionLedgerService {

    private final AtomicLong sequence = new AtomicLong();
    private final List<Transaction> transactions = new CopyOnWriteArrayList<>();

    public Transaction record(String ownerUniId, Long jobId, TransactionType type,
                               BigDecimal amount, BigDecimal balanceAfter, String description) {
        Transaction transaction = new Transaction(sequence.incrementAndGet(), ownerUniId, jobId, type,
                amount, balanceAfter, Instant.now(), description);
        transactions.add(transaction);
        return transaction;
    }

    public List<Transaction> history(String ownerUniId) {
        return transactions.stream()
                .filter(transaction -> transaction.getOwnerUniId().equals(ownerUniId))
                .collect(Collectors.toUnmodifiableList());
    }

    public List<Transaction> all() {
        return Collections.unmodifiableList(transactions);
    }
}

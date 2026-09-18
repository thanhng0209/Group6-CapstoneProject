package com.uwa.printerfarm.wallet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Append-only ledger of wallet transactions backed by PostgreSQL.
 * Deliberately exposes no update or delete operations:
 * once a transaction is recorded here, it is permanent.
 */
@Service
public class TransactionLedgerService {

    private final TransactionRepository transactionRepository;

    public TransactionLedgerService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Transaction record(String ownerUniId, Long jobId, TransactionType type,
                               BigDecimal amount, BigDecimal balanceAfter, String description) {
        Transaction transaction = new Transaction(
                ownerUniId, jobId, type, amount, balanceAfter, Instant.now(), description
        );
        return transactionRepository.save(transaction);
    }

    public List<Transaction> history(String ownerUniId) {
        return transactionRepository.findByOwnerUniIdOrderByOccurredAtDesc(ownerUniId);
    }

    public List<Transaction> all() {
        return transactionRepository.findAllByOrderByOccurredAtDesc();
    }
}

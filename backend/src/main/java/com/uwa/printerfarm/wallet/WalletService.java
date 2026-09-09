package com.uwa.printerfarm.wallet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tracks per-student balances in memory. Placeholder until the team wires up
 * real account persistence and a top-up mechanism (both still unconfirmed in
 * the client agreement); every unseen student starts with a configurable
 * placeholder balance so job submission can be exercised end-to-end.
 */
@Service
public class WalletService {

    private final ConcurrentHashMap<String, BigDecimal> balances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final BigDecimal defaultStartingBalance;

    public WalletService(@Value("${printerfarm.wallet.default-starting-balance:50.00}") BigDecimal defaultStartingBalance) {
        this.defaultStartingBalance = defaultStartingBalance;
    }

    public BigDecimal getBalance(String ownerUniId) {
        return balances.computeIfAbsent(ownerUniId, id -> defaultStartingBalance);
    }

    public BigDecimal debit(String ownerUniId, BigDecimal amount) {
        ReentrantLock lock = lockFor(ownerUniId);
        lock.lock();
        try {
            BigDecimal current = getBalance(ownerUniId);
            if (current.compareTo(amount) < 0) {
                throw new InsufficientBalanceException(ownerUniId, current, amount);
            }
            BigDecimal updated = current.subtract(amount);
            balances.put(ownerUniId, updated);
            return updated;
        } finally {
            lock.unlock();
        }
    }

    public BigDecimal credit(String ownerUniId, BigDecimal amount) {
        ReentrantLock lock = lockFor(ownerUniId);
        lock.lock();
        try {
            BigDecimal updated = getBalance(ownerUniId).add(amount);
            balances.put(ownerUniId, updated);
            return updated;
        } finally {
            lock.unlock();
        }
    }

    private ReentrantLock lockFor(String ownerUniId) {
        return locks.computeIfAbsent(ownerUniId, id -> new ReentrantLock());
    }
}

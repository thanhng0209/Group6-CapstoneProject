package com.uwa.printerfarm.wallet;

import com.uwa.printerfarm.enums.Role;
import com.uwa.printerfarm.model.User;
import com.uwa.printerfarm.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that concurrent debit/credit calls against the same wallet do not lose updates.
 * The test itself must not run inside a transaction: each worker thread needs its own
 * committed transaction, otherwise the row lock would never be contended.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({WalletService.class, TransactionLedgerService.class})
class WalletConcurrencyIntegrationTest {

    private static final String TEST_UNI_ID = "55667788";
    private static final int THREADS = 8;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletService walletService;

    @BeforeEach
    void setUp() {
        userRepository.save(User.builder()
                .uniId(TEST_UNI_ID)
                .email("55667788@student.uwa.edu.au")
                .fullName("Concurrency Test Student")
                .passwordHash("hashed")
                .role(Role.STUDENT)
                .balanceCents(10_000L) // $100.00
                .build());
    }

    @AfterEach
    void tearDown() {
        userRepository.findByUniId(TEST_UNI_ID).ifPresent(userRepository::delete);
    }

    @Test
    void concurrentDebitsAreAllAppliedWithoutLostUpdates() throws Exception {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> walletService.debit(TEST_UNI_ID, new BigDecimal("5.00")));
        }

        runConcurrently(tasks);

        // 8 x $5.00 = $40.00 must come off $100.00; a lost update would leave more than $60.00.
        assertThat(userRepository.findByUniId(TEST_UNI_ID).orElseThrow().getBalanceCents())
                .isEqualTo(6_000L);
    }

    @Test
    void concurrentDebitAndCreditBothTakeEffect() throws Exception {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS / 2; i++) {
            tasks.add(() -> walletService.debit(TEST_UNI_ID, new BigDecimal("10.00")));
            tasks.add(() -> walletService.credit(TEST_UNI_ID, new BigDecimal("2.50")));
        }

        runConcurrently(tasks);

        // 4 x -$10.00 + 4 x +$2.50 = -$30.00
        assertThat(userRepository.findByUniId(TEST_UNI_ID).orElseThrow().getBalanceCents())
                .isEqualTo(7_000L);
    }

    @Test
    void concurrentDebitsCannotOverdrawTheWallet() throws Exception {
        // $100.00 balance, 8 debits of $20.00 each: exactly 5 may succeed.
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> walletService.debit(TEST_UNI_ID, new BigDecimal("20.00")));
        }

        List<Future<Object>> results = runConcurrently(tasks);

        long succeeded = results.stream().filter(f -> {
            try {
                f.get();
                return true;
            } catch (Exception e) {
                assertThat(e.getCause()).isInstanceOf(InsufficientBalanceException.class);
                return false;
            }
        }).count();

        assertThat(succeeded).isEqualTo(5);
        assertThat(userRepository.findByUniId(TEST_UNI_ID).orElseThrow().getBalanceCents())
                .isZero();
    }

    private List<Future<Object>> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return task.call();
                }));
            }
            startGate.countDown();
            for (Future<Object> future : futures) {
                try {
                    future.get();
                } catch (Exception ignored) {
                    // Individual outcomes are inspected by the caller where they matter.
                }
            }
            return futures;
        } finally {
            pool.shutdownNow();
        }
    }
}

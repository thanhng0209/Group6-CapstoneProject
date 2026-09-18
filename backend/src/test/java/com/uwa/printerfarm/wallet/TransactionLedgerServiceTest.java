package com.uwa.printerfarm.wallet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionLedgerServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    private TransactionLedgerService ledgerService;

    @BeforeEach
    void setUp() {
        ledgerService = new TransactionLedgerService(transactionRepository);
    }

    @Test
    void recordsTransactionWithSuppliedDetails() {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction transaction = ledgerService.record("22345678", 1L, TransactionType.DEBIT,
                new BigDecimal("6.20"), new BigDecimal("43.80"), "Job submission charge");

        assertThat(transaction.getOwnerUniId()).isEqualTo("22345678");
        assertThat(transaction.getJobId()).isEqualTo(1L);
        assertThat(transaction.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(transaction.getAmount()).isEqualByComparingTo("6.20");
        assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("43.80");
        assertThat(transaction.getOccurredAt()).isNotNull();
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void historyDelegatesToRepository() {
        Transaction tx = new Transaction("22345678", 1L, TransactionType.DEBIT,
                new BigDecimal("6.20"), new BigDecimal("43.80"), Instant.now(), "a");
        when(transactionRepository.findByOwnerUniIdOrderByOccurredAtDesc("22345678")).thenReturn(List.of(tx));

        List<Transaction> history = ledgerService.history("22345678");

        assertThat(history).containsExactly(tx);
        verify(transactionRepository).findByOwnerUniIdOrderByOccurredAtDesc("22345678");
    }

    @Test
    void allDelegatesToRepository() {
        Transaction tx = new Transaction("22345678", 1L, TransactionType.DEBIT,
                new BigDecimal("6.20"), new BigDecimal("43.80"), Instant.now(), "a");
        when(transactionRepository.findAllByOrderByOccurredAtDesc()).thenReturn(List.of(tx));

        List<Transaction> all = ledgerService.all();

        assertThat(all).containsExactly(tx);
        verify(transactionRepository).findAllByOrderByOccurredAtDesc();
    }
}

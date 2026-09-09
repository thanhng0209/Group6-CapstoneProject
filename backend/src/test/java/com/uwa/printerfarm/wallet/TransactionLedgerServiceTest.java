package com.uwa.printerfarm.wallet;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionLedgerServiceTest {

    private final TransactionLedgerService ledgerService = new TransactionLedgerService();

    @Test
    void recordsTransactionWithSuppliedDetails() {
        Transaction transaction = ledgerService.record("22345678", 1L, TransactionType.DEBIT,
                new BigDecimal("6.20"), new BigDecimal("43.80"), "Job submission charge");

        assertThat(transaction.getOwnerUniId()).isEqualTo("22345678");
        assertThat(transaction.getJobId()).isEqualTo(1L);
        assertThat(transaction.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(transaction.getAmount()).isEqualByComparingTo("6.20");
        assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("43.80");
        assertThat(transaction.getOccurredAt()).isNotNull();
    }

    @Test
    void historyOnlyReturnsTransactionsForThatOwner() {
        ledgerService.record("22345678", 1L, TransactionType.DEBIT, new BigDecimal("6.20"), new BigDecimal("43.80"), "a");
        ledgerService.record("99999999", 2L, TransactionType.DEBIT, new BigDecimal("3.00"), new BigDecimal("47.00"), "b");

        List<Transaction> history = ledgerService.history("22345678");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getOwnerUniId()).isEqualTo("22345678");
    }

    @Test
    void historyIsImmutable() {
        ledgerService.record("22345678", 1L, TransactionType.DEBIT, new BigDecimal("6.20"), new BigDecimal("43.80"), "a");

        List<Transaction> history = ledgerService.history("22345678");

        assertThatThrownBy(() -> history.add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}

package com.uwa.printerfarm.wallet;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletServiceTest {

    private final WalletService walletService = new WalletService(new BigDecimal("50.00"));

    @Test
    void newStudentStartsWithDefaultBalance() {
        assertThat(walletService.getBalance("22345678")).isEqualByComparingTo("50.00");
    }

    @Test
    void debitReducesBalance() {
        BigDecimal balanceAfter = walletService.debit("22345678", new BigDecimal("6.20"));

        assertThat(balanceAfter).isEqualByComparingTo("43.80");
        assertThat(walletService.getBalance("22345678")).isEqualByComparingTo("43.80");
    }

    @Test
    void creditIncreasesBalance() {
        BigDecimal balanceAfter = walletService.credit("22345678", new BigDecimal("10.00"));

        assertThat(balanceAfter).isEqualByComparingTo("60.00");
    }

    @Test
    void debitBeyondBalanceThrowsAndLeavesBalanceUnchanged() {
        assertThatThrownBy(() -> walletService.debit("22345678", new BigDecimal("999.00")))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(walletService.getBalance("22345678")).isEqualByComparingTo("50.00");
    }
}

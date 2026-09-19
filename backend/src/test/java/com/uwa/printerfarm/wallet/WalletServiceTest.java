package com.uwa.printerfarm.wallet;

import com.uwa.printerfarm.enums.Role;
import com.uwa.printerfarm.model.User;
import com.uwa.printerfarm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionLedgerService ledgerService;

    private WalletService walletService;
    private User testUser;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(userRepository, ledgerService, new BigDecimal("50.00"));
        testUser = User.builder()
                .uniId("22345678")
                .balanceCents(5000L) // $50.00
                .email("22345678@student.uwa.edu.au")
                .fullName("Test Student")
                .role(Role.STUDENT)
                .build();
    }

    @Test
    void studentReturnsExistingBalance() {
        when(userRepository.findByUniId("22345678")).thenReturn(Optional.of(testUser));

        assertThat(walletService.getBalance("22345678")).isEqualByComparingTo("50.00");
    }

    @Test
    void newStudentAutoProvisionsWithDefaultBalance() {
        when(userRepository.findByUniId("99999999")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal balance = walletService.getBalance("99999999");

        assertThat(balance).isEqualByComparingTo("50.00");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getBalanceCents()).isEqualTo(5000L);
    }

    @Test
    void debitReducesBalanceAndSavesToDatabase() {
        when(userRepository.findByUniId("22345678")).thenReturn(Optional.of(testUser));

        BigDecimal balanceAfter = walletService.debit("22345678", new BigDecimal("6.20"));

        assertThat(balanceAfter).isEqualByComparingTo("43.80");
        assertThat(testUser.getBalanceCents()).isEqualTo(4380L);
        verify(userRepository).save(testUser);
    }

    @Test
    void creditIncreasesBalanceAndSavesToDatabase() {
        when(userRepository.findByUniId("22345678")).thenReturn(Optional.of(testUser));

        BigDecimal balanceAfter = walletService.credit("22345678", new BigDecimal("10.00"));

        assertThat(balanceAfter).isEqualByComparingTo("60.00");
        assertThat(testUser.getBalanceCents()).isEqualTo(6000L);
        verify(userRepository).save(testUser);
        verify(ledgerService).record(eq("22345678"), isNull(), eq(TransactionType.TOPUP),
                eq(new BigDecimal("10.00")), eq(new BigDecimal("60.00")), eq("Wallet balance top-up"));
    }

    @Test
    void debitBeyondBalanceThrowsAndLeavesBalanceUnchanged() {
        when(userRepository.findByUniId("22345678")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> walletService.debit("22345678", new BigDecimal("999.00")))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(testUser.getBalanceCents()).isEqualTo(5000L);
    }
}

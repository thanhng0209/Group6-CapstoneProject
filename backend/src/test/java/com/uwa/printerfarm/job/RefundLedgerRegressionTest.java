package com.uwa.printerfarm.job;

import com.uwa.printerfarm.model.User;
import com.uwa.printerfarm.repository.UserRepository;
import com.uwa.printerfarm.wallet.TransactionLedgerService;
import com.uwa.printerfarm.wallet.TransactionType;
import com.uwa.printerfarm.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the double ledger entry bug: a refund used to credit the wallet through
 * the generic credit path (recording a TOPUP) and then record a second REFUND entry.
 * These tests wire a real {@link WalletService} behind {@link RefundService} so the whole
 * refund path is exercised, rather than only verifying calls on a mocked wallet.
 */
@ExtendWith(MockitoExtension.class)
class RefundLedgerRegressionTest {

    private static final String UNI_ID = "22345678";
    private static final long JOB_ID = 42L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionLedgerService ledgerService;

    @Mock
    private RefundRequestRepository refundRequestRepository;

    private User user;
    private RefundService refundService;

    @BeforeEach
    void setUp() {
        user = User.builder().uniId(UNI_ID).balanceCents(5000L).build();
        lenient().when(userRepository.findByUniIdForUpdate(UNI_ID)).thenReturn(Optional.of(user));
        lenient().when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        WalletService walletService = new WalletService(userRepository, ledgerService, new BigDecimal("50.00"));
        refundService = new RefundService(new JobLifecycleService(5), walletService, refundRequestRepository);
    }

    private Job queuedJob(BigDecimal cost) {
        Job job = new Job(UNI_ID, "prusa-xl-1", "PLA", new BigDecimal("50"), new BigDecimal("120"));
        job.setCost(cost);
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        return job;
    }

    @Test
    void autoRefundRecordsExactlyOneRefundEntryAndNoTopup() {
        Job job = queuedJob(new BigDecimal("6.20"));

        refundService.cancelAndRefund(job, job.getQueuedAt().plusSeconds(60));

        verify(ledgerService, times(1)).record(any(), any(), any(), any(), any(), any());
        verify(ledgerService).record(UNI_ID, JOB_ID, TransactionType.REFUND,
                new BigDecimal("6.20"), new BigDecimal("56.20"), "Refund for cancelled job " + JOB_ID);
        verify(ledgerService, never()).record(any(), any(), eq(TransactionType.TOPUP), any(), any(), any());
        assertThat(user.getBalanceCents()).isEqualTo(5620L);
    }

    @Test
    void approvedRefundRecordsExactlyOneRefundEntryAndNoTopup() {
        RefundRequest request = new RefundRequest(1L, JOB_ID, UNI_ID, new BigDecimal("6.20"), Instant.now());
        when(refundRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(request));

        refundService.approve(1L, Instant.now());

        verify(ledgerService, times(1)).record(any(), any(), any(), any(), any(), any());
        verify(ledgerService).record(UNI_ID, JOB_ID, TransactionType.REFUND,
                new BigDecimal("6.20"), new BigDecimal("56.20"), "Refund for cancelled job " + JOB_ID);
        verify(ledgerService, never()).record(any(), any(), eq(TransactionType.TOPUP), any(), any(), any());
        assertThat(user.getBalanceCents()).isEqualTo(5620L);
    }

    @Test
    void outOfWindowCancellationLeavesBalanceAndLedgerUntouched() {
        Job job = queuedJob(new BigDecimal("6.20"));

        Optional<RefundRequest> pending =
                refundService.cancelAndRefund(job, job.getQueuedAt().plus(Duration.ofMinutes(10)));

        assertThat(pending).isPresent();
        assertThat(user.getBalanceCents()).isEqualTo(5000L);
        verifyNoInteractions(ledgerService);
    }

    @Test
    void rejectedRefundLeavesBalanceAndLedgerUntouched() {
        RefundRequest request = new RefundRequest(1L, JOB_ID, UNI_ID, new BigDecimal("6.20"), Instant.now());
        when(refundRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(request));

        refundService.reject(1L, Instant.now());

        assertThat(user.getBalanceCents()).isEqualTo(5000L);
        verifyNoInteractions(ledgerService);
    }

    @Test
    void plainTopupStillRecordsATopupEntry() {
        new WalletService(userRepository, ledgerService, new BigDecimal("50.00"))
                .credit(UNI_ID, new BigDecimal("10.00"));

        verify(ledgerService).record(eq(UNI_ID), eq(null), eq(TransactionType.TOPUP),
                eq(new BigDecimal("10.00")), eq(new BigDecimal("60.00")), any());
    }
}

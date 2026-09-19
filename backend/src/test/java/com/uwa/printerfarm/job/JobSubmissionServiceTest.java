package com.uwa.printerfarm.job;

import com.uwa.printerfarm.cost.CostCalculationService;
import com.uwa.printerfarm.wallet.InsufficientBalanceException;
import com.uwa.printerfarm.wallet.TransactionLedgerService;
import com.uwa.printerfarm.wallet.TransactionType;
import com.uwa.printerfarm.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobSubmissionServiceTest {

    private final CostCalculationService costCalculationService = new CostCalculationService();

    @Mock
    private WalletService walletService;

    @Mock
    private TransactionLedgerService ledgerService;

    @Mock
    private JobRepository jobRepository;

    private JobSubmissionService submissionService;

    @BeforeEach
    void setUp() {
        submissionService = new JobSubmissionService(costCalculationService, walletService, ledgerService, jobRepository);
    }

    private Job newJob(BigDecimal grams, BigDecimal minutes) {
        return new Job("22345678", "prusa-xl-1", "PLA", grams, minutes);
    }

    @Test
    void submittingAJobChargesCostAndRecordsDebitTransaction() {
        Job job = newJob(new BigDecimal("100"), new BigDecimal("60"));
        BigDecimal expectedCost = new BigDecimal("6.20");
        BigDecimal balanceAfter = new BigDecimal("43.80");

        when(walletService.debit("22345678", expectedCost)).thenReturn(balanceAfter);
        when(jobRepository.save(job)).thenReturn(job);

        submissionService.submit(job);

        assertThat(job.getCost()).isEqualByComparingTo(expectedCost);
        verify(walletService).debit("22345678", expectedCost);
        verify(jobRepository).save(job);
        verify(ledgerService).record(
                eq("22345678"),
                eq(job.getId()),
                eq(TransactionType.DEBIT),
                eq(expectedCost),
                eq(balanceAfter),
                any()
        );
    }

    @Test
    void insufficientBalanceLeavesJobUnpricedAndBalanceUntouched() {
        Job job = newJob(new BigDecimal("100000"), new BigDecimal("60"));

        when(walletService.debit(eq("22345678"), any()))
                .thenThrow(new InsufficientBalanceException("22345678", new BigDecimal("50.00"), new BigDecimal("999.00")));

        assertThatThrownBy(() -> submissionService.submit(job))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(jobRepository, never()).save(any());
        verify(ledgerService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void submitMethodIsTransactional() throws NoSuchMethodException {
        assertThat(JobSubmissionService.class.getMethod("submit", Job.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }
}

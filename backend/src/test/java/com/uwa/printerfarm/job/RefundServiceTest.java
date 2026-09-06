package com.uwa.printerfarm.job;

import com.uwa.printerfarm.wallet.TransactionLedgerService;
import com.uwa.printerfarm.wallet.TransactionType;
import com.uwa.printerfarm.wallet.WalletService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundServiceTest {

    private final JobLifecycleService jobLifecycleService = new JobLifecycleService(5);
    private final WalletService walletService = new WalletService(new BigDecimal("50.00"));
    private final TransactionLedgerService ledgerService = new TransactionLedgerService();
    private final RefundService refundService =
            new RefundService(jobLifecycleService, walletService, ledgerService);

    private Job newQueuedJobWithCost(BigDecimal cost) {
        Job job = new Job("22345678", "prusa-xl-1", "PLA", new BigDecimal("50"), new BigDecimal("120"));
        job.setCost(cost);
        BigDecimal balanceAfter = walletService.debit("22345678", cost);
        ledgerService.record("22345678", job.getId(), TransactionType.DEBIT, cost, balanceAfter, "Job submission charge");
        return job;
    }

    @Test
    void cancellingWithinWindowRefundsImmediately() {
        Job job = newQueuedJobWithCost(new BigDecimal("6.20"));

        Optional<RefundRequest> pending = refundService.cancelAndRefund(job, job.getQueuedAt().plusSeconds(60));

        assertThat(pending).isEmpty();
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(walletService.getBalance("22345678")).isEqualByComparingTo("50.00");
        assertThat(ledgerService.history("22345678")).hasSize(2);
        assertThat(ledgerService.history("22345678").get(1).getType()).isEqualTo(TransactionType.REFUND);
    }

    @Test
    void cancellingOutsideWindowCreatesPendingRequestWithoutRefunding() {
        Job job = newQueuedJobWithCost(new BigDecimal("6.20"));

        Optional<RefundRequest> pending = refundService.cancelAndRefund(job, job.getQueuedAt().plus(Duration.ofMinutes(10)));

        assertThat(pending).isPresent();
        assertThat(pending.get().getStatus()).isEqualTo(RefundStatus.PENDING_APPROVAL);
        assertThat(walletService.getBalance("22345678")).isEqualByComparingTo("43.80");
        assertThat(refundService.pending()).containsExactly(pending.get());
    }

    @Test
    void approvingAPendingRequestRefundsAndRecordsTransaction() {
        Job job = newQueuedJobWithCost(new BigDecimal("6.20"));
        RefundRequest request = refundService.cancelAndRefund(job, job.getQueuedAt().plus(Duration.ofMinutes(10))).orElseThrow();

        RefundRequest approved = refundService.approve(request.getId(), Instant.now());

        assertThat(approved.getStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(walletService.getBalance("22345678")).isEqualByComparingTo("50.00");
        assertThat(refundService.pending()).isEmpty();
    }

    @Test
    void rejectingAPendingRequestDoesNotRefund() {
        Job job = newQueuedJobWithCost(new BigDecimal("6.20"));
        RefundRequest request = refundService.cancelAndRefund(job, job.getQueuedAt().plus(Duration.ofMinutes(10))).orElseThrow();

        RefundRequest rejected = refundService.reject(request.getId(), Instant.now());

        assertThat(rejected.getStatus()).isEqualTo(RefundStatus.REJECTED);
        assertThat(walletService.getBalance("22345678")).isEqualByComparingTo("43.80");
    }

    @Test
    void decidingAlreadyDecidedRequestThrows() {
        Job job = newQueuedJobWithCost(new BigDecimal("6.20"));
        RefundRequest request = refundService.cancelAndRefund(job, job.getQueuedAt().plus(Duration.ofMinutes(10))).orElseThrow();
        refundService.approve(request.getId(), Instant.now());

        assertThatThrownBy(() -> refundService.reject(request.getId(), Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decidingUnknownRequestThrows() {
        assertThatThrownBy(() -> refundService.approve(999L, Instant.now()))
                .isInstanceOf(RefundRequestNotFoundException.class);
    }
}

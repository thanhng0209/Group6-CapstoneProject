package com.uwa.printerfarm.job;

import com.uwa.printerfarm.wallet.TransactionLedgerService;
import com.uwa.printerfarm.wallet.TransactionType;
import com.uwa.printerfarm.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private final JobLifecycleService jobLifecycleService = new JobLifecycleService(5);

    @Mock
    private WalletService walletService;

    @Mock
    private TransactionLedgerService ledgerService;

    @Mock
    private RefundRequestRepository refundRequestRepository;

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(jobLifecycleService, walletService, ledgerService, refundRequestRepository);
        lenient().when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Job newQueuedJobWithCost(BigDecimal cost) {
        Job job = new Job("22345678", "prusa-xl-1", "PLA", new BigDecimal("50"), new BigDecimal("120"));
        job.setCost(cost);
        return job;
    }

    @Test
    void cancellingWithinWindowRefundsImmediately() {
        Job job = newQueuedJobWithCost(new BigDecimal("6.20"));

        Optional<RefundRequest> pending = refundService.cancelAndRefund(job, job.getQueuedAt().plusSeconds(60));

        assertThat(pending).isEmpty();
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        verify(walletService).credit("22345678", new BigDecimal("6.20"));
        verify(ledgerService).record(eq("22345678"), eq(job.getId()), eq(TransactionType.REFUND),
                eq(new BigDecimal("6.20")), any(), any());
    }

    @Test
    void cancellingOutsideWindowCreatesPendingRequestWithoutRefunding() {
        Job job = newQueuedJobWithCost(new BigDecimal("6.20"));
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<RefundRequest> pending = refundService.cancelAndRefund(job, job.getQueuedAt().plus(Duration.ofMinutes(10)));

        assertThat(pending).isPresent();
        assertThat(pending.get().getStatus()).isEqualTo(RefundStatus.PENDING_APPROVAL);
        verify(refundRequestRepository).save(any(RefundRequest.class));
        verify(walletService, never()).credit(any(), any());
    }

    @Test
    void approvingAPendingRequestRefundsAndRecordsTransaction() {
        Job job = newQueuedJobWithCost(new BigDecimal("6.20"));
        RefundRequest request = new RefundRequest(1L, job.getId(), job.getOwnerUniId(), job.getCost(), Instant.now());
        when(refundRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(request));

        RefundRequest approved = refundService.approve(1L, Instant.now());

        assertThat(approved.getStatus()).isEqualTo(RefundStatus.APPROVED);
        verify(refundRequestRepository).save(request);
        verify(walletService).credit("22345678", new BigDecimal("6.20"));
        verify(ledgerService).record(eq("22345678"), eq(job.getId()), eq(TransactionType.REFUND),
                eq(new BigDecimal("6.20")), any(), any());
    }

    @Test
    void rejectingAPendingRequestDoesNotRefund() {
        Job job = newQueuedJobWithCost(new BigDecimal("6.20"));
        RefundRequest request = new RefundRequest(1L, job.getId(), job.getOwnerUniId(), job.getCost(), Instant.now());
        when(refundRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(request));

        RefundRequest rejected = refundService.reject(1L, Instant.now());

        assertThat(rejected.getStatus()).isEqualTo(RefundStatus.REJECTED);
        verify(refundRequestRepository).save(request);
        verify(walletService, never()).credit(any(), any());
    }

    @Test
    void decidingAlreadyDecidedRequestThrows() {
        Job job = newQueuedJobWithCost(new BigDecimal("6.20"));
        RefundRequest request = new RefundRequest(1L, job.getId(), job.getOwnerUniId(), job.getCost(), Instant.now());
        request.approve(Instant.now());
        when(refundRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> refundService.reject(1L, Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decidingUnknownRequestThrows() {
        when(refundRequestRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.approve(999L, Instant.now()))
                .isInstanceOf(RefundRequestNotFoundException.class);
    }

    @Test
    void pendingReturnsRequestsFromRepository() {
        RefundRequest request = new RefundRequest(1L, 10L, "22345678", new BigDecimal("6.20"), Instant.now());
        when(refundRequestRepository.findByStatus(RefundStatus.PENDING_APPROVAL)).thenReturn(List.of(request));

        List<RefundRequest> pending = refundService.pending();

        assertThat(pending).containsExactly(request);
        verify(refundRequestRepository).findByStatus(RefundStatus.PENDING_APPROVAL);
    }
}

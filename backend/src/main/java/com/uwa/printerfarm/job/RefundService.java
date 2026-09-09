package com.uwa.printerfarm.job;

import com.uwa.printerfarm.wallet.TransactionLedgerService;
import com.uwa.printerfarm.wallet.TransactionType;
import com.uwa.printerfarm.wallet.WalletService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Executes the refund side effects of a cancellation. A cancellation inside
 * the window is refunded immediately; one outside the window is parked as a
 * pending {@link RefundRequest} until a farm manager approves or rejects it
 * (MVP acceptance criteria Scenario 5/6: in-window cancellations auto-refund,
 * out-of-window ones are routed to approval instead of refunded directly).
 */
@Service
public class RefundService {

    private final JobLifecycleService jobLifecycleService;
    private final WalletService walletService;
    private final TransactionLedgerService ledgerService;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<Long, RefundRequest> refundRequests = new ConcurrentHashMap<>();

    public RefundService(JobLifecycleService jobLifecycleService, WalletService walletService,
                          TransactionLedgerService ledgerService) {
        this.jobLifecycleService = jobLifecycleService;
        this.walletService = walletService;
        this.ledgerService = ledgerService;
    }

    /**
     * Cancels the job. Returns empty if it was refunded immediately, or the
     * created {@link RefundRequest} if it now needs farm-manager approval.
     */
    public Optional<RefundRequest> cancelAndRefund(Job job, Instant now) {
        RefundDecision decision = jobLifecycleService.cancel(job, now);

        if (decision == RefundDecision.AUTO_REFUNDED) {
            refund(job.getOwnerUniId(), job.getId(), job.getCost());
            return Optional.empty();
        }

        RefundRequest request = new RefundRequest(sequence.incrementAndGet(), job.getId(),
                job.getOwnerUniId(), job.getCost(), now);
        refundRequests.put(request.getId(), request);
        return Optional.of(request);
    }

    public RefundRequest approve(long requestId, Instant now) {
        RefundRequest request = requireRequest(requestId);
        request.approve(now);
        refund(request.getOwnerUniId(), request.getJobId(), request.getAmount());
        return request;
    }

    public RefundRequest reject(long requestId, Instant now) {
        RefundRequest request = requireRequest(requestId);
        request.reject(now);
        return request;
    }

    public List<RefundRequest> pending() {
        return refundRequests.values().stream()
                .filter(request -> request.getStatus() == RefundStatus.PENDING_APPROVAL)
                .toList();
    }

    private void refund(String ownerUniId, Long jobId, BigDecimal amount) {
        BigDecimal balanceAfter = walletService.credit(ownerUniId, amount);
        ledgerService.record(ownerUniId, jobId, TransactionType.REFUND, amount, balanceAfter,
                "Refund for cancelled job " + jobId);
    }

    private RefundRequest requireRequest(long requestId) {
        RefundRequest request = refundRequests.get(requestId);
        if (request == null) {
            throw new RefundRequestNotFoundException(requestId);
        }
        return request;
    }
}

package com.uwa.printerfarm.job;

import com.uwa.printerfarm.wallet.TransactionLedgerService;
import com.uwa.printerfarm.wallet.TransactionType;
import com.uwa.printerfarm.wallet.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Executes refund side effects of job cancellations backed by PostgreSQL.
 * In-window cancellations auto-refund directly to the user's wallet.
 * Out-of-window cancellations create a pending RefundRequest requiring farm-manager approval.
 */
@Service
public class RefundService {

    private final JobLifecycleService jobLifecycleService;
    private final WalletService walletService;
    private final TransactionLedgerService ledgerService;
    private final RefundRequestRepository refundRequestRepository;

    public RefundService(JobLifecycleService jobLifecycleService,
                         WalletService walletService,
                         TransactionLedgerService ledgerService,
                         RefundRequestRepository refundRequestRepository) {
        this.jobLifecycleService = jobLifecycleService;
        this.walletService = walletService;
        this.ledgerService = ledgerService;
        this.refundRequestRepository = refundRequestRepository;
    }

    @Transactional
    public Optional<RefundRequest> cancelAndRefund(Job job, Instant now) {
        RefundDecision decision = jobLifecycleService.cancel(job, now);

        if (decision == RefundDecision.AUTO_REFUNDED) {
            refund(job.getOwnerUniId(), job.getId(), job.getCost());
            return Optional.empty();
        }

        RefundRequest request = new RefundRequest(job.getId(), job.getOwnerUniId(), job.getCost(), now);
        RefundRequest saved = refundRequestRepository.save(request);
        return Optional.of(saved);
    }

    @Transactional
    public RefundRequest approve(long requestId, Instant now) {
        RefundRequest request = requireRequestForDecision(requestId);
        request.approve(now);
        refundRequestRepository.save(request);
        refund(request.getOwnerUniId(), request.getJobId(), request.getAmount());
        return request;
    }

    @Transactional
    public RefundRequest reject(long requestId, Instant now) {
        RefundRequest request = requireRequestForDecision(requestId);
        request.reject(now);
        return refundRequestRepository.save(request);
    }

    public List<RefundRequest> pending() {
        return refundRequestRepository.findByStatus(RefundStatus.PENDING_APPROVAL);
    }

    private void refund(String ownerUniId, Long jobId, BigDecimal amount) {
        BigDecimal balanceAfter = walletService.credit(ownerUniId, amount);
        ledgerService.record(ownerUniId, jobId, TransactionType.REFUND, amount, balanceAfter,
                "Refund for cancelled job " + jobId);
    }

    private RefundRequest requireRequestForDecision(long requestId) {
        return refundRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new RefundRequestNotFoundException(requestId));
    }
}

package com.uwa.printerfarm.job;

import com.uwa.printerfarm.cost.CostCalculationService;
import com.uwa.printerfarm.wallet.TransactionLedgerService;
import com.uwa.printerfarm.wallet.TransactionType;
import com.uwa.printerfarm.wallet.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Prices and admits a job into the queue: calculates its cost, deducts that
 * cost from the student's balance, and records an immutable debit transaction
 * (MVP acceptance criteria: submission deducts balance and keeps a permanent
 * transaction history). If the student cannot afford the job, the balance is
 * left untouched and the job is never queued.
 */
@Service
public class JobSubmissionService {

    private final CostCalculationService costCalculationService;
    private final WalletService walletService;
    private final TransactionLedgerService ledgerService;
    private final JobRepository jobRepository;

    public JobSubmissionService(CostCalculationService costCalculationService,
                                 WalletService walletService,
                                 TransactionLedgerService ledgerService,
                                 JobRepository jobRepository) {
        this.costCalculationService = costCalculationService;
        this.walletService = walletService;
        this.ledgerService = ledgerService;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public Job submit(Job job) {
        BigDecimal cost = costCalculationService.calculate(
                job.getMaterial(), job.getEstimatedGrams(), job.getEstimatedMinutes());

        BigDecimal balanceAfter = walletService.debit(job.getOwnerUniId(), cost);
        job.setCost(cost);
        jobRepository.save(job);
        ledgerService.record(job.getOwnerUniId(), job.getId(), TransactionType.DEBIT, cost, balanceAfter,
                "Job submission charge for printer " + job.getPrinterId());

        return job;
    }
}

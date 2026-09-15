package com.uwa.printerfarm.admin;

import com.uwa.printerfarm.cost.CostCalculationService;
import com.uwa.printerfarm.job.Job;
import com.uwa.printerfarm.job.JobLifecycleService;
import com.uwa.printerfarm.job.JobRepository;
import com.uwa.printerfarm.job.JobStatus;
import com.uwa.printerfarm.job.JobSubmissionService;
import com.uwa.printerfarm.job.RefundRequest;
import com.uwa.printerfarm.job.RefundService;
import com.uwa.printerfarm.wallet.TransactionLedgerService;
import com.uwa.printerfarm.wallet.WalletService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdminMonitoringServiceTest {

    private final CostCalculationService costCalculationService = new CostCalculationService();
    private final WalletService walletService = new WalletService(new BigDecimal("1000.00"));
    private final TransactionLedgerService ledgerService = new TransactionLedgerService();
    private final JobRepository jobRepository = new JobRepository();
    private final JobSubmissionService submissionService =
            new JobSubmissionService(costCalculationService, walletService, ledgerService, jobRepository);
    private final JobLifecycleService jobLifecycleService = new JobLifecycleService(5);
    private final RefundService refundService = new RefundService(jobLifecycleService, walletService, ledgerService);
    private final AdminMonitoringService monitoringService =
            new AdminMonitoringService(jobRepository, ledgerService, refundService);

    private Job submitJob(String printerId, String material, BigDecimal grams) {
        Job job = new Job("22345678", printerId, material, grams, new BigDecimal("60"));
        return submissionService.submit(job);
    }

    @Test
    void jobsWithNoFilterReturnsEverySubmittedJob() {
        Job first = submitJob("prusa-xl-1", "PLA", new BigDecimal("100"));
        Job second = submitJob("prusa-xl-1", "PLA", new BigDecimal("50"));

        assertThat(monitoringService.jobs(null)).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void jobsCanBeFilteredByStatus() {
        Job queued = submitJob("prusa-xl-1", "PLA", new BigDecimal("100"));
        Job printing = submitJob("prusa-xl-1", "PLA", new BigDecimal("50"));
        jobLifecycleService.transition(printing, JobStatus.PRINTING);

        assertThat(monitoringService.jobs(JobStatus.QUEUED)).containsExactly(queued);
        assertThat(monitoringService.jobs(JobStatus.PRINTING)).containsExactly(printing);
    }

    @Test
    void filamentUsageSumsByMaterialAndExcludesCancelledJobs() {
        submitJob("prusa-xl-1", "PLA", new BigDecimal("100"));
        submitJob("prusa-xl-1", "PETG", new BigDecimal("40"));
        Job cancelled = submitJob("prusa-xl-1", "PLA", new BigDecimal("50"));
        refundService.cancelAndRefund(cancelled, cancelled.getQueuedAt().plusSeconds(30));

        var usage = monitoringService.filamentUsageByMaterial();

        assertThat(usage.get("PLA")).isEqualByComparingTo("100");
        assertThat(usage.get("PETG")).isEqualByComparingTo("40");
    }

    @Test
    void costSummaryAggregatesChargesAndRefunds() {
        Job kept = submitJob("prusa-xl-1", "PLA", new BigDecimal("100"));
        Job refunded = submitJob("prusa-xl-1", "PLA", new BigDecimal("50"));
        refundService.cancelAndRefund(refunded, refunded.getQueuedAt().plusSeconds(30));

        CostSummary summary = monitoringService.costSummary();

        BigDecimal expectedCharged = kept.getCost().add(refunded.getCost());
        assertThat(summary.getTotalCharged()).isEqualByComparingTo(expectedCharged);
        assertThat(summary.getTotalRefunded()).isEqualByComparingTo(refunded.getCost());
        assertThat(summary.getNetRevenue()).isEqualByComparingTo(expectedCharged.subtract(refunded.getCost()));
    }

    @Test
    void printerActivityGroupsJobCountsByPrinterAndStatus() {
        Job queuedOnXl = submitJob("prusa-xl-1", "PLA", new BigDecimal("100"));
        Job printingOnXl = submitJob("prusa-xl-1", "PLA", new BigDecimal("50"));
        jobLifecycleService.transition(printingOnXl, JobStatus.PRINTING);
        submitJob("prusa-core-1", "PETG", new BigDecimal("30"));

        var activity = monitoringService.printerActivity();

        var xlActivity = activity.stream()
                .filter(printer -> printer.getPrinterId().equals("prusa-xl-1"))
                .findFirst().orElseThrow();
        assertThat(xlActivity.getJobCountsByStatus().get(JobStatus.QUEUED)).isEqualTo(1L);
        assertThat(xlActivity.getJobCountsByStatus().get(JobStatus.PRINTING)).isEqualTo(1L);

        var coreActivity = activity.stream()
                .filter(printer -> printer.getPrinterId().equals("prusa-core-1"))
                .findFirst().orElseThrow();
        assertThat(coreActivity.getJobCountsByStatus().get(JobStatus.QUEUED)).isEqualTo(1L);
        assertThat(queuedOnXl.getStatus()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void pendingRefundRequestsDelegatesToRefundService() {
        Job job = submitJob("prusa-xl-1", "PLA", new BigDecimal("100"));

        Optional<RefundRequest> created = refundService.cancelAndRefund(job, job.getQueuedAt().plus(Duration.ofMinutes(10)));

        assertThat(monitoringService.pendingRefundRequests()).containsExactly(created.orElseThrow());
    }
}

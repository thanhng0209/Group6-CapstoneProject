package com.uwa.printerfarm.admin;

import com.uwa.printerfarm.job.Job;
import com.uwa.printerfarm.job.JobLifecycleService;
import com.uwa.printerfarm.job.JobRepository;
import com.uwa.printerfarm.job.JobStatus;
import com.uwa.printerfarm.job.RefundRequest;
import com.uwa.printerfarm.job.RefundService;
import com.uwa.printerfarm.wallet.Transaction;
import com.uwa.printerfarm.wallet.TransactionLedgerService;
import com.uwa.printerfarm.wallet.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMonitoringServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private TransactionLedgerService ledgerService;

    @Mock
    private RefundService refundService;

    private AdminMonitoringService monitoringService;
    private final JobLifecycleService jobLifecycleService = new JobLifecycleService(5);

    @BeforeEach
    void setUp() {
        monitoringService = new AdminMonitoringService(jobRepository, ledgerService, refundService);
    }

    private Job createJob(String printerId, String material, BigDecimal grams) {
        return new Job("22345678", printerId, material, grams, new BigDecimal("60"));
    }

    @Test
    void jobsWithNoFilterReturnsEverySubmittedJob() {
        Job first = createJob("prusa-xl-1", "PLA", new BigDecimal("100"));
        Job second = createJob("prusa-xl-1", "PLA", new BigDecimal("50"));
        when(jobRepository.findAll()).thenReturn(List.of(first, second));

        assertThat(monitoringService.jobs(null)).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void jobsCanBeFilteredByStatus() {
        Job queued = createJob("prusa-xl-1", "PLA", new BigDecimal("100"));
        Job printing = createJob("prusa-xl-1", "PLA", new BigDecimal("50"));
        jobLifecycleService.transition(printing, JobStatus.PRINTING);
        when(jobRepository.findAll()).thenReturn(List.of(queued, printing));

        assertThat(monitoringService.jobs(JobStatus.QUEUED)).containsExactly(queued);
        assertThat(monitoringService.jobs(JobStatus.PRINTING)).containsExactly(printing);
    }

    @Test
    void filamentUsageSumsByMaterialAndExcludesCancelledJobs() {
        Job job1 = createJob("prusa-xl-1", "PLA", new BigDecimal("100"));
        Job job2 = createJob("prusa-xl-1", "PETG", new BigDecimal("40"));
        Job cancelled = createJob("prusa-xl-1", "PLA", new BigDecimal("50"));
        jobLifecycleService.cancel(cancelled, cancelled.getQueuedAt().plusSeconds(30));

        when(jobRepository.findAll()).thenReturn(List.of(job1, job2, cancelled));

        var usage = monitoringService.filamentUsageByMaterial();

        assertThat(usage.get("PLA")).isEqualByComparingTo("100");
        assertThat(usage.get("PETG")).isEqualByComparingTo("40");
    }

    @Test
    void costSummaryAggregatesChargesAndRefunds() {
        Transaction charge = new Transaction("22345678", 1L, TransactionType.DEBIT,
                new BigDecimal("6.20"), new BigDecimal("43.80"), Instant.now(), "Job charge");
        Transaction refund = new Transaction("22345678", 2L, TransactionType.REFUND,
                new BigDecimal("2.10"), new BigDecimal("45.90"), Instant.now(), "Job refund");

        when(ledgerService.all()).thenReturn(List.of(charge, refund));

        CostSummary summary = monitoringService.costSummary();

        assertThat(summary.getTotalCharged()).isEqualByComparingTo("6.20");
        assertThat(summary.getTotalRefunded()).isEqualByComparingTo("2.10");
        assertThat(summary.getNetRevenue()).isEqualByComparingTo("4.10");
    }

    @Test
    void printerActivityGroupsJobCountsByPrinterAndStatus() {
        Job queuedOnXl = createJob("prusa-xl-1", "PLA", new BigDecimal("100"));
        Job printingOnXl = createJob("prusa-xl-1", "PLA", new BigDecimal("50"));
        jobLifecycleService.transition(printingOnXl, JobStatus.PRINTING);
        Job queuedOnCore = createJob("prusa-core-1", "PETG", new BigDecimal("30"));

        when(jobRepository.findAll()).thenReturn(List.of(queuedOnXl, printingOnXl, queuedOnCore));

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
    }

    @Test
    void pendingRefundRequestsDelegatesToRefundService() {
        RefundRequest request = new RefundRequest(1L, 10L, "22345678", new BigDecimal("6.20"), Instant.now());
        when(refundService.pending()).thenReturn(List.of(request));

        assertThat(monitoringService.pendingRefundRequests()).containsExactly(request);
    }

    @Test
    void usageTrendsIsZeroFilledForEveryDayInRangeAndAggregatesTodayFromRealData() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        Job job1 = createJob("prusa-xl-1", "PLA", new BigDecimal("100"));
        Job job2 = createJob("prusa-xl-1", "PETG", new BigDecimal("40"));
        Job cancelled = createJob("prusa-xl-1", "PLA", new BigDecimal("50"));
        jobLifecycleService.cancel(cancelled, cancelled.getQueuedAt().plusSeconds(30));
        when(jobRepository.findAll()).thenReturn(List.of(job1, job2, cancelled));

        Transaction charge = new Transaction("22345678", 1L, TransactionType.DEBIT,
                new BigDecimal("6.20"), new BigDecimal("43.80"), Instant.now(), "Job charge");
        Transaction refund = new Transaction("22345678", 2L, TransactionType.REFUND,
                new BigDecimal("2.10"), new BigDecimal("45.90"), Instant.now(), "Job refund");
        when(ledgerService.all()).thenReturn(List.of(charge, refund));

        List<UsageTrendPoint> trends = monitoringService.usageTrends(3);

        assertThat(trends).hasSize(3);
        assertThat(trends.get(0).getDate()).isEqualTo(today.minusDays(2));
        assertThat(trends.get(1).getDate()).isEqualTo(today.minusDays(1));
        assertThat(trends.get(2).getDate()).isEqualTo(today);

        assertThat(trends.get(0).getFilamentGrams()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(trends.get(0).getJobCount()).isZero();

        UsageTrendPoint todayPoint = trends.get(2);
        assertThat(todayPoint.getFilamentGrams()).isEqualByComparingTo("140");
        assertThat(todayPoint.getJobCount()).isEqualTo(3);
        assertThat(todayPoint.getCharged()).isEqualByComparingTo("6.20");
        assertThat(todayPoint.getRefunded()).isEqualByComparingTo("2.10");
    }

    @Test
    void usageTrendsRejectsNonPositiveDays() {
        assertThatThrownBy(() -> monitoringService.usageTrends(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

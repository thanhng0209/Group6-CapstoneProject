package com.uwa.printerfarm.printer;

import com.uwa.printerfarm.job.Job;
import com.uwa.printerfarm.job.JobLifecycleService;
import com.uwa.printerfarm.job.JobRepository;
import com.uwa.printerfarm.job.JobStatus;
import com.uwa.printerfarm.notification.JobNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MockPrinterDispatcherTest {

    private PrinterRepository printerRepository;
    private JobRepository jobRepository;
    private JobLifecycleService jobLifecycleService;
    private JobNotificationService jobNotificationService;
    private Clock fixedClock;

    private MockPrinterDispatcher dispatcher;
    private Instant initialTime;

    @BeforeEach
    void setUp() {
        printerRepository = mock(PrinterRepository.class);
        jobRepository = mock(JobRepository.class);
        jobLifecycleService = new JobLifecycleService(5);
        jobNotificationService = mock(JobNotificationService.class);

        initialTime = Instant.parse("2026-09-19T10:00:00Z");
        fixedClock = Clock.fixed(initialTime, ZoneId.of("UTC"));

        dispatcher = new MockPrinterDispatcher(
                printerRepository,
                jobRepository,
                jobLifecycleService,
                jobNotificationService,
                60L,
                fixedClock
        );
    }

    private Job createQueuedJob(Long id, String uniId, String printerId, String material, Instant queuedAt) {
        Job job = new Job(uniId, printerId, "test.gcode", material, new BigDecimal("30.0"), new BigDecimal("45.0"));
        job.assignId(id);
        job.setCost(new BigDecimal("3.50"));
        // Use reflection or constructor to simulate queuedAt if needed, or leave default
        return job;
    }

    private Printer createPrinter(String id, String model, String status, String material) {
        return new Printer(id, id + " Name", model, status, material, "Orange");
    }

    @Test
    void idlePrinterPicksUpOldestMatchingQueuedJob() {
        Printer printer = createPrinter("PRUSA_XL_1", "PRUSA_XL", "IDLE", "PLA");
        when(printerRepository.findByStatus("IDLE")).thenReturn(List.of(printer));

        Job job1 = createQueuedJob(1L, "22345678", "PRUSA_XL", "PLA", initialTime.minusSeconds(120));
        Job job2 = createQueuedJob(2L, "22345679", "PRUSA_XL", "PLA", initialTime.minusSeconds(60));
        when(jobRepository.findByStatusOrderByQueuedAtAsc(JobStatus.QUEUED)).thenReturn(List.of(job1, job2));

        dispatcher.dispatchIdlePrinters();

        assertThat(job1.getStatus()).isEqualTo(JobStatus.PRINTING);
        assertThat(job1.getPrinterId()).isEqualTo("PRUSA_XL_1");
        assertThat(printer.getStatus()).isEqualTo("PRINTING");

        verify(jobRepository).save(job1);
        verify(printerRepository).save(printer);
        verify(jobRepository, never()).save(job2);
        assertThat(job2.getStatus()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void idlePrinterSkipsJobWithMismatchedFilament() {
        Printer printer = createPrinter("PRUSA_XL_1", "PRUSA_XL", "IDLE", "PLA");
        when(printerRepository.findByStatus("IDLE")).thenReturn(List.of(printer));

        // Job requires PETG, printer has PLA
        Job petgJob = createQueuedJob(1L, "22345678", "PRUSA_XL", "PETG", initialTime.minusSeconds(120));
        // Job requires PLA, printer has PLA
        Job plaJob = createQueuedJob(2L, "22345679", "PRUSA_XL", "PLA", initialTime.minusSeconds(60));
        when(jobRepository.findByStatusOrderByQueuedAtAsc(JobStatus.QUEUED)).thenReturn(List.of(petgJob, plaJob));

        dispatcher.dispatchIdlePrinters();

        assertThat(petgJob.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(plaJob.getStatus()).isEqualTo(JobStatus.PRINTING);
        assertThat(plaJob.getPrinterId()).isEqualTo("PRUSA_XL_1");
        assertThat(printer.getStatus()).isEqualTo("PRINTING");

        verify(jobRepository).save(plaJob);
        verify(jobRepository, never()).save(petgJob);
    }

    @Test
    void idlePrinterSkipsJobWithMismatchedModel() {
        Printer xlPrinter = createPrinter("PRUSA_XL_1", "PRUSA_XL", "IDLE", "PLA");
        when(printerRepository.findByStatus("IDLE")).thenReturn(List.of(xlPrinter));

        // Job targeted for MK4S
        Job mk4sJob = createQueuedJob(1L, "22345678", "PRUSA_MK4S", "PLA", initialTime.minusSeconds(120));
        when(jobRepository.findByStatusOrderByQueuedAtAsc(JobStatus.QUEUED)).thenReturn(List.of(mk4sJob));

        dispatcher.dispatchIdlePrinters();

        assertThat(mk4sJob.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(xlPrinter.getStatus()).isEqualTo("IDLE");
        verify(jobRepository, never()).save(any(Job.class));
    }

    @Test
    void twoIdlePrintersDoNotPickUpTheSameQueuedJob() {
        Printer printer1 = createPrinter("PRUSA_XL_1", "PRUSA_XL", "IDLE", "PLA");
        Printer printer2 = createPrinter("PRUSA_XL_2", "PRUSA_XL", "IDLE", "PLA");
        when(printerRepository.findByStatus("IDLE")).thenReturn(List.of(printer1, printer2));

        Job job1 = createQueuedJob(10L, "22345678", "PRUSA_XL", "PLA", initialTime.minusSeconds(300));
        Job job2 = createQueuedJob(20L, "22345679", "PRUSA_XL", "PLA", initialTime.minusSeconds(200));
        when(jobRepository.findByStatusOrderByQueuedAtAsc(JobStatus.QUEUED)).thenReturn(List.of(job1, job2));

        dispatcher.dispatchIdlePrinters();

        assertThat(job1.getStatus()).isEqualTo(JobStatus.PRINTING);
        assertThat(job1.getPrinterId()).isEqualTo("PRUSA_XL_1");
        assertThat(job2.getStatus()).isEqualTo(JobStatus.PRINTING);
        assertThat(job2.getPrinterId()).isEqualTo("PRUSA_XL_2");

        assertThat(printer1.getStatus()).isEqualTo("PRINTING");
        assertThat(printer2.getStatus()).isEqualTo("PRINTING");

        verify(jobRepository).save(job1);
        verify(jobRepository).save(job2);
    }

    @Test
    void jobPrintingForOverOneMinuteIsCompletedAndPrinterRevertsToIdle() {
        Printer printer = createPrinter("PRUSA_XL_1", "PRUSA_XL", "PRINTING", "PLA");
        when(printerRepository.findById("PRUSA_XL_1")).thenReturn(Optional.of(printer));

        Job job = createQueuedJob(100L, "22345678", "PRUSA_XL_1", "PLA", initialTime.minusSeconds(200));
        jobLifecycleService.transition(job, JobStatus.PRINTING);

        when(jobRepository.findByStatus(JobStatus.PRINTING)).thenReturn(List.of(job));

        // Start time was 61 seconds ago
        dispatcher.getPrintingStartedAt().put(100L, initialTime.minusSeconds(61));

        dispatcher.simulatePrintTime();

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getCompletedAt()).isNotNull();
        assertThat(printer.getStatus()).isEqualTo("IDLE");

        verify(jobRepository).save(job);
        verify(printerRepository).save(printer);
        verify(jobNotificationService).notifyJobFinished(job);
        assertThat(dispatcher.getPrintingStartedAt()).doesNotContainKey(100L);
    }

    @Test
    void jobPrintingForLessThanOneMinuteRemainsInPrintingState() {
        Printer printer = createPrinter("PRUSA_XL_1", "PRUSA_XL", "PRINTING", "PLA");
        when(printerRepository.findById("PRUSA_XL_1")).thenReturn(Optional.of(printer));

        Job job = createQueuedJob(100L, "22345678", "PRUSA_XL_1", "PLA", initialTime.minusSeconds(200));
        jobLifecycleService.transition(job, JobStatus.PRINTING);

        when(jobRepository.findByStatus(JobStatus.PRINTING)).thenReturn(List.of(job));

        // Start time was only 30 seconds ago
        dispatcher.getPrintingStartedAt().put(100L, initialTime.minusSeconds(30));

        dispatcher.simulatePrintTime();

        assertThat(job.getStatus()).isEqualTo(JobStatus.PRINTING);
        assertThat(printer.getStatus()).isEqualTo("PRINTING");

        verify(jobRepository, never()).save(job);
        verify(jobNotificationService, never()).notifyJobFinished(any(Job.class));
    }

    @Test
    void failJobTransitionsToFailedRevertsPrinterAndNotifies() {
        Printer printer = createPrinter("PRUSA_XL_1", "PRUSA_XL", "PRINTING", "PLA");
        when(printerRepository.findById("PRUSA_XL_1")).thenReturn(Optional.of(printer));

        Job job = createQueuedJob(200L, "22345678", "PRUSA_XL_1", "PLA", initialTime.minusSeconds(100));
        jobLifecycleService.transition(job, JobStatus.PRINTING);
        dispatcher.getPrintingStartedAt().put(200L, initialTime.minusSeconds(40));

        dispatcher.failJob(job);

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(printer.getStatus()).isEqualTo("IDLE");

        verify(jobRepository).save(job);
        verify(printerRepository).save(printer);
        verify(jobNotificationService).notifyJobFinished(job);
        assertThat(dispatcher.getPrintingStartedAt()).doesNotContainKey(200L);
    }

    @Test
    void notificationFailureDoesNotInterruptJobCompletion() {
        Printer printer = createPrinter("PRUSA_XL_1", "PRUSA_XL", "PRINTING", "PLA");
        when(printerRepository.findById("PRUSA_XL_1")).thenReturn(Optional.of(printer));

        Job job = createQueuedJob(100L, "22345678", "PRUSA_XL_1", "PLA", initialTime.minusSeconds(200));
        jobLifecycleService.transition(job, JobStatus.PRINTING);
        when(jobRepository.findByStatus(JobStatus.PRINTING)).thenReturn(List.of(job));
        dispatcher.getPrintingStartedAt().put(100L, initialTime.minusSeconds(65));

        doThrow(new RuntimeException("Mail server down")).when(jobNotificationService).notifyJobFinished(job);

        dispatcher.simulatePrintTime();

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(printer.getStatus()).isEqualTo("IDLE");
        verify(jobRepository).save(job);
    }
}

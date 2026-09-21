package com.uwa.printerfarm.printer;

import com.uwa.printerfarm.job.Job;
import com.uwa.printerfarm.job.JobLifecycleService;
import com.uwa.printerfarm.job.JobRepository;
import com.uwa.printerfarm.job.JobStatus;
import com.uwa.printerfarm.notification.JobNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates physical 3D printers for the UWA Print Farm.
 * Runs on a recurring schedule (every 10 seconds by default) to:
 * 1. Progress active PRINTING jobs and mark them COMPLETED once print time passes (> 1 min).
 * 2. Find IDLE printers and assign the oldest matching QUEUED jobs.
 * 3. Trigger email notifications via {@link JobNotificationService} upon completion or failure.
 */
@Component
@ConditionalOnProperty(name = "printerfarm.dispatcher.enabled", havingValue = "true", matchIfMissing = true)
public class MockPrinterDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MockPrinterDispatcher.class);

    private final PrinterRepository printerRepository;
    private final JobRepository jobRepository;
    private final JobLifecycleService jobLifecycleService;
    private final JobNotificationService jobNotificationService;
    private final Duration printDuration;
    private final Clock clock;

    private final Map<Long, Instant> printingStartedAt = new ConcurrentHashMap<>();

    public MockPrinterDispatcher(PrinterRepository printerRepository,
                                 JobRepository jobRepository,
                                 JobLifecycleService jobLifecycleService,
                                 JobNotificationService jobNotificationService,
                                 long printDurationSeconds,
                                 Clock clock) {
        this.printerRepository = printerRepository;
        this.jobRepository = jobRepository;
        this.jobLifecycleService = jobLifecycleService;
        this.jobNotificationService = jobNotificationService;
        this.printDuration = Duration.ofSeconds(printDurationSeconds);
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Autowired
    public MockPrinterDispatcher(PrinterRepository printerRepository,
                                 JobRepository jobRepository,
                                 JobLifecycleService jobLifecycleService,
                                 JobNotificationService jobNotificationService,
                                 @Value("${printerfarm.dispatcher.print-duration-seconds:60}") long printDurationSeconds) {
        this(printerRepository, jobRepository, jobLifecycleService, jobNotificationService, printDurationSeconds, Clock.systemUTC());
    }

    public MockPrinterDispatcher(PrinterRepository printerRepository,
                                 JobRepository jobRepository,
                                 JobLifecycleService jobLifecycleService,
                                 JobNotificationService jobNotificationService) {
        this(printerRepository, jobRepository, jobLifecycleService, jobNotificationService, 60L, Clock.systemUTC());
    }

    /**
     * Recurring simulation loop executed every 10 seconds.
     */
    @Scheduled(fixedDelayString = "${printerfarm.dispatcher.fixed-delay-ms:10000}")
    @Transactional
    public void dispatchAndSimulate() {
        simulatePrintTime();
        dispatchIdlePrinters();
    }

    /**
     * Checks all currently PRINTING jobs. If a job has been printing for at least
     * the configured print duration (default 1 minute), mark it COMPLETED, return the
     * printer to IDLE, and send an email notification.
     */
    public void simulatePrintTime() {
        List<Job> printingJobs = jobRepository.findByStatus(JobStatus.PRINTING);
        Instant now = clock.instant();

        for (Job job : printingJobs) {
            Instant started = printingStartedAt.computeIfAbsent(job.getId(), id -> resolveStartTime(job));
            updateTelemetry(job, started, now);
            if (job.getRemainingSeconds() <= 0) {
                completeJob(job);
            }
        }
    }

    /**
     * Finds all printers currently in IDLE status and assigns each the oldest matching QUEUED job.
     */
    public void dispatchIdlePrinters() {
        List<Printer> idlePrinters = printerRepository.findByStatus("IDLE");
        if (idlePrinters.isEmpty()) {
            return;
        }

        List<Job> queuedJobs = jobRepository.findByStatusOrderByQueuedAtAsc(JobStatus.QUEUED);
        if (queuedJobs.isEmpty()) {
            return;
        }

        Set<Long> assignedInThisTick = new HashSet<>();

        for (Printer printer : idlePrinters) {
            for (Job job : queuedJobs) {
                if (assignedInThisTick.contains(job.getId())) {
                    continue;
                }

                if (matchesFilament(job, printer) && matchesModel(job, printer)) {
                    assignJobToPrinter(job, printer);
                    assignedInThisTick.add(job.getId());
                    break;
                }
            }
        }
    }

    /**
     * Completes a print job, returns its printer to IDLE, and triggers user notification.
     */
    public void completeJob(Job job) {
        log.info("Completing job #{} on printer {}", job.getId(), job.getPrinterId());
        jobLifecycleService.transition(job, JobStatus.COMPLETED);
        job.setProgressPercent(100);
        job.setRemainingSeconds(0);
        jobRepository.save(job);
        printingStartedAt.remove(job.getId());

        if (job.getPrinterId() != null) {
            printerRepository.findById(job.getPrinterId()).ifPresent(printer -> {
                printer.setStatus("IDLE");
                printerRepository.save(printer);
            });
        }

        notifyFinished(job);
    }

    /**
     * Fails a print job, returns its printer to IDLE, and triggers user notification.
     */
    public void failJob(Job job) {
        log.warn("Failing job #{} on printer {}", job.getId(), job.getPrinterId());
        jobLifecycleService.transition(job, JobStatus.FAILED);
        jobRepository.save(job);
        printingStartedAt.remove(job.getId());

        if (job.getPrinterId() != null) {
            printerRepository.findById(job.getPrinterId()).ifPresent(printer -> {
                printer.setStatus("IDLE");
                printerRepository.save(printer);
            });
        }

        notifyFinished(job);
    }

    private void assignJobToPrinter(Job job, Printer printer) {
        log.info("Assigning queued job #{} to printer {} ({})", job.getId(), printer.getId(), printer.getModel());
        job.setPrinterId(printer.getId());
        jobLifecycleService.transition(job, JobStatus.PRINTING);
        job.setStartedAt(clock.instant());
        job.setPausedAt(null);
        job.setPausedDurationSeconds(0);
        job.setProgressPercent(0);
        job.setRemainingSeconds(printDuration.toSeconds());
        jobRepository.save(job);

        printer.setStatus("PRINTING");
        printerRepository.save(printer);

        printingStartedAt.put(job.getId(), clock.instant());
    }

    public Job pauseJob(Job job) {
        if (job.getStatus() != JobStatus.PRINTING) {
            throw new IllegalStateException("Only printing jobs can be paused");
        }
        jobLifecycleService.transition(job, JobStatus.PAUSED);
        job.setPausedAt(clock.instant());
        jobRepository.save(job);
        return job;
    }

    public Job resumeJob(Job job) {
        if (job.getStatus() != JobStatus.PAUSED) {
            throw new IllegalStateException("Only paused jobs can be resumed");
        }
        if (job.getPausedAt() != null) {
            long pausedSeconds = Duration.between(job.getPausedAt(), clock.instant()).getSeconds();
            job.setPausedDurationSeconds(job.getPausedDurationSeconds() + Math.max(0, pausedSeconds));
        }
        job.setPausedAt(null);
        jobLifecycleService.transition(job, JobStatus.PRINTING);
        jobRepository.save(job);
        return job;
    }

    public com.uwa.printerfarm.job.RefundDecision cancelJob(Job job) {
        com.uwa.printerfarm.job.RefundDecision decision = jobLifecycleService.cancel(job, clock.instant());
        jobRepository.save(job);
        printingStartedAt.remove(job.getId());
        releasePrinter(job);
        return decision;
    }

    private void releasePrinter(Job job) {
        if (job.getPrinterId() == null) {
            return;
        }
        printerRepository.findById(job.getPrinterId()).ifPresent(printer -> {
            if ("PRINTING".equals(printer.getStatus())) {
                printer.setStatus("IDLE");
                printerRepository.save(printer);
            }
        });
    }

    private void updateTelemetry(Job job, Instant started, Instant now) {
        long elapsedSeconds = Math.max(0, Duration.between(started, now).getSeconds()
                - job.getPausedDurationSeconds());
        long totalSeconds = Math.max(1, printDuration.toSeconds());
        long remainingSeconds = Math.max(0, totalSeconds - elapsedSeconds);
        int progressPercent = (int) Math.min(100, (elapsedSeconds * 100) / totalSeconds);

        job.setProgressPercent(progressPercent);
        job.setRemainingSeconds(remainingSeconds);
        jobRepository.save(job);
    }

    /**
     * Checks whether the job's requested filament matches the printer's loaded material.
     */
    public boolean matchesFilament(Job job, Printer printer) {
        if (job == null || printer == null) {
            return false;
        }
        String printerMaterial = printer.getCurrentMaterial();
        String jobMaterial = job.getMaterial();

        if (printerMaterial == null || printerMaterial.isBlank()) {
            return jobMaterial == null || jobMaterial.isBlank();
        }
        if (jobMaterial == null || jobMaterial.isBlank()) {
            return false;
        }
        return printerMaterial.trim().equalsIgnoreCase(jobMaterial.trim());
    }

    /**
     * Checks whether the job matches the printer's hardware model or target printer ID.
     */
    public boolean matchesModel(Job job, Printer printer) {
        if (job == null || printer == null) {
            return false;
        }
        String jobPrinter = job.getPrinterId();
        if (jobPrinter == null || jobPrinter.isBlank()) {
            return true;
        }

        String pId = printer.getId() != null ? printer.getId().trim() : "";
        String pModel = printer.getModel() != null ? printer.getModel().trim() : "";

        // Direct printer ID match
        if (pId.equalsIgnoreCase(jobPrinter.trim())) {
            return true;
        }

        // Direct model match
        if (pModel.equalsIgnoreCase(jobPrinter.trim())) {
            return true;
        }

        // Normalized matching (e.g., handling hyphens vs underscores: "prusa-xl-1" vs "PRUSA_XL")
        String normJob = jobPrinter.replace("-", "_").toUpperCase();
        String normModel = pModel.replace("-", "_").toUpperCase();
        String normId = pId.replace("-", "_").toUpperCase();

        if (!normModel.isEmpty() && (normJob.equals(normModel) || normJob.startsWith(normModel) || normModel.startsWith(normJob))) {
            return true;
        }
        if (!normId.isEmpty() && (normJob.equals(normId) || normJob.startsWith(normId) || normId.startsWith(normJob))) {
            return true;
        }

        // Cross-check if jobPrinter is another printer ID sharing the same model
        Optional<Printer> registeredOpt = printerRepository.findById(jobPrinter);
        if (registeredOpt.isPresent() && registeredOpt.get().getModel() != null && !pModel.isEmpty()) {
            return registeredOpt.get().getModel().equalsIgnoreCase(pModel);
        }

        return false;
    }

    private Instant resolveStartTime(Job job) {
        if (job.getStartedAt() != null) {
            return job.getStartedAt();
        }
        if (job.getPrinterId() != null) {
            Optional<Printer> printerOpt = printerRepository.findById(job.getPrinterId());
            if (printerOpt.isPresent() && printerOpt.get().getUpdatedAt() != null) {
                return printerOpt.get().getUpdatedAt();
            }
        }
        return clock.instant();
    }

    private void notifyFinished(Job job) {
        try {
            jobNotificationService.notifyJobFinished(job);
        } catch (Exception e) {
            log.error("Failed to send notification for job #{} (status: {})", job.getId(), job.getStatus(), e);
        }
    }

    public Map<Long, Instant> getPrintingStartedAt() {
        return printingStartedAt;
    }
}

package com.uwa.printerfarm.admin;

import com.uwa.printerfarm.job.Job;
import com.uwa.printerfarm.job.JobRepository;
import com.uwa.printerfarm.job.JobStatus;
import com.uwa.printerfarm.job.RefundRequest;
import com.uwa.printerfarm.job.RefundService;
import com.uwa.printerfarm.wallet.Transaction;
import com.uwa.printerfarm.wallet.TransactionLedgerService;
import com.uwa.printerfarm.wallet.TransactionType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregates the data behind the farm manager's admin monitoring view: job
 * lists, filament/material use, revenue totals, per-printer activity, and the
 * pending-refund approval queue (MVP task: "admin monitoring view for the
 * farm manager - printers, jobs, costs, filament use").
 */
@Service
public class AdminMonitoringService {

    private final JobRepository jobRepository;
    private final TransactionLedgerService ledgerService;
    private final RefundService refundService;

    public AdminMonitoringService(JobRepository jobRepository, TransactionLedgerService ledgerService,
                                   RefundService refundService) {
        this.jobRepository = jobRepository;
        this.ledgerService = ledgerService;
        this.refundService = refundService;
    }

    public List<Job> jobs(JobStatus statusFilter) {
        List<Job> all = jobRepository.findAll();
        if (statusFilter == null) {
            return all;
        }
        return all.stream().filter(job -> job.getStatus() == statusFilter).toList();
    }

    /**
     * Total estimated grams consumed per material. Cancelled jobs are
     * excluded: the state machine only allows cancelling a still-queued job,
     * so a cancelled job never actually printed and never consumed filament.
     */
    public Map<String, BigDecimal> filamentUsageByMaterial() {
        Map<String, BigDecimal> usage = new LinkedHashMap<>();
        for (Job job : jobRepository.findAll()) {
            if (job.getStatus() == JobStatus.CANCELLED) {
                continue;
            }
            usage.merge(job.getMaterial(), job.getEstimatedGrams(), BigDecimal::add);
        }
        return usage;
    }

    public CostSummary costSummary() {
        BigDecimal charged = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        for (Transaction transaction : ledgerService.all()) {
            if (transaction.getType() == TransactionType.DEBIT) {
                charged = charged.add(transaction.getAmount());
            } else if (transaction.getType() == TransactionType.REFUND) {
                refunded = refunded.add(transaction.getAmount());
            }
        }
        return new CostSummary(charged, refunded, charged.subtract(refunded));
    }

    public List<PrinterActivity> printerActivity() {
        Map<String, Map<JobStatus, Long>> countsByPrinter = new LinkedHashMap<>();
        for (Job job : jobRepository.findAll()) {
            countsByPrinter
                    .computeIfAbsent(job.getPrinterId(), id -> new EnumMap<>(JobStatus.class))
                    .merge(job.getStatus(), 1L, Long::sum);
        }
        return countsByPrinter.entrySet().stream()
                .map(entry -> new PrinterActivity(entry.getKey(), Map.copyOf(entry.getValue())))
                .toList();
    }

    public List<RefundRequest> pendingRefundRequests() {
        return refundService.pending();
    }

    /**
     * Daily filament usage and revenue for the last {@code days} calendar
     * days (UTC, inclusive of today), zero-filled so a chart over the range
     * has no gaps. Gives the farm manager long-term usage/consumption
     * tracking rather than just an all-time snapshot.
     */
    public List<UsageTrendPoint> usageTrends(int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("days must be positive");
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(days - 1L);

        Map<LocalDate, BigDecimal> filamentByDate = new TreeMap<>();
        Map<LocalDate, Long> jobCountByDate = new TreeMap<>();
        for (Job job : jobRepository.findAll()) {
            LocalDate date = job.getQueuedAt().atZone(ZoneOffset.UTC).toLocalDate();
            if (date.isBefore(start) || date.isAfter(today)) {
                continue;
            }
            jobCountByDate.merge(date, 1L, Long::sum);
            if (job.getStatus() != JobStatus.CANCELLED) {
                filamentByDate.merge(date, job.getEstimatedGrams(), BigDecimal::add);
            }
        }

        Map<LocalDate, BigDecimal> chargedByDate = new TreeMap<>();
        Map<LocalDate, BigDecimal> refundedByDate = new TreeMap<>();
        for (Transaction transaction : ledgerService.all()) {
            LocalDate date = transaction.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate();
            if (date.isBefore(start) || date.isAfter(today)) {
                continue;
            }
            if (transaction.getType() == TransactionType.DEBIT) {
                chargedByDate.merge(date, transaction.getAmount(), BigDecimal::add);
            } else if (transaction.getType() == TransactionType.REFUND) {
                refundedByDate.merge(date, transaction.getAmount(), BigDecimal::add);
            }
        }

        List<UsageTrendPoint> points = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            points.add(new UsageTrendPoint(
                    date,
                    filamentByDate.getOrDefault(date, BigDecimal.ZERO),
                    chargedByDate.getOrDefault(date, BigDecimal.ZERO),
                    refundedByDate.getOrDefault(date, BigDecimal.ZERO),
                    jobCountByDate.getOrDefault(date, 0L)));
        }
        return points;
    }
}

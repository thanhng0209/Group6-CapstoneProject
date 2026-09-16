package com.uwa.printerfarm.admin;

import com.uwa.printerfarm.job.Job;
import com.uwa.printerfarm.job.JobStatus;
import com.uwa.printerfarm.job.RefundRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Read-only endpoints backing the farm manager's admin monitoring view.
 */
@RestController
public class AdminMonitoringController {

    private final AdminMonitoringService monitoringService;

    public AdminMonitoringController(AdminMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/api/admin/jobs")
    public List<Job> jobs(@RequestParam(required = false) JobStatus status) {
        return monitoringService.jobs(status);
    }

    @GetMapping("/api/admin/printers")
    public List<PrinterActivity> printers() {
        return monitoringService.printerActivity();
    }

    @GetMapping("/api/admin/costs")
    public CostSummary costs() {
        return monitoringService.costSummary();
    }

    @GetMapping("/api/admin/filament-usage")
    public Map<String, BigDecimal> filamentUsage() {
        return monitoringService.filamentUsageByMaterial();
    }

    @GetMapping("/api/admin/refunds/pending")
    public List<RefundRequest> pendingRefunds() {
        return monitoringService.pendingRefundRequests();
    }
}

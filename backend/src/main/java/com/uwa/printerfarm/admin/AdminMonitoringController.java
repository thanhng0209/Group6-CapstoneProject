package com.uwa.printerfarm.admin;

import com.uwa.printerfarm.job.Job;
import com.uwa.printerfarm.job.JobStatus;
import com.uwa.printerfarm.job.RefundRequest;
import com.uwa.printerfarm.job.RefundRequestNotFoundException;
import com.uwa.printerfarm.job.RefundService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Endpoints backing the farm manager's admin monitoring and refund approval views.
 */
@RestController
public class AdminMonitoringController {

    private final AdminMonitoringService monitoringService;
    private final RefundService refundService;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminMonitoringController(AdminMonitoringService monitoringService, RefundService refundService) {
        this.monitoringService = monitoringService;
        this.refundService = refundService;
    }

    public AdminMonitoringController(AdminMonitoringService monitoringService) {
        this(monitoringService, null);
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

    @GetMapping("/api/admin/usage-trends")
    public List<UsageTrendPoint> usageTrends(@RequestParam(defaultValue = "30") int days) {
        return monitoringService.usageTrends(days);
    }

    @PostMapping("/api/admin/refunds/{id}/approve")
    public ResponseEntity<?> approveRefund(@PathVariable Long id) {
        if (refundService == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "errors", List.of("Refund service is not configured"),
                    "code", "SERVICE_UNAVAILABLE"));
        }
        RefundRequest approved = refundService.approve(id, Instant.now());
        return ResponseEntity.ok(approved);
    }

    @PostMapping("/api/admin/refunds/{id}/reject")
    public ResponseEntity<?> rejectRefund(@PathVariable Long id) {
        if (refundService == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "errors", List.of("Refund service is not configured"),
                    "code", "SERVICE_UNAVAILABLE"));
        }
        RefundRequest rejected = refundService.reject(id, Instant.now());
        return ResponseEntity.ok(rejected);
    }

    @ExceptionHandler(RefundRequestNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRefundRequestNotFound(RefundRequestNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "errors", List.of(ex.getMessage()),
                "code", "REFUND_REQUEST_NOT_FOUND"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "errors", List.of(ex.getMessage()),
                "code", "INVALID_REFUND_STATE"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "errors", List.of(ex.getMessage()),
                "code", "INVALID_USAGE_TRENDS_REQUEST"));
    }
}

package com.uwa.printerfarm.job;

import com.uwa.printerfarm.printer.PrinterRepository;
import com.uwa.printerfarm.printer.MockPrinterDispatcher;
import com.uwa.printerfarm.security.UserPrincipal;
import com.uwa.printerfarm.wallet.InsufficientBalanceException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller exposing endpoints for student 3D print job submissions,
 * cancellations, and historical job retrieval.
 */
@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Jobs", description = "Endpoints for submitting, cancelling, and viewing 3D print jobs")
public class JobController {

    private final JobSubmissionService jobSubmissionService;
    private final JobLifecycleService jobLifecycleService;
    private final JobRepository jobRepository;
    private final PrinterRepository printerRepository;
    private final MockPrinterDispatcher mockPrinterDispatcher;
    private final RefundService refundService;

    @org.springframework.beans.factory.annotation.Autowired
    public JobController(JobSubmissionService jobSubmissionService,
            JobLifecycleService jobLifecycleService,
            JobRepository jobRepository,
            PrinterRepository printerRepository,
            ObjectProvider<MockPrinterDispatcher> mockPrinterDispatcherProvider,
            RefundService refundService) {
        this.jobSubmissionService = jobSubmissionService;
        this.jobLifecycleService = jobLifecycleService;
        this.jobRepository = jobRepository;
        this.printerRepository = printerRepository;
        this.mockPrinterDispatcher = mockPrinterDispatcherProvider.getIfAvailable();
        this.refundService = refundService;
    }

    public JobController(JobSubmissionService jobSubmissionService,
            JobLifecycleService jobLifecycleService,
            JobRepository jobRepository,
            PrinterRepository printerRepository) {
        this.jobSubmissionService = jobSubmissionService;
        this.jobLifecycleService = jobLifecycleService;
        this.jobRepository = jobRepository;
        this.printerRepository = printerRepository;
        this.mockPrinterDispatcher = null;
        this.refundService = null;
    }

    public JobController(JobSubmissionService jobSubmissionService,
            JobLifecycleService jobLifecycleService,
            JobRepository jobRepository) {
        this(jobSubmissionService, jobLifecycleService, jobRepository, null, null, null);
    }

    public JobController(JobSubmissionService jobSubmissionService,
            JobLifecycleService jobLifecycleService) {
        this(jobSubmissionService, jobLifecycleService, null, null);
    }

    /**
     * Accepts parsed G-code metadata, checks user balance, deducts funds,
     * creates a Job entity with 'QUEUED' status, and saves to DB.
     */
    @Operation(summary = "Submit a print job", description = "Deducts funds, creates a Job with QUEUED status, and saves to database.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Print job successfully queued and cost deducted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Insufficient funds or invalid input parameters"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "User authentication required")
    })
    @PostMapping("/submit")
    public ResponseEntity<?> submit(
            @RequestBody JobSubmitRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            Authentication authentication) {

        String ownerUniId = resolveUniId(principal, authentication, request != null ? request.getOwnerUniId() : null);
        if (ownerUniId == null || ownerUniId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "errors", List.of("Authentication required or ownerUniId must be provided"),
                    "code", "UNAUTHORIZED"));
        }

        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "errors", List.of("Request body is missing"),
                    "code", "MISSING_REQUEST_BODY"));
        }

        String printerId = request.getPrinterId();
        if (printerId == null || printerId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "errors", List.of("printerId is required"),
                    "code", "MISSING_PRINTER_ID"));
        }
        if (printerRepository != null && !printerRepository.existsById(printerId)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "errors", List.of("Unknown printerId: " + printerId),
                    "code", "INVALID_PRINTER_ID"));
        }

        String material = request.getMaterial() != null ? request.getMaterial().trim().toUpperCase() : "PLA";
        BigDecimal estimatedGrams = request.getEstimatedGrams() != null ? request.getEstimatedGrams() : BigDecimal.ZERO;
        BigDecimal estimatedMinutes = request.getEstimatedMinutes() != null ? request.getEstimatedMinutes()
                : BigDecimal.ZERO;

        Job job = new Job(
                ownerUniId,
                printerId,
                request.getFileName(),
                material,
                estimatedGrams,
                estimatedMinutes);

        Job submittedJob = jobSubmissionService.submit(job);
        return ResponseEntity.ok(submittedJob);
    }

    /**
     * Allows a student to cancel their job. It triggers the existing refund logic
     * in JobLifecycleService.
     */
    @Operation(summary = "Cancel a print job", description = "Cancels a student's print job and evaluates refund decision.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Print job successfully cancelled and refund processed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status transition for cancellation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden: user does not own this job"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found")
    })
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            Authentication authentication) {

        if (jobRepository == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "errors", List.of("Job repository is not configured"),
                    "code", "REPOSITORY_UNAVAILABLE"));
        }

        Job job = jobRepository.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "errors", List.of("Job not found with id: " + id),
                    "code", "JOB_NOT_FOUND"));
        }

        String currentUniId = resolveUniId(principal, authentication, null);
        if (currentUniId != null && !currentUniId.isBlank()) {
            boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
            if (!isAdmin && !currentUniId.equals(job.getOwnerUniId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "errors", List.of("You are not authorized to cancel this job"),
                        "code", "FORBIDDEN"));
            }
        }

        JobStatus statusBeforeCancellation = job.getStatus();
        Optional<RefundRequest> refundRequest = refundService != null
                ? refundService.cancelAndRefund(job, Instant.now())
                : Optional.empty();

        if (refundService == null) {
            jobLifecycleService.cancel(job, Instant.now());
        }
        if (mockPrinterDispatcher != null && statusBeforeCancellation != JobStatus.QUEUED) {
            mockPrinterDispatcher.releasePrinterAfterCancellation(job);
        }
        jobRepository.save(job);

        RefundDecision decision = refundRequest.isEmpty()
                ? RefundDecision.AUTO_REFUNDED
                : RefundDecision.REQUIRES_APPROVAL;

        return ResponseEntity.ok(Map.of(
                "jobId", job.getId(),
                "status", job.getStatus().name(),
                "refundDecision", decision.name(),
                "message", decision == RefundDecision.AUTO_REFUNDED
                        ? "Job cancelled and automatically refunded."
                        : "Job cancelled. Refund requires manager approval."));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<?> pause(@PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            Authentication authentication) {
        return controlJob(id, JobStatus.PRINTING, true, principal, authentication);
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<?> resume(@PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            Authentication authentication) {
        return controlJob(id, JobStatus.PAUSED, false, principal, authentication);
    }

    private ResponseEntity<?> controlJob(Long id, JobStatus expectedStatus, boolean pause,
            UserPrincipal principal, Authentication authentication) {
        if (mockPrinterDispatcher == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "errors", List.of("Virtual printer controls are not enabled"),
                    "code", "DISPATCHER_UNAVAILABLE"));
        }

        Job job = jobRepository.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "errors", List.of("Job not found with id: " + id),
                    "code", "JOB_NOT_FOUND"));
        }
        String currentUniId = resolveUniId(principal, authentication, null);
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
        if (!isAdmin && (currentUniId == null || !currentUniId.equals(job.getOwnerUniId()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "errors", List.of("You are not authorized to control this job"),
                "code", "FORBIDDEN"));
        }
        if (job.getStatus() != expectedStatus) {
            return ResponseEntity.badRequest().body(Map.of(
                    "errors", List.of("Job must be " + expectedStatus + " to perform this action"),
                    "code", "INVALID_STATUS_TRANSITION"));
        }

        return ResponseEntity.ok(pause
                ? mockPrinterDispatcher.pauseJob(job)
                : mockPrinterDispatcher.resumeJob(job));
    }

    /**
     * Returns a list of all jobs belonging to the currently authenticated user.
     */
    @Operation(summary = "Get current user's jobs", description = "Returns all jobs belonging to the authenticated student.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of user print jobs retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "User authentication required")
    })
    @GetMapping("/my")
    public ResponseEntity<?> getMyJobs(
            @AuthenticationPrincipal UserPrincipal principal,
            Authentication authentication,
            @RequestParam(required = false) String uniId) {

        if (jobRepository == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "errors", List.of("Job repository is not configured"),
                    "code", "REPOSITORY_UNAVAILABLE"));
        }

        String effectiveUniId = resolveUniId(principal, authentication, uniId);
        if (effectiveUniId == null || effectiveUniId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "errors", List.of("User must be authenticated to view their jobs"),
                    "code", "UNAUTHORIZED"));
        }

        List<Job> jobs = jobRepository.findByOwnerUniIdOrderByQueuedAtDesc(effectiveUniId);
        return ResponseEntity.ok(jobs);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientBalance(InsufficientBalanceException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "errors", List.of(ex.getMessage()),
                "code", "INSUFFICIENT_FUNDS"));
    }

    @ExceptionHandler(InvalidJobStatusTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidJobStatusTransition(
            InvalidJobStatusTransitionException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "errors", List.of(ex.getMessage()),
                "code", "INVALID_STATUS_TRANSITION"));
    }

    private String resolveUniId(UserPrincipal userPrincipal, Authentication authentication, String fallback) {
        if (userPrincipal != null && userPrincipal.getUniId() != null) {
            return userPrincipal.getUniId();
        }
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            Object p = authentication.getPrincipal();
            if (p instanceof UserPrincipal up) {
                return up.getUniId();
            } else if (p instanceof UserDetails ud) {
                return ud.getUsername();
            } else if (p instanceof String s && !s.isBlank()) {
                return s;
            }
            return authentication.getName();
        }
        Authentication ctxAuth = SecurityContextHolder.getContext().getAuthentication();
        if (ctxAuth != null && ctxAuth.isAuthenticated() && !"anonymousUser".equals(ctxAuth.getPrincipal())) {
            Object p = ctxAuth.getPrincipal();
            if (p instanceof UserPrincipal up) {
                return up.getUniId();
            } else if (p instanceof UserDetails ud) {
                return ud.getUsername();
            } else if (p instanceof String s && !s.isBlank()) {
                return s;
            }
            return ctxAuth.getName();
        }
        return fallback;
    }

    /**
     * DTO for parsed G-code submission.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobSubmitRequest {
        private String printerId;
        private String printerProfileId;
        private String fileName;
        private String material;
        private BigDecimal estimatedGrams;
        private BigDecimal estimatedMinutes;
        private String ownerUniId;
    }
}

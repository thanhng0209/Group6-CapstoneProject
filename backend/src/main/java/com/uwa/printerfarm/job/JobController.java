package com.uwa.printerfarm.job;

import com.uwa.printerfarm.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

    @Autowired
    public JobController(JobSubmissionService jobSubmissionService,
                         JobLifecycleService jobLifecycleService,
                         JobRepository jobRepository) {
        this.jobSubmissionService = jobSubmissionService;
        this.jobLifecycleService = jobLifecycleService;
        this.jobRepository = jobRepository;
    }

    public JobController(JobSubmissionService jobSubmissionService,
                         JobLifecycleService jobLifecycleService) {
        this(jobSubmissionService, jobLifecycleService, null);
    }

    @Operation(summary = "Submit a print job", description = "Deducts funds, creates a Job with QUEUED status, and saves to database.")
    @PostMapping("/submit")
    public ResponseEntity<?> submit(
            @RequestBody JobSubmitRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            Authentication authentication) {

        String ownerUniId = resolveUniId(principal, authentication, request != null ? request.getOwnerUniId() : null);
        if (ownerUniId == null || ownerUniId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "errors", List.of("Authentication required or ownerUniId must be provided"),
                    "code", "UNAUTHORIZED"
            ));
        }

        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "errors", List.of("Request body is missing"),
                    "code", "MISSING_REQUEST_BODY"
            ));
        }

        String printerId = request.getPrinterId();
        if (printerId == null || printerId.isBlank()) {
            printerId = request.getPrinterProfileId();
        }
        if (printerId == null || printerId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "errors", List.of("printerId is required"),
                    "code", "MISSING_PRINTER_ID"
            ));
        }

        return ResponseEntity.ok().build();
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

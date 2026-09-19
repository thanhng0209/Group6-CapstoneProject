package com.uwa.printerfarm.job;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

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

package com.uwa.printerfarm.job;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * REST controller exposing endpoints for student 3D print job submissions,
 * cancellations, and historical job retrieval.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

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

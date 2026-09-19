package com.uwa.printerfarm.job;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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

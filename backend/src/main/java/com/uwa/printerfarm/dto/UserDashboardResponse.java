package com.uwa.printerfarm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class UserDashboardResponse {

    private String uniId;

    private String fullName;

    private String email;

    private String role;

    // Balance expressed in dollars for the frontend.
    private double balance;

    // Current active print jobs.
    private List<PrintJobSummary> currentJobs;

    // Completed/failed print jobs.
    private List<PrintJobSummary> printHistory;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class PrintJobSummary {

        private Long jobId;

        private String fileName;

        private String printerName;

        private String status;

        private String submittedAt;

        private int progressPercent;

        private long remainingSeconds;
    }
}
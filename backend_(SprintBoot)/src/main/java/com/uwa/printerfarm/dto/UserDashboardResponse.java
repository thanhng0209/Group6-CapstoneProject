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

    // Balance expressed in dollars for the frontend (converted from balanceCents).
    private double balance;

    // NOTE: PrintJob doesn't exist yet — that table/entity belongs to the
    // "File & G-code Validation" / "Job & Financial Management" workstreams.
    // Until those land, this returns an empty list so the dashboard still renders end-to-end.
    private List<PrintJobSummary> currentJobs;
    private List<PrintJobSummary> printHistory;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class PrintJobSummary {
        private Long jobId;
        private String fileName;
        private String printerName;
        private String status; // QUEUED, PRINTING, COMPLETED, FAILED
        private String submittedAt;
    }
}

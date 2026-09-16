package com.uwa.printerfarm.admin;

import com.uwa.printerfarm.job.JobStatus;

import java.util.Map;

/**
 * Per-printer job-status breakdown. Derived purely from job records because
 * Workstream 3's Prusa Connect integration (real printer telemetry) is not
 * wired up yet; this stands in for it until that lands.
 */
public final class PrinterActivity {

    private final String printerId;
    private final Map<JobStatus, Long> jobCountsByStatus;

    PrinterActivity(String printerId, Map<JobStatus, Long> jobCountsByStatus) {
        this.printerId = printerId;
        this.jobCountsByStatus = jobCountsByStatus;
    }

    public String getPrinterId() {
        return printerId;
    }

    public Map<JobStatus, Long> getJobCountsByStatus() {
        return jobCountsByStatus;
    }
}

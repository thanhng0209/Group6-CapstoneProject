package com.uwa.printerfarm.admin;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day's worth of aggregated farm activity for the admin usage/revenue
 * trend view: how much filament was consumed and how much was charged vs.
 * refunded, so the farm manager can see usage and revenue over time rather
 * than just an all-time total.
 */
public final class UsageTrendPoint {

    private final LocalDate date;
    private final BigDecimal filamentGrams;
    private final BigDecimal charged;
    private final BigDecimal refunded;
    private final long jobCount;

    UsageTrendPoint(LocalDate date, BigDecimal filamentGrams, BigDecimal charged,
                     BigDecimal refunded, long jobCount) {
        this.date = date;
        this.filamentGrams = filamentGrams;
        this.charged = charged;
        this.refunded = refunded;
        this.jobCount = jobCount;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getFilamentGrams() {
        return filamentGrams;
    }

    public BigDecimal getCharged() {
        return charged;
    }

    public BigDecimal getRefunded() {
        return refunded;
    }

    public long getJobCount() {
        return jobCount;
    }
}

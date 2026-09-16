package com.uwa.printerfarm.admin;

import java.math.BigDecimal;

/**
 * Aggregate revenue figures for the admin monitoring view.
 */
public final class CostSummary {

    private final BigDecimal totalCharged;
    private final BigDecimal totalRefunded;
    private final BigDecimal netRevenue;

    CostSummary(BigDecimal totalCharged, BigDecimal totalRefunded, BigDecimal netRevenue) {
        this.totalCharged = totalCharged;
        this.totalRefunded = totalRefunded;
        this.netRevenue = netRevenue;
    }

    public BigDecimal getTotalCharged() {
        return totalCharged;
    }

    public BigDecimal getTotalRefunded() {
        return totalRefunded;
    }

    public BigDecimal getNetRevenue() {
        return netRevenue;
    }
}

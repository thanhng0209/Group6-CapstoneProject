package com.uwa.printerfarm.cost;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CostCalculationServiceTest {

    private final CostCalculationService costCalculationService = new CostCalculationService();

    @Test
    void calculatesCostFromMaterialAndPrintTime() {
        BigDecimal cost = costCalculationService.calculate("PLA", new BigDecimal("100"), new BigDecimal("60"));

        // 100g * 0.05 + 60min * 0.02 = 5.00 + 1.20 = 6.20
        assertThat(cost).isEqualByComparingTo("6.20");
    }

    @Test
    void fallsBackToDefaultRateForUnknownMaterial() {
        BigDecimal cost = costCalculationService.calculate("NYLON", new BigDecimal("100"), new BigDecimal("0"));

        assertThat(cost).isEqualByComparingTo("6.00");
    }

    @Test
    void isCaseInsensitiveForMaterialLookup() {
        BigDecimal cost = costCalculationService.calculate("pla", new BigDecimal("100"), new BigDecimal("0"));

        assertThat(cost).isEqualByComparingTo("5.00");
    }
}

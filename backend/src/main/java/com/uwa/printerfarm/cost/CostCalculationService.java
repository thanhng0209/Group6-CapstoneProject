package com.uwa.printerfarm.cost;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Centralises print-cost calculation (per Risk Register: "Cost calculation
 * incorrect" is mitigated by centralising this logic in one place).
 */
@Service
public class CostCalculationService {

    // Placeholder per-gram material rates pending confirmed pricing from the client.
    private static final Map<String, BigDecimal> MATERIAL_RATE_PER_GRAM = Map.of(
            "PLA", new BigDecimal("0.05"),
            "PETG", new BigDecimal("0.07"),
            "ABS", new BigDecimal("0.06")
    );

    private static final BigDecimal DEFAULT_MATERIAL_RATE_PER_GRAM = new BigDecimal("0.06");
    private static final BigDecimal MACHINE_RATE_PER_MINUTE = new BigDecimal("0.02");

    public BigDecimal calculate(String material, BigDecimal estimatedGrams, BigDecimal estimatedMinutes) {
        BigDecimal materialRate = MATERIAL_RATE_PER_GRAM.getOrDefault(
                material == null ? null : material.toUpperCase(),
                DEFAULT_MATERIAL_RATE_PER_GRAM);

        BigDecimal materialCost = materialRate.multiply(estimatedGrams);
        BigDecimal machineCost = MACHINE_RATE_PER_MINUTE.multiply(estimatedMinutes);

        return materialCost.add(machineCost).setScale(2, RoundingMode.HALF_UP);
    }
}

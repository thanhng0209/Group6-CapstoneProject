package com.uwa.printerfarm.printer;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory registry of approved Prusa printer profiles.
 *
 * <p>Printer specifications are sourced from the official Prusa product pages
 * and reflect the hardware installed at the UWA 3D Print Farm.  Update this
 * class (or migrate to a database table) when new printer models are added
 * or existing specs change.</p>
 *
 * <h3>Registered printers</h3>
 * <ul>
 *   <li>Prusa XL (multi-tool, large format)</li>
 *   <li>Prusa Core One (enclosed CoreXY)</li>
 *   <li>Prusa MK4S (i3-style workhorse)</li>
 * </ul>
 */
@Component
public class PrinterProfileRegistry {

    private final Map<String, PrinterProfile> profiles;

    public PrinterProfileRegistry() {
        this.profiles = Map.of(
                "PRUSA_XL", new PrinterProfile(
                        "PRUSA_XL",
                        "Prusa XL",
                        new BigDecimal("360"),  // X mm
                        new BigDecimal("360"),  // Y mm
                        new BigDecimal("360"),  // Z mm
                        Set.of(
                                new BigDecimal("0.25"),
                                new BigDecimal("0.4"),
                                new BigDecimal("0.6"),
                                new BigDecimal("0.8")
                        ),
                        new BigDecimal("0.05"), // layer height min mm
                        new BigDecimal("0.35"), // layer height max mm
                        Set.of("PLA", "PETG", "ABS", "ASA", "TPU", "FLEX")
                ),

                "PRUSA_CORE_ONE", new PrinterProfile(
                        "PRUSA_CORE_ONE",
                        "Prusa Core One",
                        new BigDecimal("250"),  // X mm
                        new BigDecimal("220"),  // Y mm
                        new BigDecimal("270"),  // Z mm
                        Set.of(
                                new BigDecimal("0.25"),
                                new BigDecimal("0.4"),
                                new BigDecimal("0.6")
                        ),
                        new BigDecimal("0.05"),
                        new BigDecimal("0.30"),
                        Set.of("PLA", "PETG", "ABS", "ASA", "FLEX")
                ),

                "PRUSA_MK4S", new PrinterProfile(
                        "PRUSA_MK4S",
                        "Prusa MK4S",
                        new BigDecimal("250"),  // X mm
                        new BigDecimal("210"),  // Y mm
                        new BigDecimal("220"),  // Z mm
                        Set.of(
                                new BigDecimal("0.25"),
                                new BigDecimal("0.4"),
                                new BigDecimal("0.6")
                        ),
                        new BigDecimal("0.05"),
                        new BigDecimal("0.30"),
                        Set.of("PLA", "PETG", "ABS", "ASA")
                )
        );
    }

    /**
     * Returns the {@link PrinterProfile} for the given canonical ID, or
     * {@link Optional#empty()} if no such printer is registered.
     *
     * @param printerId canonical ID, e.g. "PRUSA_XL" (case-sensitive)
     */
    public Optional<PrinterProfile> findById(String printerId) {
        return Optional.ofNullable(profiles.get(printerId));
    }

    /** Returns all registered printer profiles. */
    public Collection<PrinterProfile> findAll() {
        return profiles.values();
    }
}

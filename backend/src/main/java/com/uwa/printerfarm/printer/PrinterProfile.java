package com.uwa.printerfarm.printer;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Immutable descriptor for a single Prusa printer model's approved print
 * parameters.  Instances are created by {@link PrinterProfileRegistry} at
 * application start-up and are never mutated at runtime.
 *
 * <p>All dimensional fields use millimetres; temperatures use degrees Celsius.</p>
 */
public record PrinterProfile(

        /**
         * Canonical identifier used to match against the
         * {@code printer_model} comment in G-code files, e.g. "PRUSA_XL".
         */
        String id,

        /** Human-readable display name shown in the UI, e.g. "Prusa XL". */
        String displayName,

        /** Maximum allowed print volume in X (mm). */
        BigDecimal maxVolumeX,

        /** Maximum allowed print volume in Y (mm). */
        BigDecimal maxVolumeY,

        /** Maximum allowed print volume in Z (mm). */
        BigDecimal maxVolumeZ,

        /**
         * Set of nozzle diameters (mm) that are approved for this printer.
         * Any G-code specifying a diameter not in this set will be rejected.
         */
        Set<BigDecimal> allowedNozzleDiameters,

        /** Minimum allowed layer height (mm), inclusive. */
        BigDecimal layerHeightMin,

        /** Maximum allowed layer height (mm), inclusive. */
        BigDecimal layerHeightMax,

        /**
         * Set of material types (upper-case) approved for this printer,
         * e.g. {@code {"PLA", "PETG", "ABS"}}.
         */
        Set<String> allowedMaterials
) {}

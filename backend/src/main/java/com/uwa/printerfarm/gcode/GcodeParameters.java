package com.uwa.printerfarm.gcode;

import java.math.BigDecimal;

/**
 * Immutable value object holding all parameters extracted from a PrusaSlicer
 * G-code file's comment header.
 *
 * <p>Fields may be {@code null} when the corresponding comment was absent from
 * the file – callers should treat null as "not specified" rather than a parse
 * error; compatibility validation is responsible for deciding which fields are
 * mandatory for a given printer model.</p>
 */
public record GcodeParameters(

        /** Value of {@code ; printer_model = ...} comment, e.g. "XL", "CORE_ONE", "MK4S". */
        String printerProfileId,

        /** Maximum X dimension of the print in millimetres (from bounding-box comments). */
        BigDecimal printVolumeX,

        /** Maximum Y dimension of the print in millimetres. */
        BigDecimal printVolumeY,

        /** Maximum Z dimension of the print in millimetres. */
        BigDecimal printVolumeZ,

        /** Nozzle diameter in millimetres, e.g. 0.4 (from {@code ; nozzle_diameter = ...}). */
        BigDecimal nozzleDiameter,

        /** Layer height in millimetres (from {@code ; layer_height = ...}). */
        BigDecimal layerHeight,

        /** Filament type string, e.g. "PLA", "PETG" (from {@code ; filament_type = ...}). */
        String material,

        /** Nozzle/extruder temperature in °C (from {@code ; temperature = ...}). */
        Integer nozzleTemp,

        /** Bed temperature in °C (from {@code ; bed_temperature = ...}). */
        Integer bedTemp,

        /** Estimated filament weight in grams (from {@code ; total weight = ...}). */
        BigDecimal estimatedGrams,

        /** Estimated print duration in minutes (converted from PrusaSlicer's h/m/s string). */
        BigDecimal estimatedMinutes,

        /**
         * {@code true} when the file starts with the PrusaSlicer binary G-code
         * magic bytes {@code 0x9D 0x8B 0x0A 0x0B}. Binary files cannot be
         * parsed and must be rejected at the upload boundary.
         */
        boolean isBinaryGcode
) {}

package com.uwa.printerfarm.gcode;

import com.uwa.printerfarm.printer.ValidationResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO returned by {@code POST /api/gcode/upload} on a successful parse.
 *
 * <p>Contains both the raw parameters extracted from the G-code header and
 * the outcome of the compatibility check so the frontend can display a
 * single consolidated response panel.</p>
 */
public record GcodeUploadResponse(

        // ── Extracted G-code parameters ────────────────────────────────────
        String  printerProfileId,
        BigDecimal printVolumeX,
        BigDecimal printVolumeY,
        BigDecimal printVolumeZ,
        BigDecimal nozzleDiameter,
        BigDecimal layerHeight,
        String  material,
        Integer nozzleTemp,
        Integer bedTemp,
        BigDecimal estimatedGrams,
        BigDecimal estimatedMinutes,

        // ── Compatibility validation outcome ───────────────────────────────
        boolean compatible,

        /**
         * Human-readable list of constraint violations; empty when
         * {@code compatible} is {@code true}.
         */
        List<String> compatibilityErrors
) {

    /** Maps a parsed result + validation outcome into a response DTO. */
    public static GcodeUploadResponse from(GcodeParameters params, ValidationResult validation) {
        return new GcodeUploadResponse(
                params.printerProfileId(),
                params.printVolumeX(),
                params.printVolumeY(),
                params.printVolumeZ(),
                params.nozzleDiameter(),
                params.layerHeight(),
                params.material(),
                params.nozzleTemp(),
                params.bedTemp(),
                params.estimatedGrams(),
                params.estimatedMinutes(),
                validation.valid(),
                validation.errors()
        );
    }
}

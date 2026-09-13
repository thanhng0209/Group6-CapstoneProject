package com.uwa.printerfarm.printer;

import com.uwa.printerfarm.gcode.GcodeParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CompatibilityValidationService}.
 *
 * Includes the project acceptance check:
 *   "Upload an XL file while selecting Core One → correctly rejected with
 *    compatibility errors."
 */
class CompatibilityValidationServiceTest {

    private CompatibilityValidationService service;

    @BeforeEach
    void setUp() {
        service = new CompatibilityValidationService(new PrinterProfileRegistry());
    }

    // ── Happy paths ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("XL G-code uploaded for XL printer → PASSES validation")
    void xlFileOnXlPrinter_passes() {
        GcodeParameters params = xlParams(
                new BigDecimal("125.3"), new BigDecimal("98.5"), new BigDecimal("42.0"));

        ValidationResult result = service.validate(params, "PRUSA_XL");

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("Core One file on Core One printer → PASSES validation")
    void coreOneFileOnCoreOnePrinter_passes() {
        GcodeParameters params = new GcodeParameters(
                "PRUSA_CORE_ONE",
                new BigDecimal("200"), new BigDecimal("180"), new BigDecimal("100"),
                new BigDecimal("0.4"),
                new BigDecimal("0.20"),
                "PLA", 215, 60,
                new BigDecimal("12.5"), new BigDecimal("60.0"),
                false
        );

        ValidationResult result = service.validate(params, "PRUSA_CORE_ONE");

        assertThat(result.valid()).isTrue();
    }

    // ── ACCEPTANCE CHECK: XL file → Core One → rejected ────────────────────

    @Test
    @DisplayName("ACCEPTANCE: XL G-code uploaded for Core One → rejected with profile + volume errors")
    void xlFileOnCoreOnePrinter_rejected() {
        // File represents: printer_model=XL, dims 310×280×50 mm (exceeds Core One)
        GcodeParameters params = new GcodeParameters(
                "PRUSA_XL",                     // printer_model comment in the file
                new BigDecimal("310.0"),         // X – exceeds Core One max (250)
                new BigDecimal("280.0"),         // Y – exceeds Core One max (220)
                new BigDecimal("50.0"),          // Z – within Core One max (270)
                new BigDecimal("0.4"),
                new BigDecimal("0.20"),
                "PLA", 215, 60,
                new BigDecimal("45.8"), new BigDecimal("192.0"),
                false
        );

        ValidationResult result = service.validate(params, "PRUSA_CORE_ONE");

        assertThat(result.valid()).isFalse();

        // Must contain at least the profile mismatch error
        assertThat(result.errors())
                .anySatisfy(e -> assertThat(e).containsIgnoringCase("profile mismatch")
                        .containsIgnoringCase("XL"));

        // Must contain volume violation for X axis
        assertThat(result.errors())
                .anySatisfy(e -> assertThat(e).containsIgnoringCase("X axis")
                        .containsIgnoringCase("310"));

        // Must contain volume violation for Y axis
        assertThat(result.errors())
                .anySatisfy(e -> assertThat(e).containsIgnoringCase("Y axis")
                        .containsIgnoringCase("280"));

        // Z is within bounds – must NOT be in errors
        assertThat(result.errors())
                .noneMatch(e -> e.toLowerCase().contains("z axis"));
    }

    // ── Individual rule tests ────────────────────────────────────────────────

    @Test
    @DisplayName("Unsupported nozzle diameter → rejected")
    void unsupportedNozzle_rejected() {
        GcodeParameters params = new GcodeParameters(
                "PRUSA_MK4S",
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"),
                new BigDecimal("1.0"),    // 1.0 mm nozzle not allowed on MK4S
                new BigDecimal("0.20"),
                "PLA", 215, 60,
                new BigDecimal("10"), new BigDecimal("60"),
                false
        );

        ValidationResult result = service.validate(params, "PRUSA_MK4S");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).containsIgnoringCase("nozzle"));
    }

    @Test
    @DisplayName("Layer height too high → rejected")
    void layerHeightTooHigh_rejected() {
        GcodeParameters params = new GcodeParameters(
                "PRUSA_MK4S",
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"),
                new BigDecimal("0.4"),
                new BigDecimal("0.50"),   // 0.50 mm > MK4S max 0.30 mm
                "PLA", 215, 60,
                new BigDecimal("10"), new BigDecimal("60"),
                false
        );

        ValidationResult result = service.validate(params, "PRUSA_MK4S");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).containsIgnoringCase("layer height")
                .containsIgnoringCase("maximum"));
    }

    @Test
    @DisplayName("Unapproved material → rejected")
    void unapprovedMaterial_rejected() {
        // MK4S does not support TPU
        GcodeParameters params = new GcodeParameters(
                "PRUSA_MK4S",
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"),
                new BigDecimal("0.4"),
                new BigDecimal("0.20"),
                "TPU", 230, 25,
                new BigDecimal("10"), new BigDecimal("60"),
                false
        );

        ValidationResult result = service.validate(params, "PRUSA_MK4S");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).containsIgnoringCase("TPU")
                .containsIgnoringCase("approved"));
    }

    @Test
    @DisplayName("Unknown printer ID → rejected with descriptive error")
    void unknownPrinterId_rejected() {
        GcodeParameters params = xlParams(
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"));

        ValidationResult result = service.validate(params, "PRUSA_UNKNOWN_9000");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).containsIgnoringCase("unknown printer"));
    }

    @Test
    @DisplayName("Missing printer_model comment → validation error about missing comment")
    void missingProfileId_reportedAsError() {
        GcodeParameters params = new GcodeParameters(
                null,    // no printer_model comment in file
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"),
                new BigDecimal("0.4"),
                new BigDecimal("0.20"),
                "PLA", 215, 60,
                new BigDecimal("10"), new BigDecimal("60"),
                false
        );

        ValidationResult result = service.validate(params, "PRUSA_XL");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(e ->
                assertThat(e).containsIgnoringCase("printer_model"));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Builds a minimal valid-looking XL G-code parameter set. */
    private GcodeParameters xlParams(BigDecimal x, BigDecimal y, BigDecimal z) {
        return new GcodeParameters(
                "PRUSA_XL",
                x, y, z,
                new BigDecimal("0.4"),
                new BigDecimal("0.20"),
                "PLA", 215, 60,
                new BigDecimal("18.62"), new BigDecimal("83.75"),
                false
        );
    }
}

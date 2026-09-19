package com.uwa.printerfarm.printer;

import com.uwa.printerfarm.gcode.GcodeParameters;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Validates a parsed G-code file against the approved profile for a specific
 * Prusa printer.
 *
 * <h3>Validation rules (applied in order)</h3>
 * <ol>
 *   <li><b>Unknown printer:</b> the selected {@code printerId} is not in the
 *       registry → immediate rejection.</li>
 *   <li><b>Profile mismatch:</b> the {@code printer_model} comment in the
 *       G-code does not match the printer selected by the user.</li>
 *   <li><b>Print volume:</b> any bounding-box dimension exceeds the printer's
 *       maximum build volume.</li>
 *   <li><b>Nozzle diameter:</b> the sliced nozzle diameter is not in the
 *       printer's approved nozzle set.</li>
 *   <li><b>Layer height:</b> the layer height is outside [min, max] for the
 *       printer.</li>
 *   <li><b>Material:</b> the filament type is not on the printer's approved
 *       materials list.</li>
 * </ol>
 *
 * <p>All detected violations are collected before returning so the user
 * receives a complete list in one response rather than one error at a time.</p>
 */
@Service
public class CompatibilityValidationService {

    private final PrinterProfileRegistry registry;
    private final PrinterRepository printerRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public CompatibilityValidationService(PrinterProfileRegistry registry,
                                          PrinterRepository printerRepository) {
        this.registry = registry;
        this.printerRepository = printerRepository;
    }

    public CompatibilityValidationService(PrinterProfileRegistry registry) {
        this(registry, null);
    }

    /**
     * Validates {@code params} against the profile identified by
     * {@code selectedPrinterId}.
     *
     * @param params            parameters extracted by the G-code parser
     * @param selectedPrinterId the printer the user has selected in the UI,
     *                          e.g. "PRUSA_XL_1" (physical printer) or "PRUSA_XL" (model)
     * @return a {@link ValidationResult} – never {@code null}
     */
    public ValidationResult validate(GcodeParameters params, String selectedPrinterId) {
        String profileId = selectedPrinterId;
        if (printerRepository != null && selectedPrinterId != null) {
            Optional<Printer> physicalOpt = printerRepository.findById(selectedPrinterId);
            if (physicalOpt.isEmpty()) {
                physicalOpt = printerRepository.findById(selectedPrinterId.toUpperCase());
            }
            if (physicalOpt.isPresent()) {
                profileId = physicalOpt.get().getModel();
            }
        }

        Optional<PrinterProfile> profileOpt = (profileId != null) ? registry.findById(profileId) : Optional.empty();
        if (profileOpt.isEmpty() && profileId != null) {
            profileOpt = registry.findById(profileId.toUpperCase());
        }

        if (profileOpt.isEmpty()) {
            return ValidationResult.fail(List.of(
                    "Unknown printer: '" + selectedPrinterId + "'. "
                    + "Please select a valid printer from the list."));
        }

        PrinterProfile profile = profileOpt.get();
        List<String> errors = new ArrayList<>();

        // ── Rule 1: printer_model comment must match the selected printer ──
        checkProfileId(params, profile, errors);

        // ── Rule 2: print volume ──────────────────────────────────────────
        checkVolume(params, profile, errors);

        // ── Rule 3: nozzle diameter ───────────────────────────────────────
        checkNozzle(params, profile, errors);

        // ── Rule 4: layer height ──────────────────────────────────────────
        checkLayerHeight(params, profile, errors);

        // ── Rule 5: material ─────────────────────────────────────────────
        checkMaterial(params, profile, errors);

        return errors.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(errors);
    }

    // -----------------------------------------------------------------------
    // Private rule checks
    // -----------------------------------------------------------------------

    private void checkProfileId(GcodeParameters params, PrinterProfile profile, List<String> errors) {
        String gcodeProfileId = params.printerProfileId();
        if (gcodeProfileId == null) {
            errors.add("The G-code file does not contain a 'printer_model' comment. "
                       + "Please re-slice with PrusaSlicer and ensure metadata comments are enabled.");
            return;
        }
        if (!gcodeProfileId.equalsIgnoreCase(profile.id())) {
            errors.add(String.format(
                    "Printer profile mismatch: the G-code was sliced for '%s' but you selected '%s' (%s). "
                    + "Please re-slice using the correct printer profile.",
                    gcodeProfileId, profile.id(), profile.displayName()));
        }
    }

    private void checkVolume(GcodeParameters params, PrinterProfile profile, List<String> errors) {
        checkDimension(params.printVolumeX(), profile.maxVolumeX(), "X", profile.displayName(), errors);
        checkDimension(params.printVolumeY(), profile.maxVolumeY(), "Y", profile.displayName(), errors);
        checkDimension(params.printVolumeZ(), profile.maxVolumeZ(), "Z", profile.displayName(), errors);
    }

    private void checkDimension(BigDecimal actual, BigDecimal max,
                                 String axis, String printerName, List<String> errors) {
        if (actual == null) return; // dimension not present in file – skip
        if (actual.compareTo(max) > 0) {
            errors.add(String.format(
                    "Print volume exceeds %s's build volume on the %s axis: "
                    + "G-code requires %.1f mm but the printer maximum is %.1f mm.",
                    printerName, axis, actual, max));
        }
    }

    private void checkNozzle(GcodeParameters params, PrinterProfile profile, List<String> errors) {
        BigDecimal nozzle = params.nozzleDiameter();
        if (nozzle == null) return;
        Set<BigDecimal> allowed = profile.allowedNozzleDiameters();
        boolean found = allowed.stream().anyMatch(n -> n.compareTo(nozzle) == 0);
        if (!found) {
            errors.add(String.format(
                    "Nozzle diameter %.2f mm is not supported by %s. "
                    + "Approved nozzle sizes: %s.",
                    nozzle, profile.displayName(), formatSet(allowed)));
        }
    }

    private void checkLayerHeight(GcodeParameters params, PrinterProfile profile, List<String> errors) {
        BigDecimal lh = params.layerHeight();
        if (lh == null) return;
        if (lh.compareTo(profile.layerHeightMin()) < 0) {
            errors.add(String.format(
                    "Layer height %.3f mm is below the minimum (%.3f mm) for %s.",
                    lh, profile.layerHeightMin(), profile.displayName()));
        }
        if (lh.compareTo(profile.layerHeightMax()) > 0) {
            errors.add(String.format(
                    "Layer height %.3f mm exceeds the maximum (%.3f mm) for %s.",
                    lh, profile.layerHeightMax(), profile.displayName()));
        }
    }

    private void checkMaterial(GcodeParameters params, PrinterProfile profile, List<String> errors) {
        String material = params.material();
        if (material == null) return;
        if (!profile.allowedMaterials().contains(material.toUpperCase())) {
            errors.add(String.format(
                    "Material '%s' is not approved for %s. "
                    + "Approved materials: %s.",
                    material, profile.displayName(), String.join(", ", profile.allowedMaterials())));
        }
    }

    private static String formatSet(Set<BigDecimal> values) {
        return values.stream()
                .sorted()
                .map(v -> v.stripTrailingZeros().toPlainString() + " mm")
                .reduce((a, b) -> a + ", " + b)
                .orElse("(none)");
    }
}

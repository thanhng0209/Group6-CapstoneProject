package com.uwa.printerfarm.gcode;

import com.uwa.printerfarm.printer.CompatibilityValidationService;
import com.uwa.printerfarm.printer.ValidationResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * REST endpoint for G-code file uploads.
 *
 * <h3>POST /api/gcode/upload</h3>
 * <pre>
 * Content-Type: multipart/form-data
 * Parameters:
 *   file      – the .gcode file (max 50 MB, configured in application.yml)
 *   printerId – canonical printer ID, e.g. "PRUSA_XL" or "PRUSA_CORE_ONE"
 *
 * Responses:
 *   200 OK               – {@link GcodeUploadResponse} (may include incompatibility details)
 *   400 Bad Request      – invalid file type, missing params, or binary G-code
 *   413 Payload Too Large – file exceeds 50 MB limit (handled by GlobalExceptionHandler)
 * </pre>
 *
 * <h3>GET /api/gcode/printers</h3>
 * Returns the list of registered printer IDs and display names so the
 * frontend can build the printer-selector drop-down dynamically.
 */
@RestController
@RequestMapping("/api/gcode")
@CrossOrigin(origins = "*") // allow the standalone HTML frontend during development
public class GcodeUploadController {

    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024; // 50 MB

    private final GcodeParserService parserService;
    private final CompatibilityValidationService validationService;
    private final com.uwa.printerfarm.printer.PrinterProfileRegistry printerRegistry;

    public GcodeUploadController(GcodeParserService parserService,
                                  CompatibilityValidationService validationService,
                                  com.uwa.printerfarm.printer.PrinterProfileRegistry printerRegistry) {
        this.parserService     = parserService;
        this.validationService = validationService;
        this.printerRegistry   = printerRegistry;
    }

    // ────────────────────────────────────────────────────────────────────────
    // POST /api/gcode/upload
    // ────────────────────────────────────────────────────────────────────────

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @RequestParam("file")      MultipartFile file,
            @RequestParam("printerId") String printerId) {

        // ── 1. File presence check ─────────────────────────────────────────
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(errorBody("No file was provided. Please select a .gcode file to upload.",
                            "MISSING_FILE"));
        }

        // ── 2. Extension check ─────────────────────────────────────────────
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".gcode")) {
            return ResponseEntity.badRequest()
                    .body(errorBody(
                            "Invalid file type: only .gcode files are accepted. "
                            + "Received: " + (originalName != null ? originalName : "(unknown)"),
                            "INVALID_FILE_TYPE"));
        }

        // ── 3. File size check (belt-and-suspenders; Spring also limits it) ─
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            return ResponseEntity.status(413)
                    .body(errorBody(
                            "File too large: maximum upload size is 50 MB. "
                            + "Received: " + (file.getSize() / (1024 * 1024)) + " MB.",
                            "FILE_TOO_LARGE"));
        }

        // ── 4. Printer ID check ────────────────────────────────────────────
        if (printerId == null || printerId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(errorBody("Please select a printer before uploading.", "MISSING_PRINTER_ID"));
        }

        // ── 5. Parse G-code (throws GcodeParseException on binary / IO error)
        GcodeParameters params = parserService.parse(file);

        // ── 6. Compatibility validation ───────────────────────────────────
        ValidationResult validation = validationService.validate(params, printerId.toUpperCase());

        // Return 200 always; let the frontend inspect `compatible` field
        return ResponseEntity.ok(GcodeUploadResponse.from(params, validation));
    }

    // ────────────────────────────────────────────────────────────────────────
    // GET /api/gcode/printers  – printer selector support
    // ────────────────────────────────────────────────────────────────────────

    @GetMapping("/printers")
    public ResponseEntity<?> listPrinters() {
        List<Map<String, String>> list = printerRegistry.findAll().stream()
                .map(p -> Map.of("id", p.id(), "displayName", p.displayName()))
                .toList();
        return ResponseEntity.ok(list);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────

    private static Map<String, Object> errorBody(String message, String code) {
        return Map.of(
                "errors", List.of(message),
                "code", code
        );
    }
}

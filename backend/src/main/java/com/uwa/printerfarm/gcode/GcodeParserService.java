package com.uwa.printerfarm.gcode;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the comment header of a PrusaSlicer-generated G-code file and
 * returns a {@link GcodeParameters} record containing the extracted values.
 *
 * <h3>How PrusaSlicer embeds metadata</h3>
 * PrusaSlicer writes machine-readable key/value pairs at the <em>top</em> of
 * the file as G-code comments, for example:
 * <pre>
 * ; printer_model = XL
 * ; nozzle_diameter = 0.4
 * ; layer_height = 0.2
 * ; filament_type = PLA
 * ; temperature = 215
 * ; bed_temperature = 60
 * ; total weight = 12.34
 * ; estimated printing time (normal mode) = 1h 23m 45s
 * </pre>
 *
 * <h3>Binary G-code detection</h3>
 * PrusaSlicer ≥ 2.7 can optionally export a binary-encoded G-code file whose
 * first four bytes are {@code 0x9D 0x8B 0x0A 0x0B}. Such files cannot be
 * parsed by this service and are rejected immediately with a user-facing
 * explanation instructing the operator to re-export with binary mode disabled.
 *
 * <h3>Performance</h3>
 * Only the first {@value #MAX_HEADER_LINES} lines are scanned; PrusaSlicer
 * always writes its metadata at the top of the file, so we never need to read
 * the full (potentially many-MB) toolpath section.
 */
@Service
public class GcodeParserService {

    /** Maximum number of lines to scan for metadata comments. */
    private static final int MAX_HEADER_LINES = 500;

    /**
     * Magic bytes that identify a PrusaSlicer binary G-code file.
     * Source: PrusaSlicer source – {@code src/libslic3r/GCode/GCodeWriter.cpp}
     */
    private static final byte[] BINARY_MAGIC = {(byte) 0x9D, (byte) 0x8B, 0x0A, 0x0B};

    // -----------------------------------------------------------------------
    // Comment-line patterns (PrusaSlicer 2.x / 3.x format)
    // -----------------------------------------------------------------------
    private static final Pattern PRINTER_MODEL   = pattern("printer_model");
    private static final Pattern NOZZLE_DIAMETER = pattern("nozzle_diameter");
    private static final Pattern LAYER_HEIGHT    = pattern("layer_height");
    private static final Pattern FILAMENT_TYPE   = pattern("filament_type");
    private static final Pattern TEMPERATURE     = pattern("temperature");
    private static final Pattern BED_TEMPERATURE = pattern("bed_temperature");
    private static final Pattern TOTAL_WEIGHT    = pattern("total weight");
    private static final Pattern PRINT_TIME      = Pattern.compile(
            "^;\\s*estimated printing time.*?=\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    // Bounding-box comment emitted by PrusaSlicer:
    // ; print_dimensions_x = 125.43
    private static final Pattern DIM_X = pattern("print_dimensions_x");
    private static final Pattern DIM_Y = pattern("print_dimensions_y");
    private static final Pattern DIM_Z = pattern("print_dimensions_z");

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Parses a {@link MultipartFile} and returns the extracted G-code
     * parameters.
     *
     * @param file the uploaded file; must not be {@code null}
     * @return a fully-populated (fields may be {@code null}) {@link GcodeParameters}
     * @throws GcodeParseException if the file is binary-encoded or unreadable
     */
    public GcodeParameters parse(MultipartFile file) {
        try (InputStream raw = file.getInputStream()) {
            // -- 1. Binary detection ------------------------------------------
            byte[] header = raw.readNBytes(4);
            if (isBinaryGcode(header)) {
                throw new GcodeParseException(
                        "The uploaded file uses PrusaSlicer's binary G-code format, which cannot be "
                        + "parsed. Please re-export from PrusaSlicer with binary G-code disabled: "
                        + "Output Options → uncheck 'Binary G-code'.");
            }

            // -- 2. Text parsing (re-assemble first 4 bytes into stream) ------
            String fourByteStr = new String(header, StandardCharsets.ISO_8859_1);
            InputStream combined = concatenate(fourByteStr.getBytes(StandardCharsets.ISO_8859_1), raw);

            return parseTextStream(combined, false);

        } catch (IOException e) {
            throw new GcodeParseException("Could not read the uploaded file: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private GcodeParameters parseTextStream(InputStream stream, boolean alreadyDetectedBinary)
            throws IOException {

        String printerProfileId = null;
        BigDecimal nozzleDiameter = null;
        BigDecimal layerHeight = null;
        String material = null;
        Integer nozzleTemp = null;
        Integer bedTemp = null;
        BigDecimal estimatedGrams = null;
        BigDecimal estimatedMinutes = null;
        BigDecimal dimX = null;
        BigDecimal dimY = null;
        BigDecimal dimZ = null;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {

            String line;
            int lineCount = 0;

            while ((line = reader.readLine()) != null && lineCount < MAX_HEADER_LINES) {
                lineCount++;
                line = line.trim();
                if (line.isEmpty() || !line.startsWith(";")) {
                    // Non-comment lines after comments begin = end of header
                    if (lineCount > 10 && !line.startsWith(";")) {
                        continue; // skip toolpath commands, keep scanning for trailing comments
                    }
                    continue;
                }

                printerProfileId  = firstNonNull(printerProfileId,  extractString(PRINTER_MODEL, line));
                nozzleDiameter    = firstNonNull(nozzleDiameter,    extractDecimal(NOZZLE_DIAMETER, line));
                layerHeight       = firstNonNull(layerHeight,        extractDecimal(LAYER_HEIGHT, line));
                material          = firstNonNull(material,           extractString(FILAMENT_TYPE, line));
                nozzleTemp        = firstNonNull(nozzleTemp,         extractInt(TEMPERATURE, line));
                bedTemp           = firstNonNull(bedTemp,            extractInt(BED_TEMPERATURE, line));
                estimatedGrams    = firstNonNull(estimatedGrams,     extractDecimal(TOTAL_WEIGHT, line));
                dimX              = firstNonNull(dimX,               extractDecimal(DIM_X, line));
                dimY              = firstNonNull(dimY,               extractDecimal(DIM_Y, line));
                dimZ              = firstNonNull(dimZ,               extractDecimal(DIM_Z, line));

                if (estimatedMinutes == null) {
                    estimatedMinutes = extractPrintTime(line);
                }
            }
        }

        return new GcodeParameters(
                normalizeProfileId(printerProfileId),
                dimX, dimY, dimZ,
                nozzleDiameter,
                layerHeight,
                material == null ? null : material.toUpperCase(),
                nozzleTemp,
                bedTemp,
                estimatedGrams,
                estimatedMinutes,
                alreadyDetectedBinary
        );
    }

    /**
     * Normalises raw printer_model values to canonical IDs used by
     * {@link com.uwa.printerfarm.printer.PrinterProfileRegistry}.
     * e.g. "XL" → "PRUSA_XL", "Core One" → "PRUSA_CORE_ONE".
     */
    private String normalizeProfileId(String raw) {
        if (raw == null) return null;
        String upper = raw.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        if (upper.startsWith("PRUSA_")) return upper;
        return "PRUSA_" + upper;
    }

    private boolean isBinaryGcode(byte[] header) {
        if (header.length < BINARY_MAGIC.length) return false;
        for (int i = 0; i < BINARY_MAGIC.length; i++) {
            if (header[i] != BINARY_MAGIC[i]) return false;
        }
        return true;
    }

    /** Parses "1h 23m 45s" → total minutes as BigDecimal. */
    private BigDecimal extractPrintTime(String line) {
        Matcher m = PRINT_TIME.matcher(line);
        if (!m.matches()) return null;
        String timeStr = m.group(1).trim();

        long totalSeconds = 0;
        Matcher h = Pattern.compile("(\\d+)h").matcher(timeStr);
        Matcher min = Pattern.compile("(\\d+)m").matcher(timeStr);
        Matcher s = Pattern.compile("(\\d+)s").matcher(timeStr);
        if (h.find())   totalSeconds += Long.parseLong(h.group(1)) * 3600;
        if (min.find()) totalSeconds += Long.parseLong(min.group(1)) * 60;
        if (s.find())   totalSeconds += Long.parseLong(s.group(1));
        if (totalSeconds == 0) return null;
        return BigDecimal.valueOf(totalSeconds).divide(BigDecimal.valueOf(60), 2,
                java.math.RoundingMode.HALF_UP);
    }

    private static String extractString(Pattern p, String line) {
        Matcher m = p.matcher(line);
        return m.matches() ? m.group(1).trim() : null;
    }

    private static BigDecimal extractDecimal(Pattern p, String line) {
        String val = extractString(p, line);
        if (val == null) return null;
        try { return new BigDecimal(val); } catch (NumberFormatException e) { return null; }
    }

    private static Integer extractInt(Pattern p, String line) {
        String val = extractString(p, line);
        if (val == null) return null;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return null; }
    }

    private static Pattern pattern(String key) {
        return Pattern.compile("^;\\s*" + Pattern.quote(key) + "\\s*=\\s*(.+)$",
                Pattern.CASE_INSENSITIVE);
    }

    private static <T> T firstNonNull(T existing, T candidate) {
        return existing != null ? existing : candidate;
    }

    /** Concatenates two byte arrays into a single InputStream. */
    private static InputStream concatenate(byte[] first, InputStream rest) {
        return new java.io.SequenceInputStream(
                new java.io.ByteArrayInputStream(first), rest);
    }
}

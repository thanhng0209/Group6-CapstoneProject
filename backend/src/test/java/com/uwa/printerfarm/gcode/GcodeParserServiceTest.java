package com.uwa.printerfarm.gcode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GcodeParserService}.
 *
 * Test files are loaded from {@code src/test/resources/gcode/}.
 */
class GcodeParserServiceTest {

    private GcodeParserService parser;

    @BeforeEach
    void setUp() {
        parser = new GcodeParserService();
    }

    // ── Happy path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Parses all metadata fields from a valid PrusaSlicer XL G-code file")
    void parseValidXlGcode() throws Exception {
        MockMultipartFile file = loadGcode("valid_xl.gcode");

        GcodeParameters params = parser.parse(file);

        assertThat(params.printerProfileId()).isEqualTo("PRUSA_XL");
        assertThat(params.nozzleDiameter()).isEqualByComparingTo(new BigDecimal("0.4"));
        assertThat(params.layerHeight()).isEqualByComparingTo(new BigDecimal("0.20"));
        assertThat(params.material()).isEqualToIgnoringCase("PLA");
        assertThat(params.nozzleTemp()).isEqualTo(215);
        assertThat(params.bedTemp()).isEqualTo(60);
        assertThat(params.estimatedGrams()).isEqualByComparingTo(new BigDecimal("18.62"));
        assertThat(params.printVolumeX()).isEqualByComparingTo(new BigDecimal("125.30"));
        assertThat(params.printVolumeY()).isEqualByComparingTo(new BigDecimal("98.45"));
        assertThat(params.printVolumeZ()).isEqualByComparingTo(new BigDecimal("42.00"));
        assertThat(params.isBinaryGcode()).isFalse();

        // 1h 23m 45s = 83.75 minutes
        assertThat(params.estimatedMinutes()).isEqualByComparingTo(new BigDecimal("83.75"));
    }

    @Test
    @DisplayName("Parses XL profile file intended for Core One rejection test")
    void parseInvalidCoreOneFile() throws Exception {
        MockMultipartFile file = loadGcode("invalid_core_one_with_xl_profile.gcode");

        GcodeParameters params = parser.parse(file);

        assertThat(params.printerProfileId()).isEqualTo("PRUSA_XL");
        assertThat(params.printVolumeX()).isEqualByComparingTo(new BigDecimal("310.00"));
        assertThat(params.printVolumeY()).isEqualByComparingTo(new BigDecimal("280.00"));
        assertThat(params.material()).isEqualToIgnoringCase("PLA");
        assertThat(params.isBinaryGcode()).isFalse();
    }

    // ── Binary G-code detection ─────────────────────────────────────────────

    @Test
    @DisplayName("Throws GcodeParseException for binary G-code magic bytes")
    void rejectsBinaryGcode() {
        // Craft a fake binary G-code file with the magic header 0x9D 0x8B 0x0A 0x0B
        byte[] binaryContent = new byte[]{(byte)0x9D, (byte)0x8B, 0x0A, 0x0B, 0x00, 0x01};
        MockMultipartFile binaryFile = new MockMultipartFile(
                "file", "binary.gcode", "application/octet-stream", binaryContent);

        assertThatThrownBy(() -> parser.parse(binaryFile))
                .isInstanceOf(GcodeParseException.class)
                .hasMessageContaining("binary G-code")
                .hasMessageContaining("Output Options");
    }

    // ── Graceful degradation ────────────────────────────────────────────────

    @Test
    @DisplayName("Returns null for optional fields when they are absent from the file")
    void returnsNullForMissingOptionalFields() {
        String minimalGcode = "; printer_model = MK4S\n; layer_height = 0.2\nG28\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "minimal.gcode", "text/plain", minimalGcode.getBytes());

        GcodeParameters params = parser.parse(file);

        assertThat(params.printerProfileId()).isEqualTo("PRUSA_MK4S");
        assertThat(params.layerHeight()).isEqualByComparingTo(new BigDecimal("0.2"));
        // Optional fields not present → null, not exception
        assertThat(params.nozzleDiameter()).isNull();
        assertThat(params.material()).isNull();
        assertThat(params.estimatedGrams()).isNull();
        assertThat(params.estimatedMinutes()).isNull();
    }

    @Test
    @DisplayName("Returns null printerProfileId when printer_model comment is absent")
    void returnsNullProfileIdWhenAbsent() {
        String gcodeNoModel = "; layer_height = 0.15\n; nozzle_diameter = 0.4\nG28\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "no_model.gcode", "text/plain", gcodeNoModel.getBytes());

        GcodeParameters params = parser.parse(file);

        assertThat(params.printerProfileId()).isNull();
    }

    // ── Print time parsing ──────────────────────────────────────────────────

    @Test
    @DisplayName("Correctly converts h/m/s print time string to total minutes")
    void parsesPrintTimeCorrectly() {
        String gcode = "; printer_model = XL\n"
                     + "; estimated printing time (normal mode) = 2h 30m 0s\n"
                     + "G28\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "time.gcode", "text/plain", gcode.getBytes());

        GcodeParameters params = parser.parse(file);

        // 2h 30m = 150 minutes
        assertThat(params.estimatedMinutes()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private MockMultipartFile loadGcode(String filename) throws Exception {
        URL resource = getClass().getClassLoader().getResource("gcode/" + filename);
        assertThat(resource).as("test resource gcode/" + filename).isNotNull();
        byte[] bytes = Files.readAllBytes(Path.of(resource.toURI()));
        return new MockMultipartFile("file", filename, "text/plain", bytes);
    }
}

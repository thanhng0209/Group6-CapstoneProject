package com.uwa.printerfarm.config;

import com.uwa.printerfarm.gcode.GcodeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.Map;

/**
 * Central exception handler for all REST controllers.
 *
 * <p>Translates domain exceptions into RFC 7807-style JSON error responses
 * so the frontend always receives a consistent {@code {errors: [...], code: "..."}}
 * envelope regardless of which layer threw the exception.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles files that exceed the Spring multipart size limit
     * (configured via {@code spring.servlet.multipart.max-file-size}).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(error(List.of(
                        "The uploaded file exceeds the maximum allowed size of 50 MB. "
                        + "Please check that you are not uploading a full project archive, "
                        + "and that the .gcode file is not excessively large."),
                        "FILE_TOO_LARGE"));
    }

    /**
     * Handles binary G-code or fundamentally unparseable files detected by
     * {@link com.uwa.printerfarm.gcode.GcodeParserService}.
     */
    @ExceptionHandler(GcodeParseException.class)
    public ResponseEntity<Map<String, Object>> handleGcodeParse(GcodeParseException ex) {
        return ResponseEntity.badRequest()
                .body(error(List.of(ex.getMessage()), "GCODE_PARSE_ERROR"));
    }

    /**
     * Catch-all for unexpected runtime exceptions – returns a generic 500
     * without leaking internal details to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        // Log internally (in a real deployment, use a structured logger)
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(List.of("An unexpected error occurred. Please try again or contact support."),
                        "INTERNAL_ERROR"));
    }

    // -----------------------------------------------------------------------

    private static Map<String, Object> error(List<String> errors, String code) {
        return Map.of("errors", errors, "code", code);
    }
}

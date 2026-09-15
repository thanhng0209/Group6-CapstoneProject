package com.uwa.printerfarm.gcode;

/**
 * Thrown when a G-code file cannot be parsed – either because it uses binary
 * encoding or because its content is structurally invalid (e.g. not a G-code
 * file at all).
 *
 * <p>The message is intended to be forwarded directly to the end user, so it
 * should be written in plain English without technical stack-trace details.</p>
 */
public class GcodeParseException extends RuntimeException {

    public GcodeParseException(String message) {
        super(message);
    }

    public GcodeParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

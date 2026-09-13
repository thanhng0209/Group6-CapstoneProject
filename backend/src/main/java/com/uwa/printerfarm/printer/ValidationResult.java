package com.uwa.printerfarm.printer;

import java.util.Collections;
import java.util.List;

/**
 * Result of a G-code compatibility check against a specific printer profile.
 *
 * @param valid  {@code true} when all parameters are within the printer's
 *               approved bounds; {@code false} if one or more constraints
 *               are violated.
 * @param errors Human-readable descriptions of each constraint violation.
 *               Empty when {@code valid} is {@code true}.
 */
public record ValidationResult(boolean valid, List<String> errors) {

    /** Convenience factory for a passing result. */
    public static ValidationResult pass() {
        return new ValidationResult(true, Collections.emptyList());
    }

    /** Convenience factory for a failing result with one or more errors. */
    public static ValidationResult fail(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("A failing ValidationResult must have at least one error");
        }
        return new ValidationResult(false, Collections.unmodifiableList(errors));
    }
}

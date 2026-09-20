package com.uwa.printerfarm.security;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleans up whatever the user typed on the login page before it goes to Spring Security.
 *
 *   24717854@student.uwa.edu.au   ->  24717854                    (student email -> the 8-digit uni_id)
 *   24717854                      ->  24717854                    (old style still works)
 *   00000000                      ->  00000000                    (seeded admin, unchanged)
 *   Lab.Coordinator@uwa.edu.au    ->  lab.coordinator@uwa.edu.au  (staff email, lower-cased)
 *
 * Staff emails are NOT turned into a uni_id (users.uni_id is VARCHAR(20)).
 * CustomUserDetailsService looks them up in the users.email column instead.
 *
 * Anything else is returned trimmed and unchanged, so it simply fails authentication.
 */
public final class LoginIdentifier {

    private static final Pattern STUDENT_EMAIL =
            Pattern.compile("^(\\d{8})@student\\.uwa\\.edu\\.au$", Pattern.CASE_INSENSITIVE);

    private static final Pattern UWA_EMAIL =
            Pattern.compile("^[a-z0-9._%+'-]+@([a-z0-9-]+\\.)*uwa\\.edu\\.au$", Pattern.CASE_INSENSITIVE);

    private LoginIdentifier() {
    }

    public static String toUniId(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();

        // Student email: must be checked first, because it also matches the general UWA pattern.
        Matcher student = STUDENT_EMAIL.matcher(value);
        if (student.matches()) {
            return student.group(1);
        }

        if (UWA_EMAIL.matcher(value).matches()) {
            return value.toLowerCase(Locale.ROOT);
        }

        return value;
    }
}

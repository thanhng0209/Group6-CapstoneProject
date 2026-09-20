package com.uwa.printerfarm.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LoginIdentifierTest {

    @Test
    void studentEmailBecomesEightDigitId() {
        assertEquals("24717854", LoginIdentifier.toUniId("24717854@student.uwa.edu.au"));
        assertEquals("24717854", LoginIdentifier.toUniId("  24717854@STUDENT.UWA.EDU.AU  "));
    }

    @Test
    void bareStudentNumberIsUnchanged() {
        assertEquals("22345678", LoginIdentifier.toUniId("22345678"));
        assertEquals("00000000", LoginIdentifier.toUniId("00000000"));
    }

    @Test
    void staffEmailIsLowerCasedAndKept() {
        assertEquals("lab.coordinator@uwa.edu.au", LoginIdentifier.toUniId("lab.coordinator@uwa.edu.au"));
        assertEquals("first.last@uwa.edu.au", LoginIdentifier.toUniId("First.Last@UWA.edu.au"));
    }

    @Test
    void otherDomainsAreLeftAloneSoLoginFails() {
        assertEquals("john@gmail.com", LoginIdentifier.toUniId("john@gmail.com"));
        assertEquals("x@uwa.edu.au.evil.com", LoginIdentifier.toUniId("x@uwa.edu.au.evil.com"));
        assertEquals("abc@evil-uwa.edu.au", LoginIdentifier.toUniId("abc@evil-uwa.edu.au"));
    }

    @Test
    void nullStaysNull() {
        assertNull(LoginIdentifier.toUniId(null));
    }
}

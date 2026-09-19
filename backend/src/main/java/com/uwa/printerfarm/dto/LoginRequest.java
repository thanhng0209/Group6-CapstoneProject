package com.uwa.printerfarm.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.uwa.printerfarm.util.LoginIdentifier;
import jakarta.validation.constraints.NotBlank;
import lombok.Setter;

@Setter
public class LoginRequest {

    /**
     * What the user typed: a UWA email (student or staff) or an old-style 8-digit UNI ID.
     * The JSON key can be "email" or "uniId", so the current Angular service keeps working
     * whichever name it sends.
     */
    @NotBlank(message = "UWA email is required")
    @JsonAlias({"email", "uniId"})
    private String uniId;

    @NotBlank(message = "Password is required")
    private String password;

    /**
     * Returns the cleaned-up login value, not the raw text:
     *   24717854@student.uwa.edu.au -> 24717854
     *   Lab.Coordinator@uwa.edu.au  -> lab.coordinator@uwa.edu.au
     * The name getUniId() is kept so UserService and existing tests need no change.
     * CustomUserDetailsService looks the value up by uni_id first, then by email.
     */
    public String getUniId() {
        return LoginIdentifier.toUniId(uniId);
    }

    public String getPassword() {
        return password;
    }
}
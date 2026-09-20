package com.uwa.printerfarm.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.uwa.printerfarm.security.LoginIdentifier;
import jakarta.validation.constraints.NotBlank;
import lombok.Setter;

@Setter
public class LoginRequest {

    /**
     * What the user typed: a UWA email (student or staff) or an old-style 8-digit UNI ID.
     * The JSON key can be "email" or "uniId".
     */
    @NotBlank(message = "UWA email is required")
    @JsonAlias({"email", "uniId"})
    private String uniId;

    @NotBlank(message = "Password is required")
    private String password;

    /** Returns the internal uniId, so existing callers of getUniId() keep working. */
    public String getUniId() {
        return LoginIdentifier.toUniId(uniId);
    }

    public String getPassword() {
        return password;
    }
}

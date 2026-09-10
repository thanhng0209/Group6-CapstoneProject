package com.uwa.printerfarm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "UNI ID is required")
    private String uniId;

    @NotBlank(message = "Password is required")
    private String password;
}

package com.uwa.printerfarm.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String uniId;
    private String fullName;
    private String role;
}

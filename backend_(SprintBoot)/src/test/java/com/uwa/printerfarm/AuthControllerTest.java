package com.uwa.printerfarm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uwa.printerfarm.dto.LoginRequest;
import com.uwa.printerfarm.enums.Role;
import com.uwa.printerfarm.model.User;
import com.uwa.printerfarm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers the "Write unit tests for login/auth flows" and
 * "Acceptance check: a logged-in user can see their real balance and job
 * history on first load" items from the Authentication & User Dashboard workstream.
 *
 * Uses an in-memory H2 database (see src/test/resources/application-test.yml)
 * so tests don't depend on a running PostgreSQL instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_UNI_ID = "12345678";
    private static final String TEST_PASSWORD = "TestPass123!";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User user = User.builder()
                .uniId(TEST_UNI_ID)
                .email("test.student@student.uwa.edu.au")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .fullName("Test Student")
                .role(Role.STUDENT)
                .balanceCents(1500L) // $15.00 — used to assert real balance shows up below
                .build();

        userRepository.save(user);
    }

    @Test
    void login_withValidCredentials_returnsJwtToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUniId(TEST_UNI_ID);
        request.setPassword(TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.uniId").value(TEST_UNI_ID))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void login_withWrongPassword_returnsUnauthorized() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUniId(TEST_UNI_ID);
        request.setPassword("WrongPassword!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboard_withoutToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/user/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboard_withValidToken_returnsRealBalanceAndProfile() throws Exception {
        // Log in first to get a real token
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUniId(TEST_UNI_ID);
        loginRequest.setPassword(TEST_PASSWORD);

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginResponse).get("token").asText();

        // This is the acceptance check: "a logged-in user can see their real
        // balance and job history on first load" — balanceCents=1500 -> $15.00
        mockMvc.perform(get("/api/user/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uniId").value(TEST_UNI_ID))
                .andExpect(jsonPath("$.fullName").value("Test Student"))
                .andExpect(jsonPath("$.balance").value(15.0))
                .andExpect(jsonPath("$.currentJobs").isArray())
                .andExpect(jsonPath("$.printHistory").isArray());
    }
}

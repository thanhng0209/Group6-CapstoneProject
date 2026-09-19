package com.uwa.printerfarm.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uwa.printerfarm.security.JwtUtil;
import com.uwa.printerfarm.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

@WebMvcTest(JobController.class)
@WithMockUser(username = "22345678", roles = "STUDENT")
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobSubmissionService jobSubmissionService;

    @MockBean
    private JobLifecycleService jobLifecycleService;

    @MockBean
    private JobRepository jobRepository;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    private Job sampleJob;

    @BeforeEach
    void setUp() {
        sampleJob = new Job("22345678", "prusa-xl-1", "cube.gcode", "PLA", new BigDecimal("50.00"), new BigDecimal("120.00"));
        sampleJob.assignId(101L);
    }
}

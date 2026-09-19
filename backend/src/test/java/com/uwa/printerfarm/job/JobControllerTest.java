package com.uwa.printerfarm.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uwa.printerfarm.security.JwtUtil;
import com.uwa.printerfarm.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    void submitJobSuccessfully() throws Exception {
        when(jobSubmissionService.submit(any(Job.class))).thenAnswer(invocation -> {
            Job j = invocation.getArgument(0);
            j.assignId(101L);
            j.setCost(new BigDecimal("5.00"));
            return j;
        });

        JobController.JobSubmitRequest request = new JobController.JobSubmitRequest(
                "prusa-xl-1", null, "cube.gcode", "PLA",
                new BigDecimal("50.00"), new BigDecimal("120.00"), null
        );

        mockMvc.perform(post("/api/jobs/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101L))
                .andExpect(jsonPath("$.ownerUniId").value("22345678"))
                .andExpect(jsonPath("$.printerId").value("prusa-xl-1"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.cost").value(5.00));

        verify(jobSubmissionService).submit(any(Job.class));
    }
}

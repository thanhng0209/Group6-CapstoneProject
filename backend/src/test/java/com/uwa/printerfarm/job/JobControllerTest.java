package com.uwa.printerfarm.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uwa.printerfarm.security.JwtUtil;
import com.uwa.printerfarm.service.CustomUserDetailsService;
import com.uwa.printerfarm.wallet.InsufficientBalanceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
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

    @Test
    void submitJobWithInsufficientBalanceReturnsBadRequest() throws Exception {
        when(jobSubmissionService.submit(any(Job.class)))
                .thenThrow(new InsufficientBalanceException("22345678", new BigDecimal("2.00"), new BigDecimal("15.00")));

        JobController.JobSubmitRequest request = new JobController.JobSubmitRequest(
                "prusa-xl-1", null, "cube.gcode", "PLA",
                new BigDecimal("200.00"), new BigDecimal("300.00"), null
        );

        mockMvc.perform(post("/api/jobs/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void submitJobWithMissingPrinterIdReturnsBadRequest() throws Exception {
        JobController.JobSubmitRequest request = new JobController.JobSubmitRequest(
                null, "", "cube.gcode", "PLA",
                new BigDecimal("10.00"), new BigDecimal("20.00"), null
        );

        mockMvc.perform(post("/api/jobs/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PRINTER_ID"));

        verify(jobSubmissionService, never()).submit(any());
    }

    @Test
    void cancelJobWithinWindowAutoRefunds() throws Exception {
        when(jobRepository.findById(101L)).thenReturn(Optional.of(sampleJob));
        when(jobLifecycleService.cancel(eq(sampleJob), any())).thenAnswer(invocation -> {
            sampleJob.setStatus(JobStatus.CANCELLED);
            return RefundDecision.AUTO_REFUNDED;
        });
        when(jobRepository.save(sampleJob)).thenReturn(sampleJob);

        mockMvc.perform(post("/api/jobs/101/cancel").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(101L))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refundDecision").value("AUTO_REFUNDED"));

        verify(jobLifecycleService).cancel(eq(sampleJob), any());
        verify(jobRepository).save(sampleJob);
    }

    @Test
    void cancelJobOutsideWindowRequiresApproval() throws Exception {
        when(jobRepository.findById(101L)).thenReturn(Optional.of(sampleJob));
        when(jobLifecycleService.cancel(eq(sampleJob), any())).thenAnswer(invocation -> {
            sampleJob.setStatus(JobStatus.CANCELLED);
            return RefundDecision.REQUIRES_APPROVAL;
        });
        when(jobRepository.save(sampleJob)).thenReturn(sampleJob);

        mockMvc.perform(post("/api/jobs/101/cancel").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(101L))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refundDecision").value("REQUIRES_APPROVAL"));

        verify(jobLifecycleService).cancel(eq(sampleJob), any());
        verify(jobRepository).save(sampleJob);
    }
}

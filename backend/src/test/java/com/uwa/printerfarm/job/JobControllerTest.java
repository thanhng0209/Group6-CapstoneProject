package com.uwa.printerfarm.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uwa.printerfarm.printer.PrinterRepository;
import com.uwa.printerfarm.printer.MockPrinterDispatcher;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private PrinterRepository printerRepository;

        @MockBean
        private MockPrinterDispatcher mockPrinterDispatcher;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    private Job sampleJob;

    @BeforeEach
    void setUp() {
        sampleJob = new Job("22345678", "prusa-xl-1", "cube.gcode", "PLA", new BigDecimal("50.00"), new BigDecimal("120.00"));
        sampleJob.assignId(101L);
        when(printerRepository.existsById(anyString())).thenReturn(true);
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
    void submitJobWithOnlyPrinterProfileIdReturnsBadRequest() throws Exception {
        JobController.JobSubmitRequest request = new JobController.JobSubmitRequest(
                null, "PRUSA_XL", "cube.gcode", "PLA",
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
    void submitJobWithUnknownPrinterIdReturnsBadRequest() throws Exception {
        when(printerRepository.existsById("unknown-printer")).thenReturn(false);

        JobController.JobSubmitRequest request = new JobController.JobSubmitRequest(
                "unknown-printer", null, "cube.gcode", "PLA",
                new BigDecimal("10.00"), new BigDecimal("20.00"), null
        );

        mockMvc.perform(post("/api/jobs/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PRINTER_ID"));

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

    @Test
    void cancelNonExistentJobReturnsNotFound() throws Exception {
        when(jobRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/jobs/999/cancel").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));

        verify(jobLifecycleService, never()).cancel(any(), any());
    }

    @Test
    void cancelJobBelongingToAnotherStudentReturnsForbidden() throws Exception {
        Job otherUserJob = new Job("99887766", "prusa-xl-1", "other.gcode", "PLA",
                new BigDecimal("10.00"), new BigDecimal("20.00"));
        otherUserJob.assignId(202L);

        when(jobRepository.findById(202L)).thenReturn(Optional.of(otherUserJob));

        mockMvc.perform(post("/api/jobs/202/cancel").with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(jobLifecycleService, never()).cancel(any(), any());
    }

    @Test
    void cancelPrintingJobThrowsInvalidJobStatusTransitionException() throws Exception {
        when(jobRepository.findById(101L)).thenReturn(Optional.of(sampleJob));
        when(jobLifecycleService.cancel(eq(sampleJob), any()))
                .thenThrow(new InvalidJobStatusTransitionException(JobStatus.PRINTING, JobStatus.CANCELLED));

        mockMvc.perform(post("/api/jobs/101/cancel").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void getMyJobsReturnsUserJobsOrdered() throws Exception {
        Job secondJob = new Job("22345678", "prusa-core-one", "vase.gcode", "PETG",
                new BigDecimal("30.00"), new BigDecimal("80.00"));
        secondJob.assignId(102L);

        when(jobRepository.findByOwnerUniIdOrderByQueuedAtDesc("22345678"))
                .thenReturn(List.of(secondJob, sampleJob));

        mockMvc.perform(get("/api/jobs/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(102L))
                .andExpect(jsonPath("$[0].fileName").value("vase.gcode"))
                .andExpect(jsonPath("$[1].id").value(101L))
                .andExpect(jsonPath("$[1].fileName").value("cube.gcode"));

        verify(jobRepository).findByOwnerUniIdOrderByQueuedAtDesc("22345678");
    }
}

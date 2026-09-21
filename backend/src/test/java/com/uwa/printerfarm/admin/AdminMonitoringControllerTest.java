package com.uwa.printerfarm.admin;

import com.uwa.printerfarm.job.Job;
import com.uwa.printerfarm.job.JobStatus;
import com.uwa.printerfarm.job.RefundDecision;
import com.uwa.printerfarm.job.RefundRequest;
import com.uwa.printerfarm.job.RefundRequestNotFoundException;
import com.uwa.printerfarm.job.RefundService;
import com.uwa.printerfarm.job.RefundStatus;
import com.uwa.printerfarm.security.JwtUtil;
import com.uwa.printerfarm.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMonitoringController.class)
@WithMockUser(roles = "ADMIN")
class AdminMonitoringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminMonitoringService monitoringService;

    @MockBean
    private RefundService refundService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void jobsEndpointReturnsAllJobsWhenNoStatusGiven() throws Exception {
        Job job = new Job("22345678", "prusa-xl-1", "PLA", new BigDecimal("100"), new BigDecimal("60"));
        when(monitoringService.jobs(null)).thenReturn(List.of(job));

        mockMvc.perform(get("/api/admin/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownerUniId").value("22345678"));
    }

    @Test
    void jobsEndpointFiltersByStatusQueryParam() throws Exception {
        when(monitoringService.jobs(JobStatus.PRINTING)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/jobs").param("status", "PRINTING"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(monitoringService).jobs(JobStatus.PRINTING);
    }

    @Test
    void costsEndpointReturnsSummaryFromService() throws Exception {
        when(monitoringService.costSummary())
                .thenReturn(new CostSummary(new BigDecimal("10.00"), new BigDecimal("2.00"), new BigDecimal("8.00")));

        mockMvc.perform(get("/api/admin/costs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCharged").value(10.00))
                .andExpect(jsonPath("$.totalRefunded").value(2.00))
                .andExpect(jsonPath("$.netRevenue").value(8.00));
    }

    @Test
    void filamentUsageEndpointReturnsMapFromService() throws Exception {
        when(monitoringService.filamentUsageByMaterial()).thenReturn(Map.of("PLA", new BigDecimal("150")));

        mockMvc.perform(get("/api/admin/filament-usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.PLA").value(150));
    }

    @Test
    void printersEndpointReturnsActivityFromService() throws Exception {
        when(monitoringService.printerActivity()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/printers"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void pendingRefundsEndpointReturnsListFromService() throws Exception {
        when(monitoringService.pendingRefundRequests()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/refunds/pending"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void approveRefundReturnsApprovedRequest() throws Exception {
        RefundRequest request = new RefundRequest(1L, 10L, "22345678", new BigDecimal("6.20"), Instant.now());
        request.approve(Instant.now());
        when(refundService.approve(eq(1L), any(Instant.class))).thenReturn(request);

        mockMvc.perform(post("/api/admin/refunds/1/approve").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.amount").value(6.20));

        verify(refundService).approve(eq(1L), any(Instant.class));
    }

    @Test
    void approveRefundReturnsNotFoundWhenRequestDoesNotExist() throws Exception {
        when(refundService.approve(eq(999L), any(Instant.class)))
                .thenThrow(new RefundRequestNotFoundException(999L));

        mockMvc.perform(post("/api/admin/refunds/999/approve").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REFUND_REQUEST_NOT_FOUND"));
    }

    @Test
    void rejectRefundReturnsRejectedRequest() throws Exception {
        RefundRequest request = new RefundRequest(2L, 11L, "22345678", new BigDecimal("4.50"), Instant.now());
        request.reject(Instant.now());
        when(refundService.reject(eq(2L), any(Instant.class))).thenReturn(request);

        mockMvc.perform(post("/api/admin/refunds/2/reject").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.amount").value(4.50));

        verify(refundService).reject(eq(2L), any(Instant.class));
    }

    @Test
    void rejectRefundReturnsBadRequestWhenAlreadyDecided() throws Exception {
        when(refundService.reject(eq(1L), any(Instant.class)))
                .thenThrow(new IllegalStateException("Refund request 1 has already been decided: APPROVED"));

        mockMvc.perform(post("/api/admin/refunds/1/reject").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REFUND_STATE"));
    }

    @Test
    void usageTrendsEndpointDefaultsToThirtyDays() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UsageTrendPoint point = new UsageTrendPoint(today, new BigDecimal("100"),
                new BigDecimal("6.20"), BigDecimal.ZERO, 1L);
        when(monitoringService.usageTrends(30)).thenReturn(List.of(point));

        mockMvc.perform(get("/api/admin/usage-trends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value(today.toString()))
                .andExpect(jsonPath("$[0].filamentGrams").value(100))
                .andExpect(jsonPath("$[0].charged").value(6.20))
                .andExpect(jsonPath("$[0].jobCount").value(1));

        verify(monitoringService).usageTrends(30);
    }

    @Test
    void usageTrendsEndpointHonoursDaysQueryParam() throws Exception {
        when(monitoringService.usageTrends(7)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/usage-trends").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(monitoringService).usageTrends(7);
    }

    @Test
    void usageTrendsEndpointReturnsBadRequestForNonPositiveDays() throws Exception {
        when(monitoringService.usageTrends(0))
                .thenThrow(new IllegalArgumentException("days must be positive"));

        mockMvc.perform(get("/api/admin/usage-trends").param("days", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USAGE_TRENDS_REQUEST"));
    }
}

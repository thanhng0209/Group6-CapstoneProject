package com.uwa.printerfarm.admin;

import com.uwa.printerfarm.job.Job;
import com.uwa.printerfarm.job.JobStatus;
import com.uwa.printerfarm.security.JwtUtil;
import com.uwa.printerfarm.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}

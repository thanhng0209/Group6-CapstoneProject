package com.uwa.printerfarm.wallet;

import com.uwa.printerfarm.security.JwtUtil;
import com.uwa.printerfarm.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletController.class)
class WalletControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalletService walletService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void unauthenticatedUserCannotAccessWalletBalance() throws Exception {
        mockMvc.perform(get("/api/wallet/22345678/balance"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "22345678", roles = "STUDENT")
    void studentCanReadOwnBalance() throws Exception {
        when(walletService.getBalance("22345678")).thenReturn(new BigDecimal("50.00"));

        mockMvc.perform(get("/api/wallet/22345678/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uniId").value("22345678"))
                .andExpect(jsonPath("$.balance").value(50.00));
    }

    @Test
    @WithMockUser(username = "22345678", roles = "STUDENT")
    void studentCannotReadAnotherStudentBalance() throws Exception {
        mockMvc.perform(get("/api/wallet/99999999/balance"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "22345678", roles = "STUDENT")
    void studentCannotDebitWallet() throws Exception {
        mockMvc.perform(post("/api/wallet/22345678/debit").param("amount", "1.00"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanDebitWallet() throws Exception {
        when(walletService.debit("22345678", new BigDecimal("1.00"))).thenReturn(new BigDecimal("49.00"));

        mockMvc.perform(post("/api/wallet/22345678/debit").param("amount", "1.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uniId").value("22345678"))
                .andExpect(jsonPath("$.amountDebited").value(1.00))
                .andExpect(jsonPath("$.balanceAfter").value(49.00));
    }
}

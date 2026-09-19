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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void unauthenticatedWalletReadIsRejected() throws Exception {
        mockMvc.perform(get("/api/wallet/22345678/balance"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(walletService);
    }

    @Test
    @WithMockUser(username = "22345678", roles = "STUDENT")
    void studentCanReadOwnBalance() throws Exception {
        when(walletService.getBalance("22345678")).thenReturn(new BigDecimal("50.00"));

        mockMvc.perform(get("/api/wallet/22345678/balance"))
                .andExpect(status().isOk());

        verify(walletService).getBalance("22345678");
    }

    @Test
    @WithMockUser(username = "22345678", roles = "STUDENT")
    void studentCannotReadAnotherStudentBalance() throws Exception {
        mockMvc.perform(get("/api/wallet/99887766/balance"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(walletService);
    }

    @Test
    @WithMockUser(username = "22345678", roles = "STUDENT")
    void studentCannotCreditWallet() throws Exception {
        mockMvc.perform(post("/api/wallet/22345678/credit").param("amount", "10.00").with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(walletService);
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void adminCanCreditWallet() throws Exception {
        when(walletService.credit("22345678", new BigDecimal("10.00")))
                .thenReturn(new BigDecimal("60.00"));

        mockMvc.perform(post("/api/wallet/22345678/credit").param("amount", "10.00").with(csrf()))
                .andExpect(status().isOk());

        verify(walletService).credit("22345678", new BigDecimal("10.00"));
    }
}

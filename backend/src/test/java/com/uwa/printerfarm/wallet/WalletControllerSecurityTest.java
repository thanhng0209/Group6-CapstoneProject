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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void getBalanceRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/wallet/22345678/balance"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "22345678", roles = "STUDENT")
    void getBalanceAllowsAuthenticatedUser() throws Exception {
        when(walletService.getBalance("22345678")).thenReturn(new BigDecimal("10.00"));

        mockMvc.perform(get("/api/wallet/22345678/balance"))
                .andExpect(status().isOk());

        verify(walletService).getBalance("22345678");
    }
}

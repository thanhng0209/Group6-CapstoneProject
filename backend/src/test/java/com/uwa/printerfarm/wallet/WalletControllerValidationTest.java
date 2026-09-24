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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

/**
 * Verifies that a wallet debit/credit with a non-positive amount is rejected
 * with a 400 (not the generic 500 it would have fallen through to before
 * {@link InvalidWalletAmountException} had a dedicated handler).
 */
@WebMvcTest(WalletController.class)
class WalletControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalletService walletService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = "22345678", roles = "STUDENT")
    void creditWithZeroAmountReturns400() throws Exception {
        when(walletService.credit("22345678", BigDecimal.ZERO))
                .thenThrow(new InvalidWalletAmountException(BigDecimal.ZERO));

        mockMvc.perform(post("/api/wallet/22345678/credit").param("amount", "0").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WALLET_AMOUNT"));
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void debitWithNegativeAmountReturns400() throws Exception {
        when(walletService.debit("22345678", new BigDecimal("-5.00")))
                .thenThrow(new InvalidWalletAmountException(new BigDecimal("-5.00")));

        mockMvc.perform(post("/api/wallet/22345678/debit").param("amount", "-5.00").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WALLET_AMOUNT"));
    }
}

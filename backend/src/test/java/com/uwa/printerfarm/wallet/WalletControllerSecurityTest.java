package com.uwa.printerfarm.wallet;

import com.uwa.printerfarm.security.JwtUtil;
import com.uwa.printerfarm.service.CustomUserDetailsService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(WalletController.class)
class WalletControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalletService walletService;

    @MockBean
    private TransactionLedgerService transactionLedgerService;

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
    void unauthenticatedTransactionHistoryReadIsRejected() throws Exception {
        mockMvc.perform(get("/api/wallet/22345678/transactions"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(transactionLedgerService);
    }

    @Test
    @WithMockUser(username = "22345678", roles = "STUDENT")
    void studentCanReadOwnPagedTransactionHistory() throws Exception {
        Transaction transaction = new Transaction(
                7L, "22345678", 14L, TransactionType.REFUND,
                new BigDecimal("6.20"), new BigDecimal("56.20"),
                Instant.parse("2026-10-01T10:00:00Z"), "Refund for cancelled job 14"
        );
        when(transactionLedgerService.history(
                org.mockito.ArgumentMatchers.eq("22345678"),
                any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(transaction), PageRequest.of(
                0, 20, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))
        ), 1));

        mockMvc.perform(get("/api/wallet/22345678/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions[0].id").value(7))
                .andExpect(jsonPath("$.transactions[0].jobId").value(14))
                .andExpect(jsonPath("$.transactions[0].type").value("REFUND"))
                .andExpect(jsonPath("$.transactions[0].amount").value(6.20))
                .andExpect(jsonPath("$.transactions[0].balanceAfter").value(56.20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        verify(transactionLedgerService).history(
                org.mockito.ArgumentMatchers.eq("22345678"),
                any(PageRequest.class)
        );
    }

    @Test
    @WithMockUser(username = "22345678", roles = "STUDENT")
    void studentCannotReadAnotherStudentsTransactionHistory() throws Exception {
        mockMvc.perform(get("/api/wallet/99887766/transactions"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(transactionLedgerService);
    }

    @Test
    @WithMockUser(username = "22345678", roles = "STUDENT")
    void studentCanCreditOwnWallet() throws Exception {
        when(walletService.credit("22345678", new BigDecimal("10.00")))
                .thenReturn(new BigDecimal("60.00"));

        mockMvc.perform(post("/api/wallet/22345678/credit").param("amount", "10.00").with(csrf()))
                .andExpect(status().isOk());

        verify(walletService).credit("22345678", new BigDecimal("10.00"));
    }

    @Test
    @WithMockUser(username = "22345678", roles = "STUDENT")
    void studentCannotCreditAnotherStudentWallet() throws Exception {
        mockMvc.perform(post("/api/wallet/99887766/credit").param("amount", "10.00").with(csrf()))
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

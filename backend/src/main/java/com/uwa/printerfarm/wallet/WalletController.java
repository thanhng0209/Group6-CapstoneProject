package com.uwa.printerfarm.wallet;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Endpoints for managing student wallet balances and deductions")
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "Get user wallet balance", description = "Returns the balance in dollars for the specified uniId")
    @GetMapping("/{uniId}/balance")
    public ResponseEntity<Map<String, Object>> getBalance(@PathVariable String uniId, Authentication authentication) {
        if (!isAdmin(authentication) && !uniId.equals(authentication.getName())) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        BigDecimal balance = walletService.getBalance(uniId);
        return ResponseEntity.ok(Map.of(
                "uniId", uniId,
                "balance", balance
        ));
    }

    @Operation(summary = "Deduct balance from user wallet", description = "Deducts the specified dollar amount (via request body or ?amount=...) and persists to PostgreSQL")
    @PostMapping("/{uniId}/debit")
    public ResponseEntity<Map<String, Object>> debit(
            @PathVariable String uniId,
            @RequestParam(value = "amount", required = false) BigDecimal paramAmount,
            @RequestBody(required = false) WalletOperationRequest request,
            Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        BigDecimal amount = paramAmount != null ? paramAmount : (request != null ? request.getAmount() : BigDecimal.ZERO);
        BigDecimal balanceAfter = walletService.debit(uniId, amount);
        return ResponseEntity.ok(Map.of(
                "uniId", uniId,
                "amountDebited", amount,
                "balanceAfter", balanceAfter
        ));
    }

    @Operation(summary = "Credit balance to user wallet", description = "Adds the specified dollar amount (via request body or ?amount=...) and persists to PostgreSQL")
    @PostMapping("/{uniId}/credit")
    public ResponseEntity<Map<String, Object>> credit(
            @PathVariable String uniId,
            @RequestParam(value = "amount", required = false) BigDecimal paramAmount,
            @RequestBody(required = false) WalletOperationRequest request,
            Authentication authentication) {
        if (!isAdmin(authentication) && !uniId.equals(authentication.getName())) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        BigDecimal amount = paramAmount != null ? paramAmount : (request != null ? request.getAmount() : BigDecimal.ZERO);
        BigDecimal balanceAfter = walletService.credit(uniId, amount);
        return ResponseEntity.ok(Map.of(
                "uniId", uniId,
                "amountCredited", amount,
                "balanceAfter", balanceAfter
        ));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalletOperationRequest {
        private BigDecimal amount;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
    }
}

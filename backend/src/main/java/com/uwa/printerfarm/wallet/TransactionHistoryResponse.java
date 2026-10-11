package com.uwa.printerfarm.wallet;

import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TransactionHistoryResponse(
        List<TransactionItem> transactions,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static TransactionHistoryResponse from(Page<Transaction> transactionPage) {
        List<TransactionItem> items = transactionPage.getContent().stream()
                .map(TransactionItem::from)
                .toList();
        return new TransactionHistoryResponse(
                items,
                transactionPage.getNumber(),
                transactionPage.getSize(),
                transactionPage.getTotalElements(),
                transactionPage.getTotalPages()
        );
    }

    public record TransactionItem(
            Long id,
            Long jobId,
            TransactionType type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            Instant occurredAt,
            String description
    ) {
        private static TransactionItem from(Transaction transaction) {
            return new TransactionItem(
                    transaction.getId(),
                    transaction.getJobId(),
                    transaction.getType(),
                    transaction.getAmount(),
                    transaction.getBalanceAfter(),
                    transaction.getOccurredAt(),
                    transaction.getDescription()
            );
        }
    }
}

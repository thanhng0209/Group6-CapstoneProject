package com.uwa.printerfarm.job;

import com.uwa.printerfarm.cost.CostCalculationService;
import com.uwa.printerfarm.wallet.InsufficientBalanceException;
import com.uwa.printerfarm.wallet.TransactionLedgerService;
import com.uwa.printerfarm.wallet.TransactionType;
import com.uwa.printerfarm.wallet.WalletService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobSubmissionServiceTest {

    private final CostCalculationService costCalculationService = new CostCalculationService();
    private final WalletService walletService = new WalletService(new BigDecimal("50.00"));
    private final TransactionLedgerService ledgerService = new TransactionLedgerService();
    private final JobSubmissionService submissionService =
            new JobSubmissionService(costCalculationService, walletService, ledgerService);

    private Job newJob(BigDecimal grams, BigDecimal minutes) {
        return new Job("22345678", "prusa-xl-1", "PLA", grams, minutes);
    }

    @Test
    void submittingAJobChargesCostAndRecordsDebitTransaction() {
        Job job = newJob(new BigDecimal("100"), new BigDecimal("60"));

        submissionService.submit(job);

        assertThat(job.getCost()).isEqualByComparingTo("6.20");
        assertThat(walletService.getBalance("22345678")).isEqualByComparingTo("43.80");

        assertThat(ledgerService.history("22345678")).hasSize(1);
        var transaction = ledgerService.history("22345678").get(0);
        assertThat(transaction.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(transaction.getAmount()).isEqualByComparingTo("6.20");
        assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("43.80");
    }

    @Test
    void insufficientBalanceLeavesJobUnpricedAndBalanceUntouched() {
        Job job = newJob(new BigDecimal("100000"), new BigDecimal("60"));

        assertThatThrownBy(() -> submissionService.submit(job))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(job.getCost()).isNull();
        assertThat(walletService.getBalance("22345678")).isEqualByComparingTo("50.00");
        assertThat(ledgerService.history("22345678")).isEmpty();
    }
}

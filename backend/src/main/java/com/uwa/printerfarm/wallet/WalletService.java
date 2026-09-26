package com.uwa.printerfarm.wallet;

import com.uwa.printerfarm.enums.Role;
import com.uwa.printerfarm.model.User;
import com.uwa.printerfarm.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Manages student wallet balances backed by real database persistence on the User entity.
 * Balance is stored on User.balanceCents in PostgreSQL as an integer (cents)
 * to avoid floating-point rounding errors.
 */
@Service
public class WalletService {

    private final UserRepository userRepository;
    private final TransactionLedgerService transactionLedgerService;
    private final BigDecimal defaultStartingBalance;

    @org.springframework.beans.factory.annotation.Autowired
    public WalletService(UserRepository userRepository,
                         TransactionLedgerService transactionLedgerService,
                         @Value("${printerfarm.wallet.default-starting-balance:50.00}") BigDecimal defaultStartingBalance) {
        this.userRepository = userRepository;
        this.transactionLedgerService = transactionLedgerService;
        this.defaultStartingBalance = defaultStartingBalance;
    }

    public WalletService(UserRepository userRepository, BigDecimal defaultStartingBalance) {
        this(userRepository, null, defaultStartingBalance);
    }

    @Transactional
    public BigDecimal getBalance(String ownerUniId) {
        User user = getOrCreateUser(ownerUniId);
        long currentCents = user.getBalanceCents() != null ? user.getBalanceCents() : 0L;
        return toDollars(currentCents);
    }

    @Transactional
    public BigDecimal debit(String ownerUniId, BigDecimal amount) {
        User user = getOrCreateUser(ownerUniId);
        long amountCents = toCents(amount);
        long currentCents = user.getBalanceCents() != null ? user.getBalanceCents() : 0L;
        BigDecimal currentDollars = toDollars(currentCents);

        if (currentCents < amountCents) {
            throw new InsufficientBalanceException(ownerUniId, currentDollars, amount);
        }

        long updatedCents = currentCents - amountCents;
        user.setBalanceCents(updatedCents);
        userRepository.save(user);

        return toDollars(updatedCents);
    }

    /**
     * Credits the user's wallet balance and records a TOPUP transaction in the ledger.
     * This is the standard top-up path used by the wallet controller and external balance operations.
     *
     * @param ownerUniId the student or staff uniId
     * @param amount the dollar amount to credit
     * @return the new balance in dollars
     */
    @Transactional
    public BigDecimal credit(String ownerUniId, BigDecimal amount) {
        return credit(ownerUniId, amount, TransactionType.TOPUP, null, "Wallet balance top-up");
    }

    /**
     * Credits the user's wallet balance without recording a transaction in the ledger.
     * Used by internal services such as {@link com.uwa.printerfarm.job.RefundService}
     * that record their own specialized transaction (e.g. REFUND) to prevent duplicate
     * ledger entries.
     *
     * @param ownerUniId the student or staff uniId
     * @param amount the dollar amount to credit
     * @return the new balance in dollars
     */
    @Transactional
    public BigDecimal creditWithoutLedger(String ownerUniId, BigDecimal amount) {
        return applyCredit(ownerUniId, amount, false, null, null, null);
    }

    /**
     * Credits the user's wallet balance and records a transaction in the ledger with the supplied details.
     *
     * @param ownerUniId the student or staff uniId
     * @param amount the dollar amount to credit
     * @param type the transaction type to record (e.g. TOPUP or REFUND)
     * @param jobId the related job ID, or null if not associated with a job
     * @param description human-readable description for the ledger
     * @return the new balance in dollars
     */
    @Transactional
    public BigDecimal credit(String ownerUniId, BigDecimal amount, TransactionType type, Long jobId, String description) {
        return applyCredit(ownerUniId, amount, true, type, jobId, description);
    }

    private BigDecimal applyCredit(String ownerUniId, BigDecimal amount, boolean recordLedger,
                                    TransactionType type, Long jobId, String description) {
        User user = getOrCreateUser(ownerUniId);
        long amountCents = toCents(amount);
        long currentCents = user.getBalanceCents() != null ? user.getBalanceCents() : 0L;

        long updatedCents = currentCents + amountCents;
        user.setBalanceCents(updatedCents);
        userRepository.save(user);

        BigDecimal balanceAfter = toDollars(updatedCents);
        if (recordLedger && transactionLedgerService != null && type != null) {
            transactionLedgerService.record(ownerUniId, jobId, type, amount, balanceAfter, description);
        }

        return balanceAfter;
    }

    private User getOrCreateUser(String ownerUniId) {
        return userRepository.findByUniId(ownerUniId)
                .orElseGet(() -> {
                    long defaultCents = toCents(defaultStartingBalance);
                    User newUser = User.builder()
                            .uniId(ownerUniId)
                            .email(ownerUniId + "@student.uwa.edu.au")
                            .fullName("Student " + ownerUniId)
                            .passwordHash("$2a$10$placeholderForUnseenStudentUserOnly")
                            .role(Role.STUDENT)
                            .balanceCents(defaultCents)
                            .build();
                    return userRepository.save(newUser);
                });
    }

    private long toCents(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private BigDecimal toDollars(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}

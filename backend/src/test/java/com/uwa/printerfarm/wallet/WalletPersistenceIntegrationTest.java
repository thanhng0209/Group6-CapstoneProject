package com.uwa.printerfarm.wallet;

import com.uwa.printerfarm.enums.Role;
import com.uwa.printerfarm.model.User;
import com.uwa.printerfarm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(WalletService.class)
class WalletPersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletService walletService;

    private static final String TEST_UNI_ID = "11223344";

    @BeforeEach
    void setUp() {
        userRepository.findByUniId(TEST_UNI_ID).ifPresent(userRepository::delete);
        User user = User.builder()
                .uniId(TEST_UNI_ID)
                .email("11223344@student.uwa.edu.au")
                .fullName("Persistence Test Student")
                .passwordHash("hashed")
                .role(Role.STUDENT)
                .balanceCents(5000L) // $50.00
                .build();
        userRepository.save(user);
    }

    @Test
    void debitDeductsAndPersistsBalanceCentsInDatabase() {
        // Initial state
        assertThat(walletService.getBalance(TEST_UNI_ID)).isEqualByComparingTo("50.00");

        // First deduction: $15.50
        BigDecimal newBalance = walletService.debit(TEST_UNI_ID, new BigDecimal("15.50"));
        assertThat(newBalance).isEqualByComparingTo("34.50");

        // Verify direct database read
        User dbUser = userRepository.findByUniId(TEST_UNI_ID).orElseThrow();
        assertThat(dbUser.getBalanceCents()).isEqualTo(3450L);

        // Second deduction: $4.50
        BigDecimal secondBalance = walletService.debit(TEST_UNI_ID, new BigDecimal("4.50"));
        assertThat(secondBalance).isEqualByComparingTo("30.00");

        // Verify second persistence
        dbUser = userRepository.findByUniId(TEST_UNI_ID).orElseThrow();
        assertThat(dbUser.getBalanceCents()).isEqualTo(3000L);
    }

    @Test
    void overdrawThrowsExceptionAndPreservesDatabaseBalance() {
        assertThatThrownBy(() -> walletService.debit(TEST_UNI_ID, new BigDecimal("100.00")))
                .isInstanceOf(InsufficientBalanceException.class);

        // Database value must remain untouched
        User dbUser = userRepository.findByUniId(TEST_UNI_ID).orElseThrow();
        assertThat(dbUser.getBalanceCents()).isEqualTo(5000L);
    }

    @Test
    void creditIncreasesAndPersistsBalanceCentsInDatabase() {
        BigDecimal balanceAfter = walletService.credit(TEST_UNI_ID, new BigDecimal("25.00"));
        assertThat(balanceAfter).isEqualByComparingTo("75.00");

        User dbUser = userRepository.findByUniId(TEST_UNI_ID).orElseThrow();
        assertThat(dbUser.getBalanceCents()).isEqualTo(7500L);
    }
}

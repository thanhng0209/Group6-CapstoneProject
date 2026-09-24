package com.uwa.printerfarm.security;

import com.uwa.printerfarm.enums.Role;
import com.uwa.printerfarm.model.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-characters-long";

    private JwtUtil jwtUtil(String secret) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", secret);
        ReflectionTestUtils.setField(util, "expirationMs", 60_000L);
        util.init();
        return util;
    }

    private UserPrincipal principal() {
        return new UserPrincipal(User.builder()
                .uniId("22345678")
                .fullName("Test Student")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build());
    }

    @Test
    void tokenSignedWithConfiguredSecretRoundTrips() {
        JwtUtil util = jwtUtil(SECRET);

        String token = util.generateToken(principal());

        assertThat(util.extractUniId(token)).isEqualTo("22345678");
        assertThat(util.extractRole(token)).isEqualTo("STUDENT");
    }

    @Test
    void tokenFromDifferentSecretIsRejected() {
        String token = jwtUtil(SECRET).generateToken(principal());

        assertThatThrownBy(() -> jwtUtil(SECRET.replace("unit", "other")).extractUniId(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void blankSecretFallsBackToRandomKeyInsteadOfAKnownOne() {
        JwtUtil first = jwtUtil("");
        JwtUtil second = jwtUtil("   ");

        String token = first.generateToken(principal());

        assertThat(first.extractUniId(token)).isEqualTo("22345678");
        assertThatThrownBy(() -> second.extractUniId(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void unsetSecretFallsBackToRandomKey() {
        JwtUtil util = jwtUtil(null);

        assertThat(util.extractUniId(util.generateToken(principal()))).isEqualTo("22345678");
    }
}

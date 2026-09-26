package com.uwa.printerfarm.service;

import com.uwa.printerfarm.dto.LoginRequest;
import com.uwa.printerfarm.dto.LoginResponse;
import com.uwa.printerfarm.enums.Role;
import com.uwa.printerfarm.job.JobRepository;
import com.uwa.printerfarm.model.User;
import com.uwa.printerfarm.repository.UserRepository;
import com.uwa.printerfarm.security.JwtUtil;
import com.uwa.printerfarm.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.uwa.printerfarm.dto.UserDashboardResponse;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

        @Mock
        private JobRepository jobRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private Authentication authentication;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(authenticationManager, userRepository, jobRepository, jwtUtil);
    }

    @Test
    void loginWithValidCredentialsReturnsLoginResponse() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUniId("12345678");
        request.setPassword("test-password");

        User user = User.builder()
                .id(1L)
                .uniId("12345678")
                .email("student@example.com")
                .passwordHash("encoded-password")
                .fullName("Test Student")
                .role(Role.STUDENT)
                .balanceCents(2500L)
                .build();

        UserPrincipal principal = new UserPrincipal(user);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(jwtUtil.generateToken(principal)).thenReturn("test-jwt-token");

        // Act
        LoginResponse response = userService.login(request);

        // Assert
        assertNotNull(response);
        assertEquals("test-jwt-token", response.getToken());
        assertEquals("12345678", response.getUniId());
        assertEquals("Test Student", response.getFullName());
        assertEquals("STUDENT", response.getRole());

        verify(authenticationManager).authenticate(argThat(auth ->
                "12345678".equals(auth.getPrincipal())
                        && "test-password".equals(auth.getCredentials())
        ));
        verify(jwtUtil).generateToken(principal);
    }

    @Test
    void loginWithInvalidCredentialsThrowsException() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUniId("12345678");
        request.setPassword("wrong-password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // Act + Assert
        assertThrows(
                BadCredentialsException.class,
                () -> userService.login(request)
        );

        verify(jwtUtil, never()).generateToken(any(UserPrincipal.class));
    }


    @Test
void getDashboardReturnsUserProfileAndBalance() {
    User user = User.builder()
            .id(1L)
            .uniId("12345678")
            .email("student@example.com")
            .passwordHash("encoded-password")
            .fullName("Test Student")
            .role(Role.STUDENT)
            .balanceCents(2500L)
            .build();

    when(userRepository.findByUniId("12345678"))
            .thenReturn(Optional.of(user));

    when(jobRepository.findByOwnerUniIdOrderByQueuedAtDesc("12345678"))
            .thenReturn(Collections.emptyList());

    UserDashboardResponse response = userService.getDashboard("12345678");

    assertNotNull(response);
    assertEquals("12345678", response.getUniId());
    assertEquals("Test Student", response.getFullName());
    assertEquals("student@example.com", response.getEmail());
    assertEquals("STUDENT", response.getRole());
    assertEquals(25.0, response.getBalance());

    assertNotNull(response.getCurrentJobs());
    assertTrue(response.getCurrentJobs().isEmpty());

    assertNotNull(response.getPrintHistory());
    assertTrue(response.getPrintHistory().isEmpty());

    verify(userRepository).findByUniId("12345678");
    verify(jobRepository).findByOwnerUniIdOrderByQueuedAtDesc("12345678");
}


}
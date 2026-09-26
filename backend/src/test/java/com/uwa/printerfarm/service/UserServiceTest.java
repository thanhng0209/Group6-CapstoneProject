package com.uwa.printerfarm.service;

import com.uwa.printerfarm.dto.LoginRequest;
import com.uwa.printerfarm.dto.LoginResponse;
import com.uwa.printerfarm.enums.Role;
import com.uwa.printerfarm.job.Job;
import com.uwa.printerfarm.job.JobRepository;
import com.uwa.printerfarm.job.JobStatus;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

@Test
void getDashboardThrowsExceptionWhenUserDoesNotExist() {
    when(userRepository.findByUniId("99999999"))
            .thenReturn(Optional.empty());

    IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> userService.getDashboard("99999999")
    );

    assertEquals("User not found: 99999999", exception.getMessage());

    verify(userRepository).findByUniId("99999999");
    verifyNoInteractions(jobRepository);
}

@Test
void getDashboardSeparatesCurrentAndHistoricalJobs() {
    User user = User.builder()
            .id(1L)
            .uniId("12345678")
            .email("student@example.com")
            .passwordHash("encoded-password")
            .fullName("Test Student")
            .role(Role.STUDENT)
            .balanceCents(2500L)
            .build();

    Job queuedJob = mock(Job.class);
    when(queuedJob.getId()).thenReturn(1L);
    when(queuedJob.getFileName()).thenReturn("queued-print.gcode");
    when(queuedJob.getPrinterId()).thenReturn("printer-01");
    when(queuedJob.getStatus()).thenReturn(JobStatus.QUEUED);
    when(queuedJob.getQueuedAt()).thenReturn(Instant.parse("2026-09-26T01:00:00Z"));

    Job printingJob = mock(Job.class);
    when(printingJob.getId()).thenReturn(2L);
    when(printingJob.getFileName()).thenReturn("printing-print.gcode");
    when(printingJob.getPrinterId()).thenReturn("printer-02");
    when(printingJob.getStatus()).thenReturn(JobStatus.PRINTING);
    when(printingJob.getQueuedAt()).thenReturn(Instant.parse("2026-09-26T02:00:00Z"));

    Job completedJob = mock(Job.class);
    when(completedJob.getId()).thenReturn(3L);
    when(completedJob.getFileName()).thenReturn("completed-print.gcode");
    when(completedJob.getPrinterId()).thenReturn("printer-03");
    when(completedJob.getStatus()).thenReturn(JobStatus.COMPLETED);
    when(completedJob.getQueuedAt()).thenReturn(Instant.parse("2026-09-25T01:00:00Z"));

    Job failedJob = mock(Job.class);
    when(failedJob.getId()).thenReturn(4L);
    when(failedJob.getFileName()).thenReturn("failed-print.gcode");
    when(failedJob.getPrinterId()).thenReturn("printer-04");
    when(failedJob.getStatus()).thenReturn(JobStatus.FAILED);
    when(failedJob.getQueuedAt()).thenReturn(Instant.parse("2026-09-24T01:00:00Z"));

    Job cancelledJob = mock(Job.class);
    when(cancelledJob.getId()).thenReturn(5L);
    when(cancelledJob.getFileName()).thenReturn("cancelled-print.gcode");
    when(cancelledJob.getPrinterId()).thenReturn("printer-05");
    when(cancelledJob.getStatus()).thenReturn(JobStatus.CANCELLED);
    when(cancelledJob.getQueuedAt()).thenReturn(Instant.parse("2026-09-23T01:00:00Z"));

    when(userRepository.findByUniId("12345678"))
            .thenReturn(Optional.of(user));

    when(jobRepository.findByOwnerUniIdOrderByQueuedAtDesc("12345678"))
            .thenReturn(List.of(
                    queuedJob,
                    printingJob,
                    completedJob,
                    failedJob,
                    cancelledJob
            ));

    UserDashboardResponse response = userService.getDashboard("12345678");

    assertEquals(2, response.getCurrentJobs().size());
    assertEquals(3, response.getPrintHistory().size());

    assertEquals("queued-print.gcode",
            response.getCurrentJobs().get(0).getFileName());
    assertEquals("printing-print.gcode",
            response.getCurrentJobs().get(1).getFileName());

    assertEquals("completed-print.gcode",
            response.getPrintHistory().get(0).getFileName());
    assertEquals("failed-print.gcode",
            response.getPrintHistory().get(1).getFileName());
    assertEquals("cancelled-print.gcode",
            response.getPrintHistory().get(2).getFileName());

    assertEquals("QUEUED",
            response.getCurrentJobs().get(0).getStatus());
    assertEquals("PRINTING",
            response.getCurrentJobs().get(1).getStatus());

    assertEquals("COMPLETED",
            response.getPrintHistory().get(0).getStatus());
    assertEquals("FAILED",
            response.getPrintHistory().get(1).getStatus());
    assertEquals("CANCELLED",
            response.getPrintHistory().get(2).getStatus());

    verify(userRepository).findByUniId("12345678");
    verify(jobRepository).findByOwnerUniIdOrderByQueuedAtDesc("12345678");
}
}
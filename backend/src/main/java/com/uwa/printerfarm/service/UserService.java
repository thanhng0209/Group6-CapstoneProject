package com.uwa.printerfarm.service;

import com.uwa.printerfarm.dto.LoginRequest;
import com.uwa.printerfarm.dto.LoginResponse;
import com.uwa.printerfarm.dto.UserDashboardResponse;
import com.uwa.printerfarm.dto.UserDashboardResponse.PrintJobSummary;
import com.uwa.printerfarm.job.Job;
import com.uwa.printerfarm.job.JobRepository;
import com.uwa.printerfarm.model.User;
import com.uwa.printerfarm.repository.UserRepository;
import com.uwa.printerfarm.security.JwtUtil;
import com.uwa.printerfarm.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final JwtUtil jwtUtil;

    /**
     * Validates UNI ID + password via Spring Security's AuthenticationManager,
     * then issues a JWT for subsequent requests.
     */
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUniId(),
                        request.getPassword()
                )
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtUtil.generateToken(principal);

        return new LoginResponse(
                token,
                principal.getUniId(),
                principal.getFullName(),
                principal.getRole()
        );
    }

    /**
     * Builds the dashboard payload for the authenticated user:
     * profile, balance, current jobs and print history.
     */
    public UserDashboardResponse getDashboard(String uniId) {
        User user = userRepository.findByUniId(uniId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + uniId));

        List<Job> jobs = jobRepository.findByOwnerUniIdOrderByQueuedAtDesc(uniId);

        List<PrintJobSummary> currentJobs = jobs.stream()
                .filter(job -> !isCompletedOrFailed(job))
                .map(this::toPrintJobSummary)
                .collect(Collectors.toList());

        List<PrintJobSummary> printHistory = jobs.stream()
                .filter(this::isCompletedOrFailed)
                .map(this::toPrintJobSummary)
                .collect(Collectors.toList());

        return UserDashboardResponse.builder()
                .uniId(user.getUniId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .balance(user.getBalanceCents() / 100.0)
                .currentJobs(currentJobs)
                .printHistory(printHistory)
                .build();
    }

    private boolean isCompletedOrFailed(Job job) {
        String status = job.getStatus().name();

        return "COMPLETED".equals(status)
                                || "FAILED".equals(status)
                                || "CANCELLED".equals(status);
    }

    private PrintJobSummary toPrintJobSummary(Job job) {
        return PrintJobSummary.builder()
                .jobId(job.getId())
                .fileName(job.getFileName())
                .printerName(job.getPrinterId())
                .status(job.getStatus().name())
                .submittedAt(
                        job.getQueuedAt() != null
                                ? job.getQueuedAt().toString()
                                : null
                )
                .progressPercent(job.getProgressPercent())
                .remainingSeconds(job.getRemainingSeconds())
                .build();
    }
}
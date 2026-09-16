package com.uwa.printerfarm.service;

import com.uwa.printerfarm.dto.LoginRequest;
import com.uwa.printerfarm.dto.LoginResponse;
import com.uwa.printerfarm.dto.UserDashboardResponse;
import com.uwa.printerfarm.model.User;
import com.uwa.printerfarm.repository.UserRepository;
import com.uwa.printerfarm.security.JwtUtil;
import com.uwa.printerfarm.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    /**
     * Validates UNI ID + password via Spring Security's AuthenticationManager,
     * then issues a JWT for subsequent requests.
     */
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUniId(), request.getPassword())
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtUtil.generateToken(principal);

        return new LoginResponse(token, principal.getUniId(), principal.getFullName(), principal.getRole());
    }

    /**
     * Builds the dashboard payload: profile, balance, current jobs, print history.
     * Job data will be wired in once the PrintJob entity exists (Workstream 2/5);
     * until then this returns real profile/balance data with empty job lists,
     * which is enough to satisfy the acceptance check for this feature.
     */
    public UserDashboardResponse getDashboard(String uniId) {
        User user = userRepository.findByUniId(uniId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + uniId));

        return UserDashboardResponse.builder()
                .uniId(user.getUniId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .balance(user.getBalanceCents() / 100.0)
                .currentJobs(Collections.emptyList())
                .printHistory(Collections.emptyList())
                .build();
    }
}

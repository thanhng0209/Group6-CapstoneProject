package com.uwa.printerfarm.controller;

import com.uwa.printerfarm.dto.UserDashboardResponse;
import com.uwa.printerfarm.security.UserPrincipal;
import com.uwa.printerfarm.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Protected by JwtAuthFilter + SecurityConfig ("anyRequest().authenticated()").
     * @AuthenticationPrincipal injects the logged-in user straight from the JWT —
     * the uniId here is never taken from a request parameter, so a user can only
     * ever see their own dashboard.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<UserDashboardResponse> getDashboard(@AuthenticationPrincipal UserPrincipal principal) {
        UserDashboardResponse dashboard = userService.getDashboard(principal.getUniId());
        return ResponseEntity.ok(dashboard);
    }
}

package com.uwa.printerfarm.service;

import com.uwa.printerfarm.repository.UserRepository;
import com.uwa.printerfarm.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Called in two places:
     *  1. At login, with what the user typed (after LoginIdentifier cleanup):
     *     an 8-digit uni_id, or a staff email such as lab.coordinator@uwa.edu.au.
     *  2. By JwtAuthFilter on every request, with the JWT subject (always the uni_id).
     *
     * So: try uni_id first (the existing behaviour), then fall back to the email column.
     * The returned UserPrincipal always carries the real uni_id, so the JWT subject,
     * wallet ownership checks and job ownership keep working exactly as before.
     */
    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        return userRepository.findByUniId(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user found for: " + identifier));
    }
}
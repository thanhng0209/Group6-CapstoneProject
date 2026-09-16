package com.uwa.printerfarm.security;

import com.uwa.printerfarm.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapts our User entity to Spring Security's UserDetails so the
 * standard auth machinery (AuthenticationManager, filters, etc.) can use it.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String uniId;
    private final String passwordHash;
    private final String fullName;
    private final String role;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.uniId = user.getUniId();
        this.passwordHash = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.role = user.getRole().name();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring Security convention: role names prefixed with "ROLE_"
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return uniId;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}

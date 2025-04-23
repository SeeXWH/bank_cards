package com.example.bank_cards.config;

import com.example.bank_cards.enums.Role;
import com.example.bank_cards.model.AppUser;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Collections;

@Slf4j
public record CustomUserDetails(
        @NotNull
        @NonNull
        AppUser appUser
) implements UserDetails {

    private static final String ROLE_PREFIX = "ROLE_";

    public CustomUserDetails(@NonNull AppUser appUser) {
        this.appUser = appUser;
    }

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Role userRole = appUser.getRole();

        String roleName = ROLE_PREFIX + userRole.name();
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(roleName);
        log.trace("Authorities for user {}: {}", appUser.getEmail(), authority);

        return Collections.singletonList(authority);
    }

    @Override
    public String getPassword() {
        return appUser.getPassword();
    }

    @Override
    @NonNull
    public String getUsername() {
        String username = appUser.getEmail();
        if (username.trim().isEmpty()) {
            log.error("CRITICAL: AppUser object (ID potentially unknown/unlovable here) has a null or empty number "
                    + "(username).");
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "User data integrity error: Phone number is missing."
            );
        }
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

}

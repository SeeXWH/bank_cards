package com.example.bank_cards.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@Configuration
@Slf4j
public class AppConfig {
    private static final int BCRYPT_STRENGTH = 8;

    @PostConstruct
    public void initialize() {
        log.info("AppConfig initialization complete. BCrypt strength configured to: {}", BCRYPT_STRENGTH);
    }

    @Bean
    @NonNull
    public HttpHeaders httpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        log.debug("Default HttpHeaders bean created successfully.");
        return headers;
    }

    @Bean
    @NonNull
    public AuthenticationManager authenticationManager(
            @NonNull AuthenticationConfiguration configuration
    ) throws ResponseStatusException {
        try {
            AuthenticationManager manager = configuration.getAuthenticationManager();
            log.debug("AuthenticationManager bean retrieved successfully: {}", manager.getClass().getSimpleName());
            return manager;
        }
        catch (Exception e) {
            log.error("CRITICAL: Failed to obtain AuthenticationManager bean from configuration. " +
                    "This usually indicates a problem with the Spring Security setup.");
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not configure AuthenticationManager. Check Spring Security setup.");
        }
    }

    @Bean
    @NonNull
    public PasswordEncoder passwordEncoder() {
        log.info("Creating PasswordEncoder bean (BCrypt) with strength: {}", BCRYPT_STRENGTH);
        if (BCRYPT_STRENGTH < 4 || BCRYPT_STRENGTH > 31) {
            log.error("CRITICAL: Invalid BCrypt strength configured: {}. Must be between 4 and 31.", BCRYPT_STRENGTH);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Invalid BCrypt strength configured: " + BCRYPT_STRENGTH
            );
        }
        PasswordEncoder encoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
        log.debug("PasswordEncoder bean (BCrypt, strength={}) created successfully.", BCRYPT_STRENGTH);
        return encoder;
    }
}

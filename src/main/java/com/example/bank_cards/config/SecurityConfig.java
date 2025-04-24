package com.example.bank_cards.config;

import com.example.bank_cards.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/users/register/**",
            "/api/users/login/**",
            "/api/swagger-ui/**",
            "/api/v3/api-docs/**",
            "/api/api-docs/**",
            "/api/swagger-ui/index.html/**",
            "/api/swagger-ui.html/**",
            "/api/users/recoveryPassword/**"
    };

    @Bean
    @NonNull
    public SecurityFilterChain securityFilterChain(
            @NonNull HttpSecurity http,
            @NonNull JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/api/moderator/**").hasAnyRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(customAuthenticationEntryPoint())
                        .accessDeniedHandler(customAccessDeniedHandler())
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        ;

        log.info("Security Filter Chain configured successfully.");
        return http.build();
    }
    @Bean
    @NonNull
    public AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            log.warn("Unauthorized access attempt to {}: {}", request.getRequestURI(), authException.getMessage());
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Access requires "
                    + "authentication");
        };
    }
    @Bean
    @NonNull
    public AccessDeniedHandler customAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            String username = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous";
            log.warn("Access Denied for user '{}' attempting to access {}: {}", username, request.getRequestURI(),
                    accessDeniedException.getMessage()
            );
            writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden: You do not have permission to "
                    + "access this resource");
        };
    }
    private void writeErrorResponse(@NonNull HttpServletResponse response, int status, @NonNull String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter writer = response.getWriter()) {
            writer.write(message);
            writer.flush();
        }
    }
}

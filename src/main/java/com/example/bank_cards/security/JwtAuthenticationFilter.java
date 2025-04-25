package com.example.bank_cards.security;

import com.example.bank_cards.config.CustomUserDetails;
import com.example.bank_cards.config.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;


import java.io.IOException;
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            @NonNull JwtTokenProvider tokenProvider,
            @NonNull CustomUserDetailsService userDetailsService
    ) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @NonNull
    public static String getJwtFromRequest(@NonNull HttpServletRequest request) {
        final String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    @Override
    protected void doFilterInternal(
            @Nullable HttpServletRequest request,
            @Nullable HttpServletResponse response,
            @Nullable FilterChain filterChain
    ) throws ServletException, IOException {
        if (request == null || response == null || filterChain == null) {
            log.error("Received null request, response, or filterChain. Aborting filter processing.");
            if (response != null) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Core filter components are missing.");
            }
            return;
        }

        final String requestURI = request.getRequestURI();
        String jwt = getJwtFromRequest(request);

        if (StringUtils.hasText(jwt)) {
            if (tokenProvider.validateToken(jwt)) {
                String email = tokenProvider.getEmailFromToken(jwt);

                if (!StringUtils.hasText(email)) {
                    SecurityContextHolder.clearContext();
                } else {
                    CustomUserDetails userDetails =
                            (CustomUserDetails) userDetailsService.loadUserByUsername(email);
                    if (userDetails.appUser().isLocked()) {
                        log.warn("Blocked user '{}' attempted to access URI: {}", email, requestURI);
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "User is blocked");
                        return;
                    }
                    JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                            userDetails, jwt, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.info("Successfully authenticated user '{}' via JWT for URI: {}",
                            userDetails.getUsername(), requestURI
                    );
                }
            } else {
                log.warn("JWT token validation failed for URI: {}. Token might be expired or invalid.", requestURI);
                SecurityContextHolder.clearContext();
            }
        } else {
            log.trace(
                    "No JWT found in Authorization header for URI: {}. Proceeding without authentication.",
                    requestURI
            );
        }

        filterChain.doFilter(request, response);
    }
}


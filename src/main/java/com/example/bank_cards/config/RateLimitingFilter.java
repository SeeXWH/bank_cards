package com.example.bank_cards.config;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitingFilter implements Filter {

    @NonNull
    private final Bucket bucket;


    @PostConstruct
    public void initFilter() {
    }


    @Override
    public void doFilter(
            @NonNull ServletRequest request,
            @NonNull ServletResponse response,
            @NonNull FilterChain chain
    )
            throws IOException, ServletException {

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            log.trace(
                    "Rate limit check passed for request. Remaining tokens: {}. Proceeding with filter chain.",
                    probe.getRemainingTokens()
            );
            chain.doFilter(request, response);
        } else {
            long nanosToWaitForRefill = probe.getNanosToWaitForRefill();
            long millisToWaitForRefill = TimeUnit.NANOSECONDS.toMillis(nanosToWaitForRefill);
            long secondsToWaitForRetry = TimeUnit.NANOSECONDS.toSeconds(nanosToWaitForRefill) + 1;

            String remoteAddr = "unknown";
            if (request instanceof HttpServletRequest httpRequest) {
                remoteAddr = httpRequest.getRemoteAddr();
            }

            log.warn("Rate limit exceeded for client {}. Request rejected. Wait time: ~{}ms ({}s).",
                    remoteAddr, millisToWaitForRefill, secondsToWaitForRetry
            );

            if (response instanceof HttpServletResponse httpServletResponse) {
                try {
                    httpServletResponse.setStatus(429);
                    httpServletResponse.setHeader("Retry-After", String.valueOf(secondsToWaitForRetry));
                    httpServletResponse.setContentType("application/json");
                    httpServletResponse.getWriter().write("Rate limit exceeded. Please try again later. "
                            + "RetryAfterSeconds: " + secondsToWaitForRetry);
                    httpServletResponse.getWriter().flush();
                }
                catch (IOException e) {
                    log.error("IOException occurred while writing 429 response body for client {}", remoteAddr);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to write rate limit "
                            + "exceeded response");
                }
                catch (IllegalStateException e) {
                    log.error("IllegalStateException occurred while setting 429 response for client {} (response "
                            + "likely already committed)", remoteAddr);
                }
            } else {
                log.error("CRITICAL: ServletResponse could not be cast to HttpServletResponse in RateLimitingFilter. "
                        + "Cannot set 429 status.");
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot handle non-HTTP response "
                        + "for rate limiting");
            }
        }
    }


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
    }


    @Override
    public void destroy() {
        Filter.super.destroy();
    }
}
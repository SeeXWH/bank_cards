package com.example.bank_cards.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;


@Configuration
@Slf4j
public class RateLimitConfig {
    private static final long BUCKET_CAPACITY = 500;
    private static final long REFILL_TOKENS = 1;
    private static final Duration REFILL_DURATION = Duration.ofSeconds(1);

    @PostConstruct
    public void init() {
        validateRateLimitParameters();
    }


    @Bean
    public Bucket bucket() {
        log.debug("Creating Bucket4j Bucket bean...");

        validateRateLimitParameters();

        try {
            Bandwidth limit = Bandwidth.classic(BUCKET_CAPACITY, Refill.greedy(REFILL_TOKENS, REFILL_DURATION));
            log.debug("Defined Bandwidth: Classic (Capacity={}, Refill={}/{} using Greedy)",
                    BUCKET_CAPACITY, REFILL_TOKENS, REFILL_DURATION
            );
            Bucket bucket = Bucket4j.builder()
                    .addLimit(limit)
                    .build()
                    ;

            log.info("Successfully created Bucket4j Bucket bean with capacity {} and refill rate {}/{}",
                    BUCKET_CAPACITY, REFILL_TOKENS, REFILL_DURATION
            );
            return bucket;

        }
        catch (IllegalArgumentException e) {
            log.error("CRITICAL: Invalid parameters detected by Bucket4j during Bucket creation. Capacity={}, "
                            + "Refill={}/{}",
                    BUCKET_CAPACITY, REFILL_TOKENS, REFILL_DURATION);
            throw new IllegalArgumentException("Invalid configuration parameters provided for Bucket4j rate limiter.");
        }
        catch (Exception e) {
            log.error("CRITICAL: Failed to build the Bucket4j Bucket due to an unexpected error.");
            throw new IllegalStateException(
                    "Unexpected error occurred while configuring the Bucket4j rate limiter.");
        }
    }

    private void validateRateLimitParameters() {
        if (BUCKET_CAPACITY <= 0) {
            log.error("CRITICAL CONFIGURATION ERROR: BUCKET_CAPACITY must be positive. Was: {}", BUCKET_CAPACITY);
            throw new IllegalArgumentException("Rate limiter configuration error: BUCKET_CAPACITY must be positive.");
        }
        if (REFILL_TOKENS <= 0) {
            log.error("CRITICAL CONFIGURATION ERROR: REFILL_TOKENS must be positive. Was: {}", REFILL_TOKENS);
            throw new IllegalArgumentException("Rate limiter configuration error: REFILL_TOKENS must be positive.");
        }
        if (REFILL_DURATION == null || REFILL_DURATION.isNegative() || REFILL_DURATION.isZero()) {
            log.error("CRITICAL CONFIGURATION ERROR: REFILL_DURATION must be positive. Was: {}", REFILL_DURATION);
            throw new IllegalArgumentException("Rate limiter configuration error: REFILL_DURATION must be positive.");
        }
        log.trace("Rate limit parameters validated successfully.");
    }
}

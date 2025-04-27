package com.example.bank_cards.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;


/**
 * Конфигурация для ограничения частоты запросов (rate limiting) с использованием библиотеки Bucket4j.
 * <p>
 * Этот класс определяет бин {@link Bucket}, который используется для контроля
 * количества запросов, обрабатываемых приложением за определенный период времени.
 * </p>
 */
@Configuration
@Slf4j
public class RateLimitConfig {
    /**
     * Максимальная емкость "ведра" (bucket). Определяет максимальное количество токенов,
     * которое может храниться в ведре, и, следовательно, максимальное количество запросов,
     * которое может быть обработано мгновенно (burst capacity). Должно быть положительным числом.
     */
    private static final long BUCKET_CAPACITY = 500;
    /**
     * Количество токенов, добавляемых в ведро при каждом пополнении.
     * Должно быть положительным числом.
     */
    private static final long REFILL_TOKENS = 1;
    /**
     * Период времени, через который происходит пополнение ведра токенами.
     * Должен быть положительной длительностью.
     */
    private static final Duration REFILL_DURATION = Duration.ofSeconds(1);

    /**
     * Метод инициализации, выполняемый после создания бина {@code RateLimitConfig}.
     * Вызывает валидацию параметров ограничения частоты запросов.
     */
    @PostConstruct
    public void init() {
        validateRateLimitParameters();
    }


    /**
     * Определяет и настраивает бин {@link Bucket} для ограничения частоты запросов.
     * <p>
     * Создает экземпляр {@code Bucket} с использованием классической модели пропускной способности (Bandwidth),
     * заданной параметрами {@link #BUCKET_CAPACITY}, {@link #REFILL_TOKENS} и {@link #REFILL_DURATION}.
     * Используется жадное пополнение (greedy refill). Перед созданием бина выполняется валидация параметров.
     * </p>
     *
     * @return Настроенный бин {@link Bucket}.
     * @throws IllegalArgumentException если предоставленные параметры конфигурации (емкость, токены пополнения,
     *                                  длительность пополнения) недопустимы (например, неположительные).
     * @throws IllegalStateException    если происходит непредвиденная ошибка при создании бина {@code Bucket}.
     */
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
                    .build();
            log.info("Successfully created Bucket4j Bucket bean with capacity {} and refill rate {}/{}",
                    BUCKET_CAPACITY, REFILL_TOKENS, REFILL_DURATION
            );
            return bucket;
        } catch (IllegalArgumentException e) {
            log.error("CRITICAL CONFIGURATION ERROR: Invalid parameters detected for Bucket creation. Capacity={}, Refill={}/{}, Duration={}. Error: {}",
                    BUCKET_CAPACITY, REFILL_TOKENS, REFILL_DURATION, e.getMessage());
            throw new IllegalArgumentException("Invalid configuration parameters provided for Bucket4j rate limiter.", e);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to build the Bucket4j Bucket due to an unexpected error.", e);
            throw new IllegalStateException(
                    "Unexpected error occurred while configuring the Bucket4j rate limiter.", e);
        }
    }

    /**
     * Проверяет корректность параметров конфигурации для ограничения частоты запросов.
     * <p>
     * Убеждается, что {@link #BUCKET_CAPACITY}, {@link #REFILL_TOKENS} и {@link #REFILL_DURATION}
     * имеют допустимые положительные значения.
     * </p>
     *
     * @throws IllegalArgumentException если какой-либо из параметров недопустим.
     */
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
        log.trace("Rate limit parameters validated successfully: Capacity={}, Refill={}/{}",
                BUCKET_CAPACITY, REFILL_TOKENS, REFILL_DURATION);
    }
}
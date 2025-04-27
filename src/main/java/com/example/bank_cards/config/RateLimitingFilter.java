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

/**
 * Servlet-фильтр для применения глобального ограничения частоты запросов (rate limiting)
 * ко всем входящим HTTP-запросам.
 * <p>
 * Использует сконфигурированный бин {@link Bucket} из Bucket4j для отслеживания
 * и ограничения количества обрабатываемых запросов. Если лимит превышен, фильтр
 * отклоняет запрос с HTTP-статусом 429 (Too Many Requests) и заголовком Retry-After.
 * </p>
 * <p>
 * Аннотация {@code @Order(Ordered.HIGHEST_PRECEDENCE + 1)} гарантирует, что этот фильтр
 * будет выполняться одним из первых в цепочке фильтров, но после основных фильтров безопасности Spring.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 1) 
public class RateLimitingFilter implements Filter {

    /**
     * Бин {@link Bucket}, используемый для проверки и потребления токенов ограничения частоты.
     * Внедряется через конструктор благодаря {@link RequiredArgsConstructor}. Гарантированно не {@code null}.
     */
    @NonNull
    private final Bucket bucket;


    /**
     * Метод инициализации фильтра, вызываемый после создания бина.
     * В текущей реализации не выполняет дополнительных действий.
     */
    @PostConstruct
    public void initFilter() {
        log.info("RateLimitingFilter initialized with Bucket: {}", bucket);
    }


    /**
     * Основной метод фильтра, обрабатывающий каждый входящий запрос.
     * <p>
     * Пытается потребить один токен из {@link #bucket}.
     * <ul>
     *     <li>Если токен успешно потреблен, запрос передается дальше по цепочке фильтров ({@code chain.doFilter()}).</li>
     *     <li>Если токены закончились (лимит превышен), метод формирует HTTP-ответ со статусом 429 (Too Many Requests),
     *         устанавливает заголовок {@code Retry-After}, указывающий, через сколько секунд можно повторить запрос,
     *         и записывает сообщение об ошибке в тело ответа. Запрос дальше по цепочке не передается.</li>
     * </ul>
     * Логирует как успешные проверки, так и случаи превышения лимита.
     * </p>
     *
     * @param request  Входящий запрос {@link ServletRequest}. Не может быть {@code null}.
     * @param response Исходящий ответ {@link ServletResponse}. Не может быть {@code null}.
     * @param chain    Цепочка фильтров {@link FilterChain}. Не может быть {@code null}.
     * @throws IOException      если возникает ошибка ввода-вывода при обработке запроса/ответа
     *                          или при записи ответа об ошибке 429.
     * @throws ServletException если возникает ошибка сервлета при передаче запроса дальше по цепочке.
     * @throws ResponseStatusException если происходит ошибка при формировании ответа 429
     *                          (например, при записи в уже закрытый поток или если ответ не является HTTP-ответом).
     */
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
            log.warn("Rate limit exceeded for client {}. Request rejected. Wait time: ~{}ms (Retry-After: {}s).",
                    remoteAddr, millisToWaitForRefill, secondsToWaitForRetry
            );
            if (response instanceof HttpServletResponse httpServletResponse) {
                try {
                    httpServletResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); 
                    httpServletResponse.setHeader("Retry-After", String.valueOf(secondsToWaitForRetry)); 
                    httpServletResponse.setContentType("application/json");
                    httpServletResponse.getWriter().write("{\"error\": \"Rate limit exceeded. Please try again later.\", \"retryAfterSeconds\": "
                            + secondsToWaitForRetry + "}");
                    httpServletResponse.getWriter().flush();
                } catch (IOException e) {
                    log.error("IOException occurred while writing 429 response body for client {}: {}", remoteAddr, e.getMessage());
                } catch (IllegalStateException e) {
                    log.error("IllegalStateException occurred while setting 429 response for client {} (response likely already committed): {}", remoteAddr, e.getMessage());
                }
            } else {
                log.error("CRITICAL: ServletResponse could not be cast to HttpServletResponse in RateLimitingFilter for client {}. Cannot set 429 status.", remoteAddr);
                throw new ServletException("Cannot handle non-HTTP response for rate limiting");
            }
        }
    }


    /**
     * Инициализация фильтра контейнером сервлетов.
     * Использует реализацию по умолчанию из интерфейса {@link Filter}.
     *
     * @param filterConfig конфигурация фильтра.
     * @throws ServletException если возникает ошибка при инициализации.
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
        log.debug("RateLimitingFilter initialized via init(FilterConfig).");
    }


    /**
     * Освобождение ресурсов фильтра при завершении работы приложения.
     * Использует реализацию по умолчанию из интерфейса {@link Filter}.
     */
    @Override
    public void destroy() {
        log.debug("RateLimitingFilter destroyed.");
        Filter.super.destroy();
    }
}
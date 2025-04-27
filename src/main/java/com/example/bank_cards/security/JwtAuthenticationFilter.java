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
/**
 * Фильтр для обработки JWT (JSON Web Token) аутентификации при каждом запросе.
 * <p>
 * Этот фильтр проверяет наличие JWT в заголовке {@code Authorization} входящего запроса.
 * Если токен найден и валиден, он извлекает информацию о пользователе,
 * загружает соответствующие данные пользователя ({@link CustomUserDetails}) и устанавливает
 * аутентификацию в {@link SecurityContextHolder}, позволяя защищенным эндпоинтам
 * обрабатывать запрос как аутентифицированный.
 * </p>
 * <p>
 * Наследуется от {@link OncePerRequestFilter}, что гарантирует выполнение фильтра только один раз
 * для каждого запроса в рамках одного потока.
 * </p>
 */
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

    /**
     * Извлекает JWT из заголовка {@code Authorization} HTTP-запроса.
     * <p>
     * Ожидает токен в формате "Bearer [токен]".
     * </p>
     *
     * @param request HTTP-запрос. Не может быть {@code null}.
     * @return Строка JWT без префикса "Bearer ", если токен найден и имеет правильный формат,
     *         иначе {@code null}.
     */
    @Nullable 
    public static String getJwtFromRequest(@NonNull HttpServletRequest request) {
        final String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * Основной метод фильтра, выполняющийся для каждого запроса.
     * <p>
     * Получает JWT из запроса, валидирует его. Если токен валиден, загружает
     * данные пользователя, проверяет статус блокировки пользователя и устанавливает аутентификацию
     * в контексте безопасности Spring. Затем передает запрос дальше по цепочке фильтров.
     * </p>
     * <p>
     * Если основные компоненты (request, response, filterChain) равны {@code null},
     * обработка прерывается с ошибкой сервера.
     * Если пользователь заблокирован, возвращается ошибка 403 Forbidden.
     * </p>
     *
     * @param request     HTTP-запрос. Может быть {@code null} (хотя обычно нет в стандартных сервлет-контейнерах).
     * @param response    HTTP-ответ. Может быть {@code null}.
     * @param filterChain Цепочка фильтров. Может быть {@code null}.
     * @throws ServletException если возникает ошибка сервлета при обработке запроса.
     * @throws IOException      если возникает ошибка ввода-вывода при обработке запроса или ответа.
     */
    @Override
    protected void doFilterInternal(
            @Nullable HttpServletRequest request, 
            @Nullable HttpServletResponse response,
            @Nullable FilterChain filterChain
    ) throws ServletException, IOException {
        if (request == null || response == null || filterChain == null) {
            log.error("CRITICAL: Received null request, response, or filterChain. Aborting filter processing.");
            if (response != null && !response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Core filter components are missing.");
            }
            return; 
        }
        final String requestURI = request.getRequestURI(); 
        try {
            String jwt = getJwtFromRequest(request);
            if (StringUtils.hasText(jwt)) { 
                if (tokenProvider.validateToken(jwt)) { 
                    String email = tokenProvider.getEmailFromToken(jwt);
                    if (!StringUtils.hasText(email)) {
                        log.warn("JWT is valid but contains no email (username) for URI: {}", requestURI);
                        SecurityContextHolder.clearContext(); 
                    } else {
                        CustomUserDetails userDetails =
                                (CustomUserDetails) userDetailsService.loadUserByUsername(email);
                        if (userDetails.appUser().isLocked()) {
                            log.warn("Blocked user '{}' attempted to access URI: {}", email, requestURI);
                            if (!response.isCommitted()) {
                                response.sendError(HttpServletResponse.SC_FORBIDDEN, "User account is locked.");
                            }
                            return; 
                        }
                        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                                userDetails, jwt, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("Successfully authenticated user '{}' via JWT for URI: {}",
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
        } catch (Exception e) {
            log.error("Error processing JWT authentication for URI: {}", requestURI, e);
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }
}
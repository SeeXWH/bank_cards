package com.example.bank_cards.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Точка входа для обработки ошибок аутентификации в контексте JWT (JSON Web Token).
 * <p>
 * Этот компонент реализует {@link AuthenticationEntryPoint} и вызывается Spring Security,
 * когда неаутентифицированный пользователь пытается получить доступ к защищенному ресурсу,
 * и аутентификация (в данном случае, через JWT) не удалась или отсутствует.
 * Он отвечает за отправку HTTP-ответа с кодом 401 (Unauthorized) клиенту.
 * </p>
 */
@Component
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    /**
     * Стандартное сообщение об ошибке, возвращаемое клиенту при отказе в доступе из-за отсутствия аутентификации.
     */
    private static final String ERROR_MESSAGE = "Unauthorized: Access requires authentication.";

    /**
     * Метод, вызываемый Spring Security при возникновении {@link AuthenticationException}.
     * <p>
     * Устанавливает HTTP-статус ответа на 401 (Unauthorized), задает тип контента как text/plain
     * с кодировкой UTF-8 и записывает {@link #ERROR_MESSAGE} в тело ответа.
     * Также логирует информацию о неудачной попытке аутентификации.
     * </p>
     *
     * @param request       HTTP-запрос, который привел к ошибке аутентификации. Не может быть {@code null}.
     * @param response      HTTP-ответ, в который будет записано сообщение об ошибке. Не может быть {@code null}.
     * @param authException Исключение, вызванное неудачной попыткой аутентификации. Не может быть {@code null}.
     * @throws IOException если возникает ошибка при записи в {@link HttpServletResponse#getWriter()}.
     */
    @Override
    public void commence(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException authException
    )
            throws IOException {
        log.warn("Authentication failed for request URI: {}. Reason: {}. Responding with 401 Unauthorized.",
                request.getRequestURI(), authException.getMessage()
        );
        log.debug("AuthenticationException details:", authException);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); 
        response.setContentType(MediaType.TEXT_PLAIN_VALUE); 
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter writer = response.getWriter()) {
            writer.write(ERROR_MESSAGE);
            writer.flush();
        }
    }
}
package com.example.bank_cards.config;

import com.example.bank_cards.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Конфигурационный класс для настройки Spring Security.
 * <p>
 * Активирует веб-безопасность ({@code @EnableWebSecurity}) и безопасность на уровне методов ({@code @EnableMethodSecurity}).
 * Определяет правила авторизации запросов, конфигурацию CORS, управление сессиями,
 * обработку исключений безопасности и интеграцию JWT-фильтра.
 * </p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) 
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {
    /**
     * Массив шаблонов URL-адресов, доступных публично (без аутентификации).
     * Включает эндпоинты для регистрации, входа, восстановления пароля и документации Swagger.
     */
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

    /**
     * Сервис для загрузки пользовательских данных, используемый Spring Security.
     * @see CustomUserDetailsService
     */
    private final CustomUserDetailsService userDetailsService;

    /**
     * Определяет основной бин цепочки фильтров безопасности Spring Security.
     * <p>
     * Настраивает:
     * <ul>
     *     <li>CORS (Cross-Origin Resource Sharing) с использованием конфигурации по умолчанию или предоставленной бином {@link #corsConfigurer}.</li>
     *     <li>Отключение CSRF (Cross-Site Request Forgery), так как используется stateless аутентификация (JWT).</li>
     *     <li>Правила авторизации запросов: разрешает доступ к {@link #PUBLIC_ENDPOINTS} и OPTIONS-запросам, требует роль 'ADMIN' для путей '/api/moderator/**', остальные запросы требуют аутентификации.</li>
     *     <li>Использование кастомного {@link CustomUserDetailsService} для загрузки данных пользователя.</li>
     *     <li>Политику управления сессиями: STATELESS (не создавать и не использовать HTTP сессии).</li>
     *     <li>Обработку исключений: использует кастомные {@link #customAuthenticationEntryPoint()} и {@link #customAccessDeniedHandler()}.</li>
     *     <li>Добавление {@link JwtAuthenticationFilter} перед стандартным фильтром аутентификации по имени пользователя и паролю.</li>
     * </ul>
     * </p>
     *
     * @param http                  Объект {@link HttpSecurity} для конфигурации безопасности. Не может быть {@code null}.
     * @param jwtAuthenticationFilter Фильтр для обработки JWT-аутентификации. Не может быть {@code null}.
     * @return Сконфигурированный объект {@link SecurityFilterChain}. Гарантированно не {@code null}.
     * @throws Exception если возникает ошибка при конфигурации {@link HttpSecurity}.
     */
    @Bean
    @NonNull
    public SecurityFilterChain securityFilterChain(
            @NonNull HttpSecurity http,
            @NonNull JwtAuthenticationFilter jwtAuthenticationFilter 
    ) throws Exception {
        log.debug("Configuring Security Filter Chain...");
        http
                .cors(Customizer.withDefaults()) 
                .csrf(AbstractHttpConfigurer::disable) 
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS).permitAll() 
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll() 
                        .requestMatchers("/api/moderator/**").hasAnyRole("ADMIN") 
                        .anyRequest().authenticated() 
                )
                .userDetailsService(userDetailsService) 
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS) 
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(customAuthenticationEntryPoint())
                        .accessDeniedHandler(customAccessDeniedHandler())
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        log.info("Security Filter Chain configured successfully with public endpoints: {}", Arrays.toString(PUBLIC_ENDPOINTS));
        return http.build();
    }

    /**
     * Определяет бин точки входа аутентификации (Authentication Entry Point).
     * <p>
     * Этот обработчик вызывается, когда неаутентифицированный пользователь
     * пытается получить доступ к защищенному ресурсу. Он отправляет ответ
     * с HTTP-статусом 401 (Unauthorized) и сообщением об ошибке.
     * </p>
     *
     * @return Реализация {@link AuthenticationEntryPoint}. Гарантированно не {@code null}.
     */
    @Bean
    @NonNull
    public AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            log.warn("Unauthorized access attempt to {}: {}", request.getRequestURI(), authException.getMessage());
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Access requires authentication");
        };
    }

    /**
     * Определяет бин обработчика отказа в доступе (Access Denied Handler).
     * <p>
     * Этот обработчик вызывается, когда аутентифицированный пользователь
     * пытается получить доступ к ресурсу, на который у него нет прав (полномочий).
     * Он отправляет ответ с HTTP-статусом 403 (Forbidden) и сообщением об ошибке.
     * </p>
     *
     * @return Реализация {@link AccessDeniedHandler}. Гарантированно не {@code null}.
     */
    @Bean
    @NonNull
    public AccessDeniedHandler customAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            String username = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous";
            log.warn("Access Denied for user '{}' attempting to access {}: {}", username, request.getRequestURI(),
                    accessDeniedException.getMessage()
            );
            writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden: You do not have permission to access this resource");
        };
    }

    /**
     * Вспомогательный метод для записи стандартизированного текстового ответа об ошибке.
     * Устанавливает HTTP-статус, тип контента (text/plain;charset=UTF-8) и записывает сообщение в тело ответа.
     *
     * @param response HTTP-ответ {@link HttpServletResponse}, в который будет записано сообщение. Не может быть {@code null}.
     * @param status   HTTP-статус для ответа (например, 401 или 403).
     * @param message  Сообщение об ошибке для записи в тело ответа. Не может быть {@code null}.
     * @throws IOException если возникает ошибка при получении {@link PrintWriter} или записи в него.
     */
    private void writeErrorResponse(@NonNull HttpServletResponse response, int status, @NonNull String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.TEXT_PLAIN_VALUE); 
        response.setCharacterEncoding(StandardCharsets.UTF_8.name()); 
        try (PrintWriter writer = response.getWriter()) {
            writer.write(message); 
            writer.flush(); 
        }
    }

    /**
     * Определяет бин конфигуратора CORS для всего приложения.
     * <p>
     * Создает {@link WebMvcConfigurer}, который настраивает правила CORS,
     * используя список разрешенных источников (origins), полученный из свойства
     * приложения {@code cors.allowed-origins}.
     * </p>
     *
     * @param allowedOrigins Список разрешенных источников (origins), внедренный из конфигурации. Не может быть {@code null}.
     * @return Конфигуратор {@link WebMvcConfigurer} для настройки CORS. Гарантированно не {@code null}.
     */
    @Bean
    @NonNull
    public WebMvcConfigurer corsConfigurer(
            @Value("${cors.allowed-origins}") @NonNull List<String> allowedOrigins 
    ) {
        if (allowedOrigins.isEmpty()) {
            log.warn("CORS Allowed Origins property ('cors.allowed-origins') is missing or empty! CORS might not function as expected. This can be a security risk in production.");
        } else {
            log.info("Configuring CORS. Allowed Origins: {}", allowedOrigins);
        }
        return new CustomCorsConfigurer(allowedOrigins);
    }

    /**
     * Внутренний record, реализующий {@link WebMvcConfigurer} для применения настроек CORS.
     * Использует переданный список разрешенных источников.
     *
     * @param allowedOrigins Список разрешенных источников (origins). Не может быть {@code null}.
     */
    private record CustomCorsConfigurer(@NonNull List<String> allowedOrigins) implements WebMvcConfigurer {
        /**
         * Массив разрешенных HTTP-методов для CORS.
         */
        private static final String[] ALLOWED_METHODS = {"GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"};

        /**
         * Добавляет глобальные правила CORS для всех путей ({@code /**}).
         * Настраивает разрешенные источники, методы, заголовки и разрешение передачи учетных данных (credentials).
         *
         * @param registry Реестр {@link CorsRegistry} для добавления правил CORS. Не может быть {@code null}.
         */
        @Override
        public void addCorsMappings(@NonNull CorsRegistry registry) {
            log.debug("Applying CORS configuration via WebMvcConfigurer:");
            log.debug("  Mapping: /**");
            log.debug("  Allowed Origins: {}", allowedOrigins);
            log.debug("  Allowed Methods: {}", Arrays.toString(ALLOWED_METHODS));
            log.debug("  Allowed Headers: *");
            log.debug("  Allow Credentials: true");
            registry.addMapping("/**") 
                    .allowedOrigins(allowedOrigins.toArray(String[]::new)) 
                    .allowedMethods(ALLOWED_METHODS) 
                    .allowedHeaders("*") 
                    .allowCredentials(true); 
        }
    }
}
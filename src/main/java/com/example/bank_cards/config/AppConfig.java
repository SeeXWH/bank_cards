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

/**
 * Основной класс конфигурации для приложения.
 * <p>
 * Этот класс определяет и настраивает ключевые бины Spring,
 * необходимые для функционирования приложения, включая компоненты безопасности,
 * кодировщик паролей и стандартные HTTP заголовки.
 * </p>
 */
@Configuration
@Slf4j
public class AppConfig {
    /**
     * Константа, определяющая силу для алгоритма хеширования BCrypt.
     * Допустимый диапазон: 4-31.
     */
    private static final int BCRYPT_STRENGTH = 8;

    /**
     * Метод инициализации, выполняемый после создания бина {@code AppConfig}.
     * Логирует завершение инициализации конфигурации и используемую силу BCrypt.
     */
    @PostConstruct
    public void initialize() {
        log.info("AppConfig initialization complete. BCrypt strength configured to: {}", BCRYPT_STRENGTH);
    }

    /**
     * Определяет бин {@link HttpHeaders}.
     * <p>
     * Предоставляет экземпляр {@code HttpHeaders} по умолчанию, который может быть
     * внедрен в другие компоненты приложения при необходимости.
     * </p>
     *
     * @return Новый экземпляр {@link HttpHeaders}. Гарантированно не {@code null}.
     */
    @Bean
    @NonNull
    public HttpHeaders httpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        log.debug("Default HttpHeaders bean created successfully.");
        return headers;
    }

    /**
     * Определяет бин {@link AuthenticationManager}.
     * <p>
     * Получает {@code AuthenticationManager} из предоставленной {@link AuthenticationConfiguration}.
     * Этот менеджер является ключевым компонентом Spring Security для обработки аутентификации.
     * </p>
     *
     * @param configuration Конфигурация аутентификации Spring Security. Не может быть {@code null}.
     * @return Бин {@link AuthenticationManager}. Гарантированно не {@code null}.
     * @throws ResponseStatusException если не удается получить {@code AuthenticationManager} из конфигурации,
     *                             что указывает на проблемы с настройкой Spring Security. Генерируется исключение
     *                             с HTTP статусом {@code 500 Internal Server Error}.
     */
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

    /**
     * Определяет бин {@link PasswordEncoder} для хеширования паролей.
     * <p>
     * Использует {@link BCryptPasswordEncoder} с силой, заданной константой {@link #BCRYPT_STRENGTH}.
     * Выполняет проверку допустимости значения силы BCrypt перед созданием бина.
     * </p>
     *
     * @return Бин {@link PasswordEncoder} (реализация BCrypt). Гарантированно не {@code null}.
     * @throws ResponseStatusException если значение {@link #BCRYPT_STRENGTH} находится вне допустимого диапазона (4-31).
     *                             Генерируется исключение с HTTP статусом {@code 500 Internal Server Error}.
     */
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
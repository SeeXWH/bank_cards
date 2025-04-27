package com.example.bank_cards.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Компонент Spring, отвечающий за генерацию, парсинг и валидацию
 * JSON Web Tokens (JWT).
 * <p>
 * Использует секретный ключ и время жизни токена, настраиваемые
 * через свойства приложения {@code jwt.secret} и {@code jwt.expiration}.
 * </p>
 */
@Component
@Slf4j
public class JwtTokenProvider {

    /**
     * Секретная строка, используемая для генерации ключа подписи JWT.
     * Загружается из свойства приложения {@code jwt.secret}.
     * Это значение должно храниться в секрете и быть достаточно сложным.
     */
    @Value("${jwt.secret}")
    private String jwtSecretString;

    /**
     * Время жизни JWT в миллисекундах.
     * Загружается из свойства приложения {@code jwt.expiration}.
     */
    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    /**
     * Секретный ключ {@link SecretKey}, сгенерированный из {@link #jwtSecretString}.
     * Используется для подписи и верификации JWT с использованием алгоритма HMAC-SHA512.
     * Этот ключ является критически важным для безопасности системы.
     */
    private SecretKey jwtSecretKey;

    /**
     * Метод инициализации, выполняемый после внедрения зависимостей ({@code @PostConstruct}).
     * <p>
     * Проверяет наличие и непустоту секретной строки {@link #jwtSecretString}.
     * Генерирует {@link SecretKey} из {@code jwtSecretString} с использованием кодировки UTF-8.
     * Проверяет минимальную рекомендуемую длину ключа для алгоритма HS512 (64 байта) и выводит предупреждение, если ключ короче.
     * Логирует успешную инициализацию и установленное время жизни токена.
     * </p>
     *
     * @throws IllegalStateException если секретный ключ ({@code jwt.secret}) не сконфигурирован или пуст.
     */
    @PostConstruct
    protected void init() {
        if (!StringUtils.hasText(jwtSecretString)) {
            log.error("CRITICAL: JWT secret key ('jwt.secret') is not configured or is empty. Application cannot securely issue tokens.");
            throw new IllegalStateException("JWT secret key ('jwt.secret') is missing or empty in configuration.");
        }
        byte[] keyBytes = jwtSecretString.getBytes(StandardCharsets.UTF_8);
        final int minKeyLengthBytes = 64;
        if (keyBytes.length < minKeyLengthBytes) {
            log.warn("SECURITY WARNING: Configured JWT secret key ('jwt.secret') is shorter than the recommended {} bytes ({}} bits) for HS512. " +
                            "Key length: {} bytes. Consider using a longer, securely generated key for production environments.",
                    minKeyLengthBytes, minKeyLengthBytes * 8, keyBytes.length);
        }
        this.jwtSecretKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JwtTokenProvider initialized successfully. JWT Expiration time set to {} ms.", jwtExpirationMs);
    }


    /**
     * Генерирует JWT для указанного email пользователя.
     * <p>
     * Устанавливает email в качестве субъекта (subject) токена.
     * Устанавливает время выпуска (issued at) и время истечения срока действия (expiration) токена,
     * используя текущее время и настроенное {@link #jwtExpirationMs}.
     * Подписывает токен алгоритмом HS512 с использованием {@link #jwtSecretKey}.
     * </p>
     *
     * @param email Email пользователя, для которого генерируется токен. Не может быть {@code null} или пустой строкой.
     * @return Сгенерированный JWT в виде компактной строки. Гарантированно не {@code null}.
     * @throws ResponseStatusException если {@code email} пуст или равен {@code null} (статус 400 Bad Request).
     * @throws io.jsonwebtoken.JwtException если возникает внутренняя ошибка при сборке токена.
     */
    @NonNull
    public String generateToken(@NonNull String email) {
        if (!StringUtils.hasText(email)) {
            log.error("Attempted to generate JWT for a blank email address.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email cannot be blank for token generation.");
        }
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);
        log.debug("Generating JWT for email '{}' with expiration date: {}", email, expiryDate);
        try {
            return Jwts.builder()
                    .setSubject(email) 
                    .setIssuedAt(now) 
                    .setExpiration(expiryDate) 
                    .signWith(jwtSecretKey, SignatureAlgorithm.HS512) 
                    .compact(); 
        } catch (JwtException e) {
            log.error("Failed to generate JWT for email {}: {}", email, e.getMessage(), e);
            
            throw new RuntimeException("Could not generate JWT token", e);
        }
    }


    /**
     * Извлекает email (субъект) из предоставленного JWT.
     * <p>
     * Проверяет, что строка токена не пуста.
     * Парсит токен, используя {@link #jwtSecretKey} для проверки подписи.
     * Извлекает поле 'subject' (которое содержит email) из тела токена (claims).
     * Проверяет, что извлеченный email не пуст.
     * </p>
     *
     * @param token JWT в виде строки, из которого извлекается email. Не может быть {@code null}.
     * @return Email пользователя, извлеченный из токена. Гарантированно не {@code null} и не пустая строка.
     * @throws ResponseStatusException если {@code token} пуст или равен {@code null} (статус 401 Unauthorized).
     * @throws ResponseStatusException если токен невалиден (неверная подпись, структура, срок действия истек, отсутствует субъект)
     *                                 или произошла другая ошибка парсинга (статус 401 Unauthorized). Включает исходное исключение {@link JwtException}.
     */
    @NonNull
    public String getEmailFromToken(@NonNull String token) {
        if (!StringUtils.hasText(token)) {
            log.error("Attempted to extract email from a blank or null token string.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cannot extract claims from a blank or null token.");
        }
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(jwtSecretKey) 
                    .build()
                    .parseClaimsJws(token) 
                    .getBody();
            String email = claims.getSubject();
            if (!StringUtils.hasText(email)) {
                log.warn("Parsed JWT successfully, but the subject claim (email) is missing or empty.");
                throw new MalformedJwtException("JWT subject claim (email) is missing or empty.");
            }
            log.trace("Successfully extracted email '{}' from token.", email);
            return email;
        } catch (ExpiredJwtException ex) {
            log.warn("Failed to parse JWT and extract email: Token has expired. Message: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expired: " + ex.getMessage(), ex);
        } catch (JwtException | IllegalArgumentException ex) {
            log.error("Failed to parse JWT and extract email due to invalid token: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token: " + ex.getMessage(), ex);
        }
    }

    /**
     * Проверяет валидность предоставленного JWT.
     * <p>
     * Валидация включает проверку подписи с использованием {@link #jwtSecretKey},
     * проверку срока действия и корректности структуры токена.
     * </p>
     *
     * @param token JWT в виде строки для валидации. Может быть {@code null}.
     * @return {@code true}, если токен валиден (не {@code null}, не пустой, корректная подпись,
     *         не истек срок действия, поддерживаемый формат), иначе {@code false}.
     *         Логирует причину невалидности на уровне WARN или ERROR.
     */
    public boolean validateToken(@Nullable String token) {
        if (!StringUtils.hasText(token)) {
            log.debug("JWT validation failed: Token string provided was null or empty.");
            return false;
        }
        try {
            Jwts.parserBuilder()
                    .setSigningKey(jwtSecretKey) 
                    .build()
                    .parseClaimsJws(token); 
            log.trace("JWT validation successful for token."); 
            return true;
        } catch (SecurityException ex) {
            log.warn("JWT validation failed: Invalid JWT signature. Message: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.warn("JWT validation failed: Invalid JWT token structure (malformed). Message: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.warn("JWT validation failed: Token has expired. Message: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.warn("JWT validation failed: Unsupported JWT token type/format. Message: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn(
                    "JWT validation failed: JWT claims string was empty or invalid argument provided to parser. Message: {}",
                    ex.getMessage()
            );
        } catch (JwtException ex) {
            log.error("JWT validation failed due to an unexpected JWT exception. Message: {}", ex.getMessage(), ex);
        }
        return false;
    }
}
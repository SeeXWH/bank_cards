package com.example.bank_cards.security;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.SpringSecurityCoreVersion;

import java.io.Serial;
import java.util.Collection;

/**
 * Представляет токен аутентификации для JWT (JSON Web Token) в контексте Spring Security.
 * <p>
 * Этот класс расширяет {@link AbstractAuthenticationToken} и используется для хранения
 * информации об аутентифицированном пользователе (principal), самого JWT (credentials),
 * и предоставленных ему прав (authorities) после успешной валидации токена.
 * </p>
 * <p>
 * Экземпляры этого класса создаются, как правило, в {@link JwtAuthenticationFilter}
 * и помещаются в {@link org.springframework.security.core.context.SecurityContextHolder}.
 * </p>
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = SpringSecurityCoreVersion.SERIAL_VERSION_UID;

    private final Object principal;

    private Object credentials;

    public JwtAuthenticationToken(
            @NonNull Object principal, @Nullable Object credentials,
            @NonNull Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
        super.setAuthenticated(true); 
    }

    /**
     * Возвращает учетные данные, использованные для аутентификации.
     * В контексте JWT, это обычно сам токен.
     *
     * @return JWT (строка) или {@code null}, если учетные данные были удалены.
     */
    @Override
    @Nullable
    public Object getCredentials() {
        return this.credentials;
    }


    /**
     * Возвращает основной объект (principal), представляющий аутентифицированного пользователя.
     *
     * @return Объект principal (например, {@code UserDetails}). Гарантированно не {@code null}.
     */
    @Override
    @NonNull
    public Object getPrincipal() {
        return this.principal;
    }


    /**
     * Устанавливает статус аутентификации токена.
     * <p>
     * Переопределен для предотвращения установки токена в состояние "аутентифицирован"
     * после его создания через конструктор без прав. Токен считается аутентифицированным
     * только если он был создан с использованием конструктора, принимающего {@code authorities}.
     * Попытка установить {@code true} после создания вызовет исключение.
     * Установка {@code false} разрешена.
     * </p>
     *
     * @param isAuthenticated {@code true}, если токен аутентифицирован, {@code false} иначе.
     * @throws IllegalArgumentException если происходит попытка установить {@code true}
     *                                  для токена, который не был изначально создан как аутентифицированный.
     */
    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        if (isAuthenticated && !super.isAuthenticated()) {
            throw new IllegalArgumentException(
                    "Cannot set this token to trusted - use constructor which takes a GrantedAuthority list instead");
        }
        super.setAuthenticated(false);
    }

    /**
     * Стирает учетные данные (JWT) из токена.
     * <p>
     * Вызывается для удаления чувствительной информации после завершения процесса аутентификации.
     * Устанавливает поле {@code credentials} в {@code null}.
     * </p>
     */
    @Override
    public void eraseCredentials() {
        super.eraseCredentials(); 
        this.credentials = null; 
    }
}
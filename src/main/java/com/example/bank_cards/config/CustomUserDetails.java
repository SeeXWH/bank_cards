package com.example.bank_cards.config;

import com.example.bank_cards.enums.Role;
import com.example.bank_cards.model.AppUser;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Collections;

/**
 * Реализация интерфейса {@link UserDetails} для интеграции пользовательских данных
 * {@link AppUser} со Spring Security.
 * <p>
 * Этот record предоставляет Spring Security необходимую информацию о пользователе,
 * такую как имя пользователя (email), пароль, роли (authorities) и статус аккаунта.
 * </p>
 *
 * @param appUser Объект {@link AppUser}, содержащий данные пользователя. Не может быть {@code null}.
 */
@Slf4j
public record CustomUserDetails(
        @NotNull
        @NonNull
        AppUser appUser
) implements UserDetails {

    /**
     * Префикс, добавляемый к имени роли при создании объекта {@link GrantedAuthority}.
     * Стандартное соглашение Spring Security.
     */
    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Конструктор для создания экземпляра {@code CustomUserDetails}.
     *
     * @param appUser Объект {@link AppUser}, представляющий пользователя. Не может быть {@code null}.
     */
    public CustomUserDetails(@NonNull AppUser appUser) {
        this.appUser = appUser;
    }

    /**
     * Возвращает коллекцию прав (ролей), предоставленных пользователю.
     * <p>
     * Преобразует роль пользователя из {@link AppUser#getRole()} в {@link SimpleGrantedAuthority},
     * добавляя префикс {@link #ROLE_PREFIX}.
     * </p>
     *
     * @return Коллекция, содержащая одно право (роль) пользователя. Гарантированно не {@code null}.
     */
    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Role userRole = appUser.getRole();
        String roleName = ROLE_PREFIX + userRole.name();
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(roleName);
        log.trace("Authorities for user {}: {}", appUser.getEmail(), authority);
        return Collections.singletonList(authority);
    }

    /**
     * Возвращает хешированный пароль пользователя.
     *
     * @return Пароль пользователя из объекта {@link AppUser}.
     */
    @Override
    public String getPassword() {
        return appUser.getPassword();
    }

    /**
     * Возвращает имя пользователя, используемое для аутентификации.
     * <p>
     * В данной реализации в качестве имени пользователя используется email из {@link AppUser#getEmail()}.
     * Проверяет, что email не пустой.
     * </p>
     *
     * @return Email пользователя. Гарантированно не {@code null} и не пустая строка.
     * @throws ResponseStatusException если email пользователя отсутствует или пуст,
     *                                 указывая на нарушение целостности данных. Генерируется исключение
     *                                 с HTTP статусом {@code 500 Internal Server Error}.
     */
    @Override
    @NonNull
    public String getUsername() {
        String username = appUser.getEmail();
        if (username.trim().isEmpty()) {
            log.error("CRITICAL: AppUser object (ID potentially unknown/unlovable here) has a null or empty number "
                    + "(username).");
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "User data integrity error: Phone number is missing."
            );
        }
        return username;
    }

    /**
     * Указывает, не истек ли срок действия учетной записи пользователя.
     *
     * @return {@code true}, если учетная запись действительна (не просрочена), иначе {@code false}.
     * В данной реализации всегда возвращает {@code true}.
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Указывает, не заблокирована ли учетная запись пользователя.
     *
     * @return {@code true}, если учетная запись не заблокирована, {@code false} в противном случае.
     * Значение определяется полем {@link AppUser#isLocked()}.
     */
    @Override
    public boolean isAccountNonLocked() {
        return !appUser.isLocked();
    }

    /**
     * Указывает, не истек ли срок действия учетных данных пользователя (пароля).
     *
     * @return {@code true}, если учетные данные действительны (не просрочены), иначе {@code false}.
     * В данной реализации всегда возвращает {@code true}.
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Указывает, включена ли учетная запись пользователя.
     *
     * @return {@code true}, если учетная запись включена, иначе {@code false}.
     * В данной реализации всегда возвращает {@code true}.
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

}
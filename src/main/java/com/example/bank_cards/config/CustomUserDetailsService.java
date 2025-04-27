package com.example.bank_cards.config;

import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.repository.UserRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Сервис для загрузки пользовательских данных для Spring Security.
 * <p>
 * Реализует интерфейс {@link UserDetailsService}, который используется Spring Security
 * для получения информации о пользователе по его имени пользователя (в данном случае, email)
 * в процессе аутентификации.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    /**
     * Сообщение об ошибке для случая, когда переданный email является null или пустым.
     */
    private static final String NULL_EMPTY_PHONE_ERROR = "Идентификатор (email) не может быть null или пустым.";
    /**
     * Шаблон сообщения об ошибке, когда пользователь с указанным email не найден.
     */
    private static final String USER_NOT_FOUND_TEMPLATE = "Пользователь с email '%s' не найден в системе.";
    /**
     * Шаблон сообщения об ошибке при неудачной загрузке данных пользователя.
     */
    private static final String FAILED_TO_LOAD_USER_TEMPLATE = "Не удалось загрузить данные пользователя для email '%s'";
    /**
     * Репозиторий для доступа к данным пользователей {@link AppUser}.
     */
    private final UserRepository userRepository;


    /**
     * Загружает данные пользователя по его имени пользователя (email).
     * <p>
     * Этот метод вызывается Spring Security во время процесса аутентификации.
     * Он ищет пользователя в базе данных по предоставленному email.
     * Если пользователь найден, возвращает объект {@link CustomUserDetails},
     * содержащий информацию о пользователе. Если пользователь не найден или
     * происходит ошибка, выбрасывает {@link ResponseStatusException}.
     * </p>
     * <p>
     * Метод выполняется в рамках транзакции только для чтения.
     * </p>
     *
     * @param email Имя пользователя (email), по которому осуществляется поиск.
     *              Не может быть {@code null} или пустым (проверяется аннотацией {@code @NotBlank}).
     * @return Объект {@link UserDetails} (в виде {@link CustomUserDetails}),
     *         содержащий данные найденного пользователя. Гарантированно не {@code null}.
     * @throws ResponseStatusException если пользователь с указанным email не найден
     *                                 (HTTP статус {@code 404 Not Found}) или если при загрузке
     *                                 произошла непредвиденная ошибка (HTTP статус {@code 404 Not Found}
     *                                 или {@code 500 Internal Server Error} в зависимости от причины).
     */
    @Override
    @NonNull
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(
            @NotBlank(message = NULL_EMPTY_PHONE_ERROR)
            @NonNull
            String email
    ) throws ResponseStatusException {
        try {
            AppUser appUser = userRepository.findByEmail(email)
                    .orElseThrow(() -> {
                        String errorMessage = String.format(
                                USER_NOT_FOUND_TEMPLATE,
                                email
                        );
                        log.warn(errorMessage);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND, errorMessage);
                    });
            log.debug("User details found for email: {}", email);
            return new CustomUserDetails(appUser);
        }
        catch (ResponseStatusException ex) {
            String failureReason = String.format(FAILED_TO_LOAD_USER_TEMPLATE, email);
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("{} - Reason: {}", failureReason, ex.getReason());
            } else {
                log.error("{} - Reason: {} (HTTP Status: {})", failureReason, ex.getReason(), ex.getStatusCode());
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getReason() != null ? ex.getReason() : failureReason, ex);
        }
        catch (Exception e) {
            String errorMessage = String.format("Unexpected error occurred while loading user details for email '%s'", email);
            log.error(errorMessage, e);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "An internal error occurred during authentication.", e);
        }
    }
}
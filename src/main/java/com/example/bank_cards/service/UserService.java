package com.example.bank_cards.service;

import com.example.bank_cards.dto.LoginDto;
import com.example.bank_cards.dto.RegistrationDto;
import com.example.bank_cards.enums.Role;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.repository.UserRepository;
import com.example.bank_cards.security.JwtTokenProvider;
import com.example.bank_cards.serviceInterface.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException; 
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;


/**
 * Сервис для управления пользователями (AppUser).
 * Предоставляет функциональность для регистрации, аутентификации, поиска пользователей,
 * изменения ролей и управления блокировкой учетных записей.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class UserService implements UserServiceImpl {

    /**
     * Минимальная допустимая длина пароля.
     */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    /**
     * Регистрирует нового пользователя в системе.
     * Проверяет валидность пароля и уникальность email.
     * Сохраняет пользователя с хэшированным паролем и ролью по умолчанию (обычно USER).
     * После успешной регистрации автоматически аутентифицирует пользователя и генерирует JWT токен.
     *
     * @param user DTO с данными для регистрации (имя, email, пароль).
     * @return Сгенерированный JWT токен для зарегистрированного пользователя.
     * @throws ResponseStatusException Если пароль не соответствует требованиям (BAD_REQUEST).
     * @throws ResponseStatusException Если пользователь с таким email уже существует (CONFLICT).
     * @throws BadCredentialsException Если автоматическая аутентификация после регистрации не удалась (редкий случай).
     */
    @Override
    @Transactional
    public String registerUser(RegistrationDto user) { 
        log.info("Attempting to register user with email: {}", user.getEmail());
        isPasswordValid(user.getPassword()); 
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            log.warn("Registration failed: User with email {} already exists.", user.getEmail());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User with this email already exists"); 
        }
        AppUser appUser = new AppUser();
        appUser.setName(user.getName());
        appUser.setEmail(user.getEmail());
        appUser.setPassword(passwordEncoder.encode(user.getPassword().trim()));
        userRepository.save(appUser);
        log.info("User registered successfully with ID: {} and email: {}", appUser.getId(), appUser.getEmail());
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String token = jwtTokenProvider.generateToken(user.getEmail());
            log.debug("JWT token generated successfully for newly registered user with email: {}", user.getEmail());
            return token;
        } catch (BadCredentialsException e) {
            log.error("Authentication failed immediately after registration for email: {}. This should not happen.", user.getEmail(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Authentication failed after registration"); 
        }
    }

    /**
     * Аутентифицирует пользователя по email и паролю.
     * Использует Spring Security AuthenticationManager для проверки учетных данных.
     * В случае успеха генерирует и возвращает JWT токен.
     *
     * @param user DTO с данными для входа (email, пароль).
     * @return Сгенерированный JWT токен при успешной аутентификации.
     * @throws ResponseStatusException Если пароль не указан или не соответствует требованиям (BAD_REQUEST).
     * @throws BadCredentialsException Если предоставлены неверные учетные данные (обрабатывается AuthenticationManager).
     */
    @Override
    public String authenticateUser(LoginDto user) { 
        log.info("Attempting to authenticate user with email: {}", user.getEmail());
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            log.warn("Authentication failed: Password is required for email: {}", user.getEmail());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required"); 
        }
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.info("Authentication successful for email: {}", user.getEmail());
        String token = jwtTokenProvider.generateToken(user.getEmail());
        log.debug("JWT token generated successfully for email: {}", user.getEmail());
        return token;
    }

    /**
     * Находит и возвращает пользователя по его email.
     * Используется для внутренних нужд сервиса и контроллеров.
     *
     * @param email Email искомого пользователя.
     * @return Объект {@link AppUser}.
     * @throws ResponseStatusException Если пользователь с указанным email не найден (NOT_FOUND).
     */
    @Override
    @Transactional(readOnly = true)
    public AppUser getUserByEmail(String email) {
        log.debug("Attempting to retrieve user by email: {}", email);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("User not found with email: {}", email);
                    return new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "User not found with email: " + email 
                    );
                });
    }

    /**
     * Изменяет роль указанного пользователя.
     *
     * @param email Email пользователя, роль которого нужно изменить.
     * @param role  Новая роль для пользователя.
     * @throws ResponseStatusException Если пользователь с указанным email не найден (NOT_FOUND).
     */
    @Override
    @Transactional
    public void changeRoleUser(String email, Role role) {
        log.info("Attempting to change role for user with email: {} to role: {}", email, role);
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Change role failed: User not found with email: {}", email);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with email: " + email); 
                });
        if (user.getRole() == role) {
            log.warn("User {} already has role {}. No change needed.", email, role);
            return; 
        }
        user.setRole(role);
        userRepository.save(user);
        log.info("Role changed successfully for user with email: {} to role: {}", email, role);
    }

    /**
     * Проверяет, соответствует ли пароль минимальным требованиям (длина).
     * Используется при регистрации.
     *
     * @param password Пароль для проверки.
     * @throws ResponseStatusException Если пароль null, пустой или короче минимальной длины (BAD_REQUEST).
     */
    public static void isPasswordValid(String password) {
        boolean check = password != null && password.trim().length() >= MIN_PASSWORD_LENGTH;
        if (!check){
            log.warn("Password validation failed: Password does not meet minimum length requirement ({} characters).", MIN_PASSWORD_LENGTH);
            
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least " + MIN_PASSWORD_LENGTH + " characters long"); 
        }
    }

    /**
     * Блокирует учетную запись пользователя по email.
     * Заблокированный пользователь не сможет войти в систему.
     *
     * @param email Email пользователя для блокировки.
     * @throws ResponseStatusException Если пользователь с указанным email не найден (NOT_FOUND).
     */
    @Override
    @Transactional
    public void lockUser(String email) {
        log.info("Attempting to lock user with email: {}", email);
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Lock user failed: User not found with email: {}", email);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"); 
                });
        if (user.isLocked()) {
            log.warn("User {} is already locked. No action taken.", email);
            return;
        }
        user.setLocked(true);
        userRepository.save(user);
        log.info("User locked successfully with email: {}", email);
    }

    /**
     * Разблокирует учетную запись пользователя по email.
     *
     * @param email Email пользователя для разблокировки.
     * @throws ResponseStatusException Если пользователь с указанным email не найден (NOT_FOUND).
     */
    @Override
    @Transactional
    public void unlockUser(String email) {
        log.info("Attempting to unlock user with email: {}", email);
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Unlock user failed: User not found with email: {}", email);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"); 
                });
        if (!user.isLocked()) {
            log.warn("User {} is already unlocked. No action taken.", email);
            return;
        }
        user.setLocked(false);
        userRepository.save(user);
        log.info("User unlocked successfully with email: {}", email);
    }
}
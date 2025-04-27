package com.example.bank_cards.service;

import com.example.bank_cards.dto.LoginDto;
import com.example.bank_cards.dto.RegistrationDto;
import com.example.bank_cards.enums.Role;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.repository.UserRepository;
import com.example.bank_cards.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;


@Service
@RequiredArgsConstructor
@Log4j2
public class UserService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public String registerUser(@RequestBody RegistrationDto user) {
        log.info("Attempting to register user with email: {}", user.getEmail());
        if (!StringUtils.hasText(user.getEmail()) || !StringUtils.hasText(user.getPassword()) || !StringUtils.hasText(user.getName())) {
            log.warn("Registration failed: Email, password or name is blank.");
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email, password and name cannot be null or empty"
            );
        }
        isEmailValid(user.getEmail());
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
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = jwtTokenProvider.generateToken(user.getEmail());
        log.debug("JWT token generated successfully for newly registered user with email: {}", user.getEmail());
        return token;
    }

    public String authenticateUser(@RequestBody LoginDto user) {
        log.info("Attempting to authenticate user with email: {}", user.getEmail());
        if (!StringUtils.hasText(user.getEmail()) || !StringUtils.hasText(user.getPassword())) {
            log.warn("Authentication failed: Email or password is blank.");
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email and password cannot be null or empty"
            );
        }
        isEmailValid(user.getEmail());
        isPasswordValid(user.getPassword());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.info("Authentication successful for email: {}", user.getEmail());
        String token = jwtTokenProvider.generateToken(user.getEmail());
        log.debug("JWT token generated successfully for email: {}", user.getEmail());
        return token;
    }

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

    @Transactional
    public void changeRoleUser(String email, Role role) {
        log.info("Attempting to change role for user with email: {} to role: {}", email, role);
        if (!StringUtils.hasText(email) || role == null) {
            log.warn("Change role failed: Email or role is blank.");
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email and role cannot be null or empty"
            );
        }
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Change role failed: User not found with email: {}", email);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with email: " + email);
                });
        user.setRole(role);
        userRepository.save(user);
        log.info("Role changed successfully for user with email: {} to role: {}", email, role);
    }

    public static void isPasswordValid(String password) {
        boolean check = password != null && password.trim().length() >= MIN_PASSWORD_LENGTH;
        if (!check){
            log.warn("Password validation failed: Password does not meet minimum length requirement.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid password");
        }
    }

    public static void isEmailValid(String email) {
        if (email == null || email.trim().isEmpty()) {
            log.warn("Email validation failed: Email is blank.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email");
        }
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        boolean isValid = email.matches(emailRegex);
        if (!isValid) {
            log.warn("Email validation failed: Invalid email format: {}", email);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email");
        }
    }

    @Transactional
    public void lockUser(String email) {
        log.info("Attempting to lock user with email: {}", email);
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Lock user failed: User not found with email: {}", email);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                });
        user.setLocked(true);
        userRepository.save(user);
        log.info("User locked successfully with email: {}", email);
    }

    @Transactional
    public void unlockUser(String email) {
        log.info("Attempting to unlock user with email: {}", email);
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Unlock user failed: User not found with email: {}", email);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
                });
        user.setLocked(false);
        userRepository.save(user);
        log.info("User unlocked successfully with email: {}", email);
    }

}

package com.example.bank_cards.service;

import com.example.bank_cards.dto.LoginDto;
import com.example.bank_cards.dto.RegistrationDto;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.repository.UserRepository;
import com.example.bank_cards.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
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
        if (!StringUtils.hasText(user.getEmail()) || !StringUtils.hasText(user.getPassword()) || !StringUtils.hasText(user.getName())) {
            log.warn("Authentication failed: Email or password or name is blank.");
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email and password cannot be null or empty"
            );
        }

        if (!isEmailValid(user.getEmail())) {
            log.warn("Registration failed: Invalid email.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email");
        }

        if (!isPasswordValid(user.getPassword())) {
            log.warn("Registration failed: Invalid password.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid password");
        }

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            log.warn("Registration failed: User with email {} already exists.", user.getEmail());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User with this email already exists");
        }

        try {
            AppUser appUser = new AppUser();
            appUser.setName(user.getName());
            appUser.setEmail(user.getEmail());
            appUser.setPassword(passwordEncoder.encode(user.getPassword().trim()));
            userRepository.save(appUser);
            log.info("User registered successfully with ID: {} and email: {}",
                    appUser.getId(), appUser.getEmail()
            );

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String token = jwtTokenProvider.generateToken(user.getEmail());
            log.debug("JWT token generated successfully for newly registered user with email: {}", user.getEmail());

            return token;
        } catch (BadCredentialsException e) {
            log.warn("Authentication failed: Invalid credentials for email: {}", user.getEmail());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }
        catch (AuthenticationException e) {
            log.warn("Authentication failed: General authentication error for email: {}", user.getEmail());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication failed"
            );
        }
        catch (DataAccessException e) {
            log.error("Database error during user registration for email: {}", user.getEmail());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Database error during registration."
            );
        }
        catch (Exception e) {
            log.error("Unexpected error during user registration for email: {}", user.getEmail());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred during registration."
            );
        }
    }

    public String authenticateUser(@RequestBody LoginDto user) {
        if (!StringUtils.hasText(user.getEmail()) || !StringUtils.hasText(user.getPassword())) {
            log.warn("Authentication failed: Email or password is blank.");
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email and password cannot be null or empty"
            );
        }

        if (!isEmailValid(user.getEmail())) {
            log.warn("Registration failed: Invalid email.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email");
        }

        if (!isPasswordValid(user.getPassword())) {
            log.warn("Registration failed: Invalid password.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid password");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.info("Authentication successful for email: {}", user.getEmail());
            String token = jwtTokenProvider.generateToken(user.getEmail());
            log.debug("JWT token generated successfully for email: {}", user.getEmail());
            return token;
        }
        catch (BadCredentialsException e) {
            log.warn("Authentication failed: Invalid credentials for email: {}", user.getEmail());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }
        catch (AuthenticationException e) {
            log.warn("Authentication failed: General authentication error for email: {}", user.getEmail());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication failed"
            );
        }
        catch (DataAccessException e) {
            log.error("Database error during authentication for email: {}", user.getEmail(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Database error during authentication"
            );
        }
        catch (Exception e) {
            log.error("Unexpected error during authentication for email: {}", user.getEmail(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred during authentication"
            );
        }
    }

    @Transactional(readOnly = true)
    public AppUser getUserByEmail(String email) {
        log.debug("Attempting to retrieve user by email: {}", email);
        try {
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> {
                        log.warn("User not found with email: {}", email);
                        return new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found with email: " + email
                        );
                    });
        }
        catch (ResponseStatusException e) {
            throw e;
        }
        catch (DataAccessException e) {
            log.error("Database error while retrieving user by email: {}", email);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Database error retrieving user.");
        }
        catch (Exception e) {
            log.error("Unexpected error retrieving user by email: {}", email);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error retrieving user.");
        }
    }


    public static boolean isPasswordValid(String password) {
        boolean isValid = password != null && password.trim().length() >= MIN_PASSWORD_LENGTH;
        log.trace("Password validation result for length >= {}: {}", MIN_PASSWORD_LENGTH, isValid);
        return isValid;
    }

    public static boolean isEmailValid(String email) {
        if (email == null || email.trim().isEmpty()) {
            log.trace("Email validation failed: email is null or empty");
            return false;
        }
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        boolean isValid = email.matches(emailRegex);

        log.trace("Email validation result: {}", isValid);
        return isValid;
    }

}

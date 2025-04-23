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

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {


    private static final String NULL_EMPTY_PHONE_ERROR = "Phone number identifier cannot be null or blank.";
    private static final String USER_NOT_FOUND_TEMPLATE = "User with phone number '%s' not found in the system.";
    private static final String FAILED_TO_LOAD_USER_TEMPLATE = "Failed to load user details for phone number '%s'";
    private final UserRepository userRepository;


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

            return new CustomUserDetails(appUser);

        }
        catch (ResponseStatusException ex) {
            String failureReason = String.format(FAILED_TO_LOAD_USER_TEMPLATE, email);
            log.warn("{} - Reason: {} (HTTP Status: {})", failureReason, ex.getReason(), ex.getStatusCode());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getReason());

        }
        catch (Exception e) {
            String errorMessage = String.format("Unexpected error occurred while loading user details for phone "
                    + "number '%s'", email);
            log.error(errorMessage);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, errorMessage);
        }
    }
}

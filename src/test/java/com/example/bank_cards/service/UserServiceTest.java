package com.example.bank_cards.service;

import com.example.bank_cards.dto.LoginDto;
import com.example.bank_cards.dto.RegistrationDto;
import com.example.bank_cards.enums.Role;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.repository.UserRepository;
import com.example.bank_cards.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;


import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) 
class UserServiceTest {

    
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private Authentication authentication; 

    
    
    @InjectMocks
    private UserService userService;

    private RegistrationDto registrationDto;
    private LoginDto loginDto;
    private AppUser existingUser;
    private final String testEmail = "test@example.com";
    private final String testPassword = "password123";
    private final String testName = "Test User";
    private final String encodedPassword = "encodedPassword123";
    private final String testToken = "jwt.token.string";
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        
        registrationDto = new RegistrationDto();
        registrationDto.setEmail(testEmail);
        registrationDto.setPassword(testPassword);
        registrationDto.setName(testName);

        loginDto = new LoginDto();
        loginDto.setEmail(testEmail);
        loginDto.setPassword(testPassword);

        existingUser = new AppUser();
        existingUser.setId(userId);
        existingUser.setEmail(testEmail);
        existingUser.setPassword(encodedPassword);
        existingUser.setName(testName);
        existingUser.setRole(Role.USER);
        existingUser.setLocked(false);
    }

    

    @Test
    void registerUser_whenValidDataAndEmailNotExists_shouldRegisterAndAuthenticateAndReturnToken() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty()); 
        when(passwordEncoder.encode(testPassword.trim())).thenReturn(encodedPassword); 
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication); 
        when(jwtTokenProvider.generateToken(testEmail)).thenReturn(testToken);
        String resultToken = userService.registerUser(registrationDto);
        assertNotNull(resultToken);
        assertEquals(testToken, resultToken);
        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(userCaptor.capture()); 
        AppUser savedUser = userCaptor.getValue();
        assertEquals(testEmail, savedUser.getEmail());
        assertEquals(testName, savedUser.getName());
        assertEquals(encodedPassword, savedUser.getPassword());
        assertEquals(Role.USER, savedUser.getRole()); 
        assertFalse(savedUser.isLocked());
        verify(passwordEncoder).encode(testPassword.trim());
        verify(userRepository).findByEmail(testEmail);
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtTokenProvider).generateToken(testEmail);
    }

    @Test
    void registerUser_whenPasswordTooShort_shouldThrowBadRequestException() {
        registrationDto.setPassword("123");
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            userService.registerUser(registrationDto);
        });
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("at least 8 characters long"));
        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(AppUser.class));
        verify(authenticationManager, never()).authenticate(any());
        verify(jwtTokenProvider, never()).generateToken(anyString());
    }

    @Test
    void registerUser_whenEmailAlreadyExists_shouldThrowConflictException() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(existingUser)); // Email уже занят
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            userService.registerUser(registrationDto);
        });
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("User with this email already exists", exception.getReason());
        verify(userRepository).findByEmail(testEmail);
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(AppUser.class));
        verify(authenticationManager, never()).authenticate(any());
        verify(jwtTokenProvider, never()).generateToken(anyString());
    }

    @Test
    void registerUser_whenAuthenticationFailsAfterSave_shouldThrowInternalServerError() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(testPassword.trim())).thenReturn(encodedPassword);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials after registration"));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            userService.registerUser(registrationDto);
        });
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
        assertEquals("Authentication failed after registration", exception.getReason());
        verify(userRepository).save(any(AppUser.class));
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtTokenProvider, never()).generateToken(anyString());
    }


    

    @Test
    void authenticateUser_whenValidCredentials_shouldAuthenticateAndReturnToken() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtTokenProvider.generateToken(testEmail)).thenReturn(testToken);
        String resultToken = userService.authenticateUser(loginDto);
        assertNotNull(resultToken);
        assertEquals(testToken, resultToken);
        verify(authenticationManager).authenticate(argThat(token ->
                token.getName().equals(testEmail) && token.getCredentials().equals(testPassword)
        ));
        verify(jwtTokenProvider).generateToken(testEmail);
    }

    @Test
    
    void authenticateUser_whenPasswordIsNull_shouldThrowBadRequestException() {
        loginDto.setPassword(null);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            userService.authenticateUser(loginDto);
        });
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Password is required", exception.getReason());
        verify(authenticationManager, never()).authenticate(any());
        verify(jwtTokenProvider, never()).generateToken(anyString());
    }

    @Test
    void authenticateUser_whenPasswordIsBlank_shouldThrowBadRequestException() {
        loginDto.setPassword("   ");
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            userService.authenticateUser(loginDto);
        });
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Password is required", exception.getReason());
        verify(authenticationManager, never()).authenticate(any());
        verify(jwtTokenProvider, never()).generateToken(anyString());
    }

    @Test
    void authenticateUser_whenInvalidCredentials_shouldThrowBadCredentialsException() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));
        BadCredentialsException exception = assertThrows(BadCredentialsException.class, () -> {
            userService.authenticateUser(loginDto);
        });
        assertEquals("Invalid credentials", exception.getMessage());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtTokenProvider, never()).generateToken(anyString()); 
    }

    

    @Test
    void getUserByEmail_whenUserExists_shouldReturnUser() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(existingUser));
        AppUser foundUser = userService.getUserByEmail(testEmail);
        assertNotNull(foundUser);
        assertEquals(existingUser.getId(), foundUser.getId());
        assertEquals(testEmail, foundUser.getEmail());
        verify(userRepository).findByEmail(testEmail);
    }

    @Test
    void getUserByEmail_whenUserNotFound_shouldThrowNotFoundException() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            userService.getUserByEmail(testEmail);
        });
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertTrue(exception.getReason().contains("User not found with email: " + testEmail));
        verify(userRepository).findByEmail(testEmail);
    }

    

    @Test
    void changeRoleUser_whenUserExistsAndRoleDifferent_shouldChangeRoleAndSave() {
        assertEquals(Role.USER, existingUser.getRole()); 
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(existingUser));
        Role newRole = Role.ADMIN;
        userService.changeRoleUser(testEmail, newRole);
        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(userCaptor.capture());
        AppUser savedUser = userCaptor.getValue();
        assertEquals(newRole, savedUser.getRole()); 
        verify(userRepository).findByEmail(testEmail);
    }

    @Test
    void changeRoleUser_whenUserExistsAndRoleSame_shouldDoNothing() {
        Role currentRole = Role.ADMIN;
        existingUser.setRole(currentRole); 
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(existingUser));
        userService.changeRoleUser(testEmail, currentRole);
        verify(userRepository).findByEmail(testEmail);
        verify(userRepository, never()).save(any(AppUser.class)); 
    }


    @Test
    void changeRoleUser_whenUserNotFound_shouldThrowNotFoundException() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());
        Role newRole = Role.ADMIN;
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            userService.changeRoleUser(testEmail, newRole);
        });
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertTrue(exception.getReason().contains("User not found with email: " + testEmail));
        verify(userRepository).findByEmail(testEmail);
        verify(userRepository, never()).save(any(AppUser.class)); 
    }

    
    
    @Test
    void isPasswordValid_whenPasswordIsValid_shouldNotThrowException() {
        String validPassword = "validPassword123";
        assertDoesNotThrow(() -> UserService.isPasswordValid(validPassword));
    }

    @Test
    void isPasswordValid_whenPasswordIsTooShort_shouldThrowBadRequestException() {
        String shortPassword = "short";
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            UserService.isPasswordValid(shortPassword);
        });
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("at least 8 characters long"));
    }

    @Test
    void isPasswordValid_whenPasswordIsNull_shouldThrowBadRequestException() {
        String nullPassword = null;
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            UserService.isPasswordValid(nullPassword);
        });
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("at least 8 characters long"));
    }

    @Test
    
    void isPasswordValid_whenPasswordIsBlank_shouldThrowBadRequestException() {
        String blankPassword = "       ";
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            UserService.isPasswordValid(blankPassword);
        });
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("at least 8 characters long"));
    }


    

    @Test
    void lockUser_whenUserExistsAndNotLocked_shouldLockAndSave() {
        assertFalse(existingUser.isLocked()); 
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(existingUser));
        userService.lockUser(testEmail);
        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(userCaptor.capture());
        AppUser savedUser = userCaptor.getValue();
        assertTrue(savedUser.isLocked()); 
        verify(userRepository).findByEmail(testEmail);
    }

    @Test
    void lockUser_whenUserExistsAndAlreadyLocked_shouldDoNothing() {
        existingUser.setLocked(true); 
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(existingUser));
        userService.lockUser(testEmail);
        verify(userRepository).findByEmail(testEmail);
        verify(userRepository, never()).save(any(AppUser.class)); 
    }

    @Test
    void lockUser_whenUserNotFound_shouldThrowNotFoundException() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            userService.lockUser(testEmail);
        });
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("User not found", exception.getReason());
        verify(userRepository).findByEmail(testEmail);
        verify(userRepository, never()).save(any(AppUser.class));
    }


    

    @Test
    void unlockUser_whenUserExistsAndLocked_shouldUnlockAndSave() {
        existingUser.setLocked(true); 
        assertTrue(existingUser.isLocked());
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(existingUser));
        userService.unlockUser(testEmail);
        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(userCaptor.capture());
        AppUser savedUser = userCaptor.getValue();
        assertFalse(savedUser.isLocked()); 
        verify(userRepository).findByEmail(testEmail);
    }

    @Test
    void unlockUser_whenUserExistsAndAlreadyUnlocked_shouldDoNothing() {
        assertFalse(existingUser.isLocked()); 
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(existingUser));
        userService.unlockUser(testEmail);
        verify(userRepository).findByEmail(testEmail);
        verify(userRepository, never()).save(any(AppUser.class)); 
    }

    @Test
    void unlockUser_whenUserNotFound_shouldThrowNotFoundException() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            userService.unlockUser(testEmail);
        });
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("User not found", exception.getReason());
        verify(userRepository).findByEmail(testEmail);
        verify(userRepository, never()).save(any(AppUser.class));
    }
}
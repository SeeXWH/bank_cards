package com.example.bank_cards.controller;

import com.example.bank_cards.dto.CurrentUserDto;
import com.example.bank_cards.dto.LoginDto;
import com.example.bank_cards.dto.RegistrationDto;
import com.example.bank_cards.enums.Role;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.serviceInterface.UserServiceImpl; 
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException; 
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;


import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {

    private static final String BASE_URL = "/api/users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper; 

    @MockBean
    private UserServiceImpl userService;

    @Captor private ArgumentCaptor<RegistrationDto> registrationDtoCaptor;
    @Captor private ArgumentCaptor<LoginDto> loginDtoCaptor;
    @Captor private ArgumentCaptor<String> emailCaptor;
    @Captor private ArgumentCaptor<Role> roleCaptor;

    private RegistrationDto sampleRegistrationDto;
    private LoginDto sampleLoginDto;
    private AppUser sampleAppUser;
    private String sampleToken;
    private String sampleEmail;
    private String samplePassword;
    private String sampleName;


    @BeforeEach
    void setUp() {
        sampleEmail = "test@example.com";
        samplePassword = "password123";
        sampleName = "Test User";
        sampleToken = "sample.jwt.token";
        sampleRegistrationDto = new RegistrationDto();
        sampleRegistrationDto.setEmail(sampleEmail);
        sampleRegistrationDto.setPassword(samplePassword);
        sampleRegistrationDto.setName(sampleName);
        sampleLoginDto = new LoginDto();
        sampleLoginDto.setEmail(sampleEmail);
        sampleLoginDto.setPassword(samplePassword);
        sampleAppUser = new AppUser();
        sampleAppUser.setId(UUID.randomUUID());
        sampleAppUser.setEmail(sampleEmail);
        sampleAppUser.setName(sampleName);
        sampleAppUser.setRole(Role.USER);
        sampleAppUser.setPassword("hashedpassword"); 
        sampleAppUser.setLocked(false);
    }

    
    @Nested
    @DisplayName("POST /api/users/register Tests")
    class RegisterUserTests {

        @Test
        @DisplayName("shouldRegisterUserSuccessfullyAndReturnToken")
        void shouldRegisterUserSuccessfullyAndReturnToken() throws Exception {
            when(userService.registerUser(any(RegistrationDto.class))).thenReturn(sampleToken);
            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleRegistrationDto)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                    .andExpect(content().string(sampleToken));
            verify(userService, times(1)).registerUser(registrationDtoCaptor.capture());
            assertEquals(sampleEmail, registrationDtoCaptor.getValue().getEmail());
            assertEquals(sampleName, registrationDtoCaptor.getValue().getName());
        }

        @Test
        @DisplayName("shouldReturnConflictWhenEmailAlreadyExists")
        void shouldReturnConflictWhenEmailAlreadyExists() throws Exception {
            when(userService.registerUser(any(RegistrationDto.class)))
                    .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Conflict: User with this email already exists"));
            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleRegistrationDto)))
                    .andDo(print())
                    .andExpect(status().isConflict());
            verify(userService, times(1)).registerUser(any(RegistrationDto.class));
        }

        @Test
        @DisplayName("shouldReturnBadRequestWhenRegistrationDtoIsInvalid")
        void shouldReturnBadRequestWhenRegistrationDtoIsInvalid() throws Exception {
            RegistrationDto invalidDto = new RegistrationDto();
            invalidDto.setEmail("not-an-email"); 
            invalidDto.setPassword("short");    
            invalidDto.setName("");
            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidDto)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
            verify(userService, never()).registerUser(any());
        }

        @Test
        @DisplayName("shouldReturnBadRequestWhenRequestBodyIsNull")
        void shouldReturnBadRequestWhenRequestBodyIsNull() throws Exception {
            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)) 
                    .andDo(print())
                    .andExpect(status().isBadRequest());
            verify(userService, never()).registerUser(any());
        }
    }

    
    @Nested
    @DisplayName("POST /api/users/login Tests")
    class LoginUserTests {

        @Test
        @DisplayName("shouldLoginSuccessfullyAndReturnToken")
        void shouldLoginSuccessfullyAndReturnToken() throws Exception {
            when(userService.authenticateUser(any(LoginDto.class))).thenReturn(sampleToken);
            mockMvc.perform(post(BASE_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleLoginDto)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                    .andExpect(content().string(sampleToken));
            verify(userService, times(1)).authenticateUser(loginDtoCaptor.capture());
            assertEquals(sampleEmail, loginDtoCaptor.getValue().getEmail());
            assertEquals(samplePassword, loginDtoCaptor.getValue().getPassword());
        }

        @Test
        @DisplayName("shouldReturnUnauthorizedWhenCredentialsAreBad")
        void shouldReturnUnauthorizedWhenCredentialsAreBad() throws Exception {
            when(userService.authenticateUser(any(LoginDto.class)))
                    .thenThrow(new BadCredentialsException("Unauthorized: Bad credentials"));
            mockMvc.perform(post(BASE_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleLoginDto)))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
            verify(userService, times(1)).authenticateUser(any(LoginDto.class));
        }

        @Test
        @DisplayName("shouldReturnUnauthorizedWhenUserIsLocked")
        void shouldReturnUnauthorizedWhenUserIsLocked() throws Exception {
            when(userService.authenticateUser(any(LoginDto.class)))
                    .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account is locked"));
            mockMvc.perform(post(BASE_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleLoginDto)))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
            verify(userService, times(1)).authenticateUser(any(LoginDto.class));
        }


        @Test
        @DisplayName("shouldReturnBadRequestWhenLoginDtoIsInvalid")
        void shouldReturnBadRequestWhenLoginDtoIsInvalid() throws Exception {
            LoginDto invalidDto = new LoginDto();
            invalidDto.setEmail("test@example.com");
            invalidDto.setPassword(" ");
            mockMvc.perform(post(BASE_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidDto)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
            verify(userService, never()).authenticateUser(any());
        }

        @Test
        @DisplayName("shouldReturnBadRequestWhenLoginBodyIsNull")
        void shouldReturnBadRequestWhenLoginBodyIsNull() throws Exception {
            mockMvc.perform(post(BASE_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)) 
                    .andDo(print())
                    .andExpect(status().isBadRequest());
            verify(userService, never()).authenticateUser(any());
        }
    }

    
    @Nested
    @DisplayName("GET /api/users/me Tests")
    class GetCurrentUserTests {

        @Test
        @WithMockUser(username = "current@example.com") 
        @DisplayName("shouldReturnCurrentUserDtoWhenAuthenticated")
        void shouldReturnCurrentUserDtoWhenAuthenticated() throws Exception {
            AppUser currentUser = new AppUser();
            currentUser.setId(UUID.randomUUID());
            currentUser.setEmail("current@example.com");
            currentUser.setName("Current Test User");
            currentUser.setRole(Role.USER);
            when(userService.getUserByEmail("current@example.com")).thenReturn(currentUser);
            mockMvc.perform(get(BASE_URL + "/me"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.email", is(currentUser.getEmail())))
                    .andExpect(jsonPath("$.name", is(currentUser.getName())))
                    .andExpect(jsonPath("$.role", is(currentUser.getRole().name())));
            verify(userService, times(1)).getUserByEmail(eq("current@example.com"));
        }

        @Test
        @WithAnonymousUser 
        @DisplayName("shouldReturnUnauthorizedWhenNotAuthenticated")
        void shouldReturnUnauthorizedWhenNotAuthenticated() throws Exception {
            mockMvc.perform(get(BASE_URL + "/me"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
            verify(userService, never()).getUserByEmail(anyString());
        }

        @Test
        @WithMockUser(username = "ghost@example.com") 
        @DisplayName("shouldReturnNotFoundWhenUserFromTokenNotInDB")
        void shouldReturnNotFoundWhenUserFromTokenNotInDB() throws Exception {
            when(userService.getUserByEmail("ghost@example.com"))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found: User not found"));
            mockMvc.perform(get(BASE_URL + "/me"))
                    .andDo(print())
                    .andExpect(status().isNotFound());
            verify(userService, times(1)).getUserByEmail(eq("ghost@example.com"));
        }
    }

    
    @Nested
    @DisplayName("PATCH /api/users/change-role Tests")
    class ChangeRoleTests {

        @Test
        @WithMockUser(roles = "ADMIN") 
        @DisplayName("shouldChangeRoleSuccessfullyWhenAdmin")
        void shouldChangeRoleSuccessfullyWhenAdmin() throws Exception {
            String targetEmail = "user.to.promote@example.com";
            Role newRole = Role.ADMIN;
            doNothing().when(userService).changeRoleUser(eq(targetEmail), eq(newRole));
            mockMvc.perform(patch(BASE_URL + "/change-role") 
                            .param("email", targetEmail)
                            .param("role", newRole.name()))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string(isEmptyOrNullString()));
            verify(userService, times(1)).changeRoleUser(emailCaptor.capture(), roleCaptor.capture());
            assertEquals(targetEmail, emailCaptor.getValue());
            assertEquals(newRole, roleCaptor.getValue());
        }

        @Test
        @WithMockUser(roles = "USER") 
        @DisplayName("shouldReturnForbiddenWhenUserIsNotAdmin")
        void shouldReturnForbiddenWhenUserIsNotAdmin() throws Exception {
            String targetEmail = "user.to.promote@example.com";
            Role newRole = Role.ADMIN;
            mockMvc.perform(patch(BASE_URL + "/change-role")
                            .param("email", targetEmail)
                            .param("role", newRole.name()))
                    .andDo(print())
                    .andExpect(status().isForbidden());
            verify(userService, never()).changeRoleUser(anyString(), any());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("shouldReturnUnauthorizedWhenChangingRoleAnonymous")
        void shouldReturnUnauthorizedWhenChangingRoleAnonymous() throws Exception {
            String targetEmail = "user.to.promote@example.com";
            Role newRole = Role.ADMIN;
            mockMvc.perform(patch(BASE_URL + "/change-role")
                            .param("email", targetEmail)
                            .param("role", newRole.name()))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
            verify(userService, never()).changeRoleUser(anyString(), any());
        }


        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldReturnNotFoundWhenTargetUserNotFound")
        void shouldReturnNotFoundWhenTargetUserNotFound() throws Exception {
            String nonExistentEmail = "noone@example.com";
            Role newRole = Role.ADMIN;
            doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found: User not found"))
                    .when(userService).changeRoleUser(eq(nonExistentEmail), eq(newRole));
            mockMvc.perform(patch(BASE_URL + "/change-role")
                            .param("email", nonExistentEmail)
                            .param("role", newRole.name()))
                    .andDo(print())
                    .andExpect(status().isNotFound());
            verify(userService, times(1)).changeRoleUser(eq(nonExistentEmail), eq(newRole));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldReturnBadRequestWhenEmailIsInvalid")
        void shouldReturnBadRequestWhenEmailIsInvalid() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/change-role")
                            .param("email", "not-an-email") 
                            .param("role", Role.ADMIN.name()))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
            verify(userService, never()).changeRoleUser(anyString(), any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldReturnBadRequestWhenRoleIsNull")
        void shouldReturnBadRequestWhenRoleIsNull() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/change-role")
                            .param("email", "test@example.com"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
            verify(userService, never()).changeRoleUser(anyString(), any());
        }
    }

    
    @Nested
    @DisplayName("PUT /api/users/{email}/lock Tests")
    class LockUserTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldLockUserSuccessfullyWhenAdmin")
        void shouldLockUserSuccessfullyWhenAdmin() throws Exception {
            String targetEmail = "user.to.lock@example.com";
            doNothing().when(userService).lockUser(eq(targetEmail));
            mockMvc.perform(put(BASE_URL + "/" + targetEmail + "/lock")) 
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string(isEmptyOrNullString()));
            verify(userService, times(1)).lockUser(emailCaptor.capture());
            assertEquals(targetEmail, emailCaptor.getValue());
        }

        @Test
        @WithMockUser(roles = "USER") 
        @DisplayName("shouldReturnForbiddenWhenLockingUserAsNonAdmin")
        void shouldReturnForbiddenWhenLockingUserAsNonAdmin() throws Exception {
            String targetEmail = "user.to.lock@example.com";
            mockMvc.perform(put(BASE_URL + "/" + targetEmail + "/lock"))
                    .andDo(print())
                    .andExpect(status().isForbidden());
            verify(userService, never()).lockUser(anyString());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("shouldReturnUnauthorizedWhenLockingUserAsAnonymous")
        void shouldReturnUnauthorizedWhenLockingUserAsAnonymous() throws Exception {
            String targetEmail = "user.to.lock@example.com";
            mockMvc.perform(put(BASE_URL + "/" + targetEmail + "/lock"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
            verify(userService, never()).lockUser(anyString());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldReturnNotFoundWhenLockingNonExistentUser")
        void shouldReturnNotFoundWhenLockingNonExistentUser() throws Exception {
            String nonExistentEmail = "noone@example.com";
            doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found: User not found"))
                    .when(userService).lockUser(eq(nonExistentEmail));
            mockMvc.perform(put(BASE_URL + "/" + nonExistentEmail + "/lock"))
                    .andDo(print())
                    .andExpect(status().isNotFound());
            verify(userService, times(1)).lockUser(eq(nonExistentEmail));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldReturnBadRequestWhenLockingWithInvalidEmailFormat")
        void shouldReturnBadRequestWhenLockingWithInvalidEmailFormat() throws Exception {
            mockMvc.perform(put(BASE_URL + "/not-an-email/lock")) 
                    .andDo(print())
                    .andExpect(status().isBadRequest()); 
            verify(userService, never()).lockUser(anyString());
        }
    }


    
    @Nested
    @DisplayName("PUT /api/users/{email}/unlock Tests")
    class UnlockUserTests {
        

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldUnlockUserSuccessfullyWhenAdmin")
        void shouldUnlockUserSuccessfullyWhenAdmin() throws Exception {
            String targetEmail = "user.to.unlock@example.com";
            doNothing().when(userService).unlockUser(eq(targetEmail));
            mockMvc.perform(put(BASE_URL + "/" + targetEmail + "/unlock"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string(isEmptyOrNullString()));
            verify(userService, times(1)).unlockUser(emailCaptor.capture());
            assertEquals(targetEmail, emailCaptor.getValue());
        }

        @Test
        @WithMockUser(roles = "USER") 
        @DisplayName("shouldReturnForbiddenWhenUnlockingUserAsNonAdmin")
        void shouldReturnForbiddenWhenUnlockingUserAsNonAdmin() throws Exception {
            String targetEmail = "user.to.unlock@example.com";
            mockMvc.perform(put(BASE_URL + "/" + targetEmail + "/unlock"))
                    .andDo(print())
                    .andExpect(status().isForbidden());
            verify(userService, never()).unlockUser(anyString());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("shouldReturnUnauthorizedWhenUnlockingUserAsAnonymous")
        void shouldReturnUnauthorizedWhenUnlockingUserAsAnonymous() throws Exception {
            String targetEmail = "user.to.unlock@example.com";
            mockMvc.perform(put(BASE_URL + "/" + targetEmail + "/unlock"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
            verify(userService, never()).unlockUser(anyString());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldReturnNotFoundWhenUnlockingNonExistentUser")
        void shouldReturnNotFoundWhenUnlockingNonExistentUser() throws Exception {
            String nonExistentEmail = "noone@example.com";
            doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found: User not found"))
                    .when(userService).unlockUser(eq(nonExistentEmail));
            mockMvc.perform(put(BASE_URL + "/" + nonExistentEmail + "/unlock"))
                    .andDo(print())
                    .andExpect(status().isNotFound());
            verify(userService, times(1)).unlockUser(eq(nonExistentEmail));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("shouldReturnBadRequestWhenUnlockingWithInvalidEmailFormat")
        void shouldReturnBadRequestWhenUnlockingWithInvalidEmailFormat() throws Exception {
            mockMvc.perform(put(BASE_URL + "/not-an-email/unlock"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
            verify(userService, never()).unlockUser(anyString());
        }
    }
}
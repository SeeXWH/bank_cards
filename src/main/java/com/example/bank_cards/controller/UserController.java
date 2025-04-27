package com.example.bank_cards.controller;

import com.example.bank_cards.dto.CurrentUserDto;
import com.example.bank_cards.dto.LoginDto;
import com.example.bank_cards.dto.RegistrationDto;
import com.example.bank_cards.enums.Role;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.serviceInterface.UserServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "UserController")
public class UserController {

    private final UserServiceImpl userService;

    @PostMapping("/register")
    @Operation(summary = "Регистрация нового пользователя",
            description = "Регистрирует нового пользователя в системе")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пользователь успешно зарегистрирован",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (например, некорректный пароль)"),
            @ApiResponse(responseCode = "409", description = "Пользователь с таким email уже существует"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Данные для регистрации пользователя",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = LoginDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Пример регистрации",
                                    value = "{\"name\": \"Ivan\", \"email\": \"user@example.com\", \"password\": \"securePassword123\"}",
                                    description = "Стандартный пример регистрации пользователя"
                            )
                    }
            )
    )
    public ResponseEntity<String> registerUser(@RequestBody @Valid RegistrationDto userDto) {
        String token = userService.registerUser(userDto);
        return ResponseEntity.ok(token);
    }

    @PostMapping("/login")
    @Operation(summary = "Аутентификация пользователя",
            description = "Аутентифицирует пользователя и возвращает JWT токен")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Аутентификация успешна",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (email или пароль пустые)"),
            @ApiResponse(responseCode = "401", description = "Неверные учетные данные"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Данные для аутентификации",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = LoginDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Пример входа",
                                    value = "{\"email\": \"user@example.com\", \"password\": \"securePassword123\"}",
                                    description = "Стандартный пример входа пользователя"
                            )
                    }
            )
    )
    public ResponseEntity<String> authenticateUser(@RequestBody @Valid LoginDto userDto) {
        String token = userService.authenticateUser(userDto);
        return ResponseEntity.ok(token);
    }


    @GetMapping(value = "/me")
    @Operation(summary = "Получение информации о текущем пользователе",
            description = "Получает данные текущего пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пользователь успешно получен",
                    content = @Content(schema = @Schema(implementation = CurrentUserDto.class))),
            @ApiResponse(responseCode = "401", description = "Неверные учетные данные"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CurrentUserDto> getCurrentUser(Authentication authentication) {
        String phoneNumber = authentication.getName();
        AppUser user = userService.getUserByEmail(phoneNumber);
        CurrentUserDto currentUserDto = new CurrentUserDto();
        currentUserDto.setEmail(user.getEmail());
        currentUserDto.setName(user.getName());
        currentUserDto.setRole(user.getRole());
        return ResponseEntity.ok(currentUserDto);

    }

    @PatchMapping("/change-role")
    @Operation(summary = "Изменение роли пользователя",
            description = "Изменяет роль пользователя по email (только для администраторов)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Роль успешно изменена"),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (email или роль пустые)"),
            @ApiResponse(responseCode = "401", description = "Неавторизованный доступ"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён (требуются права администратора)"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> changeUserRole(
            @RequestParam @NotBlank(message = "email cannot be null or empty") @Email(message = "Email should be a valid email address format") String email,
            @RequestParam @NotNull(message = "role cannot be null or empty") Role role
    ) {
        userService.changeRoleUser(email, role);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping("/{email}/lock")
    public ResponseEntity<Void> lockUser(@PathVariable @NotBlank(message = "email cannot be null or empty") @Email(message = "Email should be a valid email address format") String email) {
        userService.lockUser(email);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{email}/unlock")
    public ResponseEntity<Void> unlockUser(@PathVariable @NotBlank(message = "email cannot be null or empty") @Email(message = "Email should be a valid email address format") String email) {
        userService.unlockUser(email);
        return ResponseEntity.ok().build();
    }
}
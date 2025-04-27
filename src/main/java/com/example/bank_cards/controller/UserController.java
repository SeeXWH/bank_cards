package com.example.bank_cards.controller;

import com.example.bank_cards.dto.CurrentUserDto;
import com.example.bank_cards.dto.LoginDto;
import com.example.bank_cards.dto.RegistrationDto;
import com.example.bank_cards.enums.Role;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.serviceInterface.UserServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter; 
import io.swagger.v3.oas.annotations.Parameters; 
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "UserController", description = "Контроллер для управления пользователями и аутентификацией") 
public class UserController {

    private final UserServiceImpl userService;

    @PostMapping("/register")
    @Operation(summary = "Регистрация нового пользователя",
            description = "Регистрирует нового пользователя в системе с указанными именем, email и паролем. Возвращает JWT токен для созданного пользователя.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пользователь успешно зарегистрирован и аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class, description = "JWT токен доступа"))), 
            @ApiResponse(responseCode = "400", description = "Неверный запрос (например, пароль слишком короткий, невалидный email, отсутствует обязательное поле)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Password must be at least 8 characters long"))), 
            @ApiResponse(responseCode = "409", description = "Пользователь с таким email уже существует",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Conflict: User with this email already exists"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Данные для регистрации: имя пользователя, email и пароль.",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    
                    schema = @Schema(implementation = RegistrationDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Пример регистрации",
                                    value = "{\"name\": \"Иван Иванов\", \"email\": \"ivan.ivanov@example.com\", \"password\": \"mySecurePassword123\"}",
                                    summary = "Регистрация нового пользователя" 
                            )
                    }
            )
    )
    public ResponseEntity<String> registerUser(@RequestBody @Valid @NotNull(message = "Request body cannot be null") RegistrationDto userDto) {
        String token = userService.registerUser(userDto);
        return ResponseEntity.ok(token);
    }

    @PostMapping("/login")
    @Operation(summary = "Аутентификация пользователя",
            description = "Аутентифицирует пользователя по email и паролю. В случае успеха возвращает JWT токен.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Аутентификация успешна",
                    content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class, description = "JWT токен доступа"))), 
            @ApiResponse(responseCode = "400", description = "Неверный запрос (например, email или пароль пустые)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Password is required"))), 
            @ApiResponse(responseCode = "401", description = "Неверные учетные данные (неправильный email или пароль, пользователь заблокирован)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized: Bad credentials"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Учетные данные для входа: email и пароль.",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = LoginDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Пример входа",
                                    value = "{\"email\": \"ivan.ivanov@example.com\", \"password\": \"mySecurePassword123\"}",
                                    summary = "Вход пользователя по email и паролю"
                            )
                    }
            )
    )
    public ResponseEntity<String> authenticateUser(@RequestBody @Valid @NotNull(message = "Request body cannot be null") LoginDto userDto) {
        String token = userService.authenticateUser(userDto);
        return ResponseEntity.ok(token);
    }


    @GetMapping(value = "/me")
    @Operation(summary = "Получение информации о текущем пользователе",
            description = "Возвращает информацию (email, имя, роль) о пользователе, аутентифицированном с помощью JWT токена.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Информация о текущем пользователе успешно получена",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CurrentUserDto.class))),
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован (неверный или отсутствующий токен)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "404", description = "Пользователь, связанный с токеном, не найден в базе данных",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: User not found with email: ..."))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @PreAuthorize("isAuthenticated()") 
    public ResponseEntity<CurrentUserDto> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        String email = authentication.getName();
        AppUser user = userService.getUserByEmail(email);
        CurrentUserDto currentUserDto = new CurrentUserDto();
        currentUserDto.setEmail(user.getEmail());
        currentUserDto.setName(user.getName());
        currentUserDto.setRole(user.getRole());
        return ResponseEntity.ok(currentUserDto);
    }

    @PatchMapping("/change-role")
    @Operation(summary = "Изменение роли пользователя (Админ)",
            description = "Изменяет роль пользователя по email. Требуются права Администратора.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Роль успешно изменена (тело ответа пустое)",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (например, не указан email или роль, невалидный email)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "email cannot be null or empty"))), 
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "403", description = "Доступ запрещён (недостаточно прав)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Forbidden"))), 
            @ApiResponse(responseCode = "404", description = "Пользователь с указанным email не найден",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: User not found with email: ..."))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @Parameters({ 
            @Parameter(name = "email", description = "Email пользователя, чью роль нужно изменить", required = true,
                    example = "user.to.promote@example.com", schema = @Schema(type = "string")),
            @Parameter(name = "role", description = "Новая роль для пользователя", required = true,
                    schema = @Schema(implementation = Role.class, example = "ADMIN"))
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
    @Operation(summary = "Блокировка пользователя (Админ)",
            description = "Блокирует учетную запись пользователя по email. Заблокированный пользователь не сможет войти. Требуются права Администратора.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пользователь успешно заблокирован (тело ответа пустое)",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (например, невалидный email)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Email should be a valid email address format"))), 
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "403", description = "Доступ запрещен (недостаточно прав)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Forbidden"))), 
            @ApiResponse(responseCode = "404", description = "Пользователь с указанным email не найден",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: User not found"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    public ResponseEntity<Void> lockUser(
            @Parameter(description = "Email пользователя для блокировки", required = true, example = "user.to.lock@example.com")
            @PathVariable @NotBlank(message = "email cannot be null or empty") @Email(message = "Email should be a valid email address format") String email) {
        userService.lockUser(email);
        return ResponseEntity.ok().build();
    }

    
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping("/{email}/unlock") 
    @Operation(summary = "Разблокировка пользователя (Админ)",
            description = "Разблокирует учетную запись пользователя по email. Требуются права Администратора.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пользователь успешно разблокирован (тело ответа пустое)",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (например, невалидный email)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Email should be a valid email address format"))), 
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "403", description = "Доступ запрещен (недостаточно прав)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Forbidden"))), 
            @ApiResponse(responseCode = "404", description = "Пользователь с указанным email не найден",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: User not found"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    public ResponseEntity<Void> unlockUser(
            @Parameter(description = "Email пользователя для разблокировки", required = true, example = "user.to.unlock@example.com")
            @PathVariable @NotBlank(message = "email cannot be null or empty") @Email(message = "Email should be a valid email address format") String email) {
        userService.unlockUser(email);
        return ResponseEntity.ok().build();
    }
}
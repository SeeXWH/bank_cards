package com.example.bank_cards.controller;

import com.example.bank_cards.dto.AppUserDto;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "UserController")
public class UserController {

    private final UserService userService;

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
                    schema = @Schema(implementation = AppUserDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Пример регистрации",
                                    value = "{\"email\": \"user@example.com\", \"password\": \"securePassword123\"}",
                                    description = "Стандартный пример регистрации пользователя"
                            )
                    }
            )
    )
    public ResponseEntity<String> registerUser(@RequestBody AppUserDto userDto) {
        log.info("Registering new user with email: {}", userDto.getEmail());
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
                    schema = @Schema(implementation = AppUserDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Пример входа",
                                    value = "{\"email\": \"user@example.com\", \"password\": \"securePassword123\"}",
                                    description = "Стандартный пример входа пользователя"
                            )
                    }
            )
    )
    public ResponseEntity<String> authenticateUser(@RequestBody AppUserDto userDto) {
        log.info("Authenticating user with email: {}", userDto.getEmail());
        String token = userService.authenticateUser(userDto);
        return ResponseEntity.ok(token);
    }
}
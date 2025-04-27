package com.example.bank_cards.controller;

import com.example.bank_cards.dto.CardCreateDto;
import com.example.bank_cards.dto.CardDto;
import com.example.bank_cards.dto.CardLimitDto;
import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.serviceInterface.CardServiceImpl;
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
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "CardController", description = "Контроллер для управления банковскими картами")
public class CardController {

    private final CardServiceImpl cardService;

    @PostMapping("/create-card")
    @Operation(summary = "Создание новой карты",
            description = "Создает новую банковскую карту для пользователя с указанным email и сроком действия. Требуются права Администратора.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Карта успешно создана",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CardDto.class))),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (например, невалидный email, дата в прошлом, отсутствует тело запроса или обязательные поля)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "email must be email pattern"))), 
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
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Данные для создания карты: email владельца и дата окончания срока действия.",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CardCreateDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Пример создания карты",
                                    value = "{\"email\": \"user@example.com\", \"expiryDate\": \"2027-12-31\"}",
                                    summary = "Создание карты для user@example.com"
                            )
                    }
            )
    )
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<CardDto> createCard(@RequestBody @NotNull(message = "Тело запроса не может быть null") @Valid CardCreateDto cardCreateDto) {
        CardDto createdCard = cardService.createCard(cardCreateDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCard);
    }


    @PutMapping("/set-card-status")
    @Operation(summary = "Изменение статуса карты",
            description = "Обновляет статус карты по ее UUID (ACTIVE, BLOCKED). Запрещено устанавливать статус EXPIRED. Требуются права Администратора.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Статус карты успешно изменен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CardDto.class))),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (не указан ID карты или статус, попытка установить EXPIRED)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Cannot manually set status to EXPIRED"))), 
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "403", description = "Доступ запрещен (недостаточно прав)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Forbidden"))), 
            @ApiResponse(responseCode = "404", description = "Карта с указанным ID не найдена",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: Card not found"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @Parameters({
            @Parameter(name = "id", description = "UUID карты, у которой меняется статус", required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000", schema = @Schema(implementation = UUID.class)),
            @Parameter(name = "status", description = "Новый статус карты", required = true,
                    schema = @Schema(implementation = CardStatus.class, example = "BLOCKED"))
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<CardDto> setCardStatus(
            @RequestParam @NotNull(message = "id не может быть null") UUID id,
            @RequestParam @NotNull(message = "status не может быть null") CardStatus status) {
        CardDto updatedCard = cardService.setCardStatus(id, status);
        return ResponseEntity.ok(updatedCard);
    }

    @GetMapping("/my-cards")
    @Operation(summary = "Получение карт текущего пользователя",
            description = "Возвращает постраничный список карт, принадлежащих аутентифицированному пользователю. Можно фильтровать по статусу и настраивать сортировку.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список карт успешно получен (может быть пустым)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(type = "array", implementation = CardDto.class))),
            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса (пагинация, сортировка)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Invalid sort parameter"))), 
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @Parameters({
            @Parameter(name = "status", description = "Фильтр по статусу карты (ACTIVE, BLOCKED, EXPIRED). Если не указан, возвращаются карты со всеми статусами.",
                    required = false, schema = @Schema(implementation = CardStatus.class)),
            @Parameter(name = "page", description = "Номер страницы (начиная с 0)", example = "0",
                    required = false, schema = @Schema(type = "integer", defaultValue = "0")),
            @Parameter(name = "size", description = "Количество элементов на странице", example = "10",
                    required = false, schema = @Schema(type = "integer", defaultValue = "10")),
            @Parameter(name = "sort", description = "Параметры сортировки (поле,направление). Например: 'expiryDate,desc' или 'balance,asc'. По умолчанию 'expiryDate,asc'.",
                    example = "expiryDate,asc", required = false, schema = @Schema(type = "string", defaultValue = "expiryDate,asc"))
    })
    public ResponseEntity<List<CardDto>> getCurrentUserCards(
            Authentication authentication,
            @RequestParam(required = false) CardStatus status,
            @RequestParam(defaultValue = "0")   @PositiveOrZero(message = "page должен быть >= 0") int page,
            @RequestParam(defaultValue = "10")  @PositiveOrZero(message = "size должен быть >= 0") int size,
            @RequestParam(defaultValue = "expiryDate,asc")  String sort) {
        if (authentication == null || !authentication.isAuthenticated()) {
            
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Пользователь не аутентифицирован");
        }
        String email = authentication.getName();
        try {
            String[] sortParams = sort.split(",");
            String sortField = sortParams[0];
            Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc")
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
            List<CardDto> cards = cardService.getCardsByUserEmail(email, status, pageable);
            return ResponseEntity.ok(cards);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            log.error("Invalid sort parameter provided: {}", sort, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный параметр сортировки: " + sort);
        }
    }

    @GetMapping("/cards-by-user")
    @Operation(summary = "Получение карт по email пользователя (Админ)",
            description = "Возвращает постраничный список карт для пользователя с указанным email. Доступно только Администраторам.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список карт успешно получен (может быть пустым)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(type = "array", implementation = CardDto.class))),
            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса (email, пагинация, сортировка)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "email must be email pattern"))), 
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
    @Parameters({
            @Parameter(name = "email", description = "Email пользователя, чьи карты нужно получить", required = true,
                    example = "admin@example.com", schema = @Schema(implementation = String.class)),
            @Parameter(name = "status", description = "Фильтр по статусу карты (ACTIVE, BLOCKED, EXPIRED). Если не указан, возвращаются карты со всеми статусами.",
                    required = false, schema = @Schema(implementation = CardStatus.class)),
            @Parameter(name = "page", description = "Номер страницы (начиная с 0)", example = "0",
                    required = false, schema = @Schema(type = "integer", defaultValue = "0")),
            @Parameter(name = "size", description = "Количество элементов на странице", example = "10",
                    required = false, schema = @Schema(type = "integer", defaultValue = "10")),
            @Parameter(name = "sort", description = "Параметры сортировки (поле,направление). Например: 'expiryDate,desc' или 'balance,asc'. По умолчанию 'expiryDate,asc'.",
                    example = "expiryDate,asc", required = false, schema = @Schema(type = "string", defaultValue = "expiryDate,asc"))
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<CardDto>> getCardsByUser(
            @RequestParam @NotBlank(message = "email не может быть пустым") @Email(message = "email должен быть валидным адресом") String email,
            @RequestParam(required = false) CardStatus status,
            @RequestParam(defaultValue = "0") @PositiveOrZero(message = "page должен быть >= 0") int page,
            @RequestParam(defaultValue = "10") @PositiveOrZero(message = "size должен быть >= 0") int size,
            @RequestParam(defaultValue = "expiryDate,asc") String sort) {
        try {
            String[] sortParams = sort.split(",");
            String sortField = sortParams[0];
            Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc")
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
            List<CardDto> cards = cardService.getCardsByUserEmail(email, status, pageable);
            return ResponseEntity.ok(cards);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            log.error("Invalid sort parameter provided: {}", sort, e);
            
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный параметр сортировки: " + sort);
        }
    }

    @DeleteMapping("/{id}/delete")
    @Operation(summary = "Удаление карты (Админ)",
            description = "Удаляет карту по указанному UUID. Требуются права Администратора.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Карта успешно удалена (нет тела ответа)"),
            @ApiResponse(responseCode = "400", description = "Неверный формат UUID",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Invalid UUID format"))), 
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "403", description = "Доступ запрещен (недостаточно прав)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Forbidden"))), 
            @ApiResponse(responseCode = "404", description = "Карта с указанным ID не найдена",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: Card not found"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteCard(
            @Parameter(description = "UUID карты для удаления", required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000", schema = @Schema(implementation = UUID.class))
            @PathVariable @NotNull(message = "id не может быть null") UUID id) {
        cardService.deleteCard(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/limits")
    @Operation(summary = "Обновление лимитов карты (Админ)",
            description = "Частично обновляет дневной и/или месячный лимит для карты по ее UUID. Можно обновлять один или оба лимита. Поля, не указанные в запросе, не изменяются. Требуются права Администратора.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Лимиты успешно обновлены",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CardDto.class))),
            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса (например, отрицательные лимиты, невалидное тело запроса)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Daily limit cannot be negative"))), 
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "403", description = "Доступ запрещен (недостаточно прав)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Forbidden"))), 
            @ApiResponse(responseCode = "404", description = "Карта с указанным ID не найдена",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: Card not found"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Данные для обновления лимитов. Укажите только те лимиты, которые нужно изменить.",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CardLimitDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Обновление только дневного лимита",
                                    value = "{\"dailyLimit\": 1500.50}",
                                    summary = "Установить дневной лимит 1500.50"
                            ),
                            @ExampleObject(
                                    name = "Обновление только месячного лимита",
                                    value = "{\"monthlyLimit\": 10000}",
                                    summary = "Установить месячный лимит 10000"
                            ),
                            @ExampleObject(
                                    name = "Обновление обоих лимитов",
                                    value = "{\"dailyLimit\": 1000, \"monthlyLimit\": 5000}",
                                    summary = "Установить дневной=1000, месячный=5000"
                            )
                    }
            )
    )
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<CardDto> updateCardLimits(
            @Parameter(description = "UUID карты для обновления лимитов", required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000", schema = @Schema(implementation = UUID.class))
            @PathVariable @NotNull(message = "id не может быть null") UUID id,
            @RequestBody @NotNull(message = "Тело запроса не может быть null") @Valid CardLimitDto cardLimitDto) {
        CardDto updatedCard = cardService.setCardLimit(id, cardLimitDto);
        return ResponseEntity.ok(updatedCard);
    }

    @GetMapping("/{id}/number")
    @Operation(summary = "Получение полного номера карты (Админ)",
            description = "Возвращает полный, расшифрованный номер карты по её UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Номер карты успешно получен",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "1111222233334444"))),
            @ApiResponse(responseCode = "400", description = "Неверный формат UUID",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Invalid UUID format"))), 
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "403", description = "Доступ запрещен",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Forbidden"))), 
            @ApiResponse(responseCode = "404", description = "Карта с указанным ID не найдена",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: Card not found"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера (например, ошибка дешифрования)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error: Decryption failed"))) 
    })
    public ResponseEntity<String> getCardNumber(
            @Parameter(description = "UUID карты, чей номер нужно получить", required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000", schema = @Schema(implementation = UUID.class))
            @PathVariable @NotNull(message = "id не может быть null") UUID id) {
        String decryptedNumber = cardService.getCardNumber(id);
        return ResponseEntity.ok(decryptedNumber);
    }
}
package com.example.bank_cards.controller;

import com.example.bank_cards.dto.CardCreateDto;
import com.example.bank_cards.dto.CardDto;
import com.example.bank_cards.dto.CardLimitDto;
import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "CardController")
public class CardController {

    private final CardService cardService;

    @PostMapping("/create-card")
    @Operation(summary = "Создание новой карты",
            description = "Создает новую банковскую карту для указанного пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Карта успешно создана",
                    content = @Content(schema = @Schema(implementation = Card.class))),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (не указан email или дата expiry)"),
            @ApiResponse(responseCode = "404", description = "Пользователь с указанным email не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Данные для создания карты",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CardCreateDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Пример создания карты",
                                    value = "{\"email\": \"user@example.com\", \"expiryDate\": \"2026-12-31\"}",
                                    description = "Стандартный пример создания новой карты"
                            )
                    }
            )
    )
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<CardDto> createCard(@RequestBody CardCreateDto cardCreateDto) {
        log.info("Creating new card for user with email: {}", cardCreateDto.getEmail());
        CardDto createdCard = cardService.createCard(cardCreateDto);
        return ResponseEntity.ok(createdCard);
    }


    @PutMapping("/set-card-status")
    @Operation(summary = "Изменение статуса карты",
            description = "Обновляет статус указанной карты (ACTIVE, BLOCKED, EXPIRED и т.д.)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Статус карты успешно изменен",
                    content = @Content(schema = @Schema(implementation = CardDto.class))),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (не указан ID карты или статус)"),
            @ApiResponse(responseCode = "404", description = "Карта с указанным ID не найдена"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещен (требуются права ADMIN)"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @Parameters({
            @Parameter(name = "id", description = "UUID карты", required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000"),
            @Parameter(name = "status", description = "Новый статус карты", required = true,
                    schema = @Schema(implementation = CardStatus.class),
                    examples = @ExampleObject(value = "\"ACTIVE\"", description = "Допустимые значения: ACTIVE, BLOCKED, EXPIRED"))
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<CardDto> setCardStatus(
            @RequestParam UUID id,
            @RequestParam CardStatus status) {
        log.info("Attempting to change status for card ID: {} to status: {}", id, status);
        CardDto updatedCard = cardService.setCardStatus(id, status);
        log.info("Successfully changed status for card ID: {}", id);

        return ResponseEntity.ok(updatedCard);
    }

    @GetMapping("/my-cards")
    @Operation(summary = "Получение карт текущего пользователя",
            description = "Возвращает список карт аутентифицированного пользователя с возможностью фильтрации по статусу")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список карт успешно получен",
                    content = @Content(schema = @Schema(implementation = CardDto.class))),
            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса"),
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещен"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @Parameters({
            @Parameter(name = "status", description = "Фильтр по статусу карты",
                    content = @Content(schema = @Schema(implementation = CardStatus.class))),
            @Parameter(name = "page", description = "Номер страницы (начиная с 0)", example = "0"),
            @Parameter(name = "size", description = "Размер страницы", example = "10"),
            @Parameter(name = "sort", description = "Поле для сортировки (например, expiryDate,asc)",
                    example = "expiryDate,asc")

    })
    public ResponseEntity<List<CardDto>> getCurrentUserCards(
            Authentication authentication,
            @RequestParam(required = false) CardStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "expiryDate,asc") String sort) {

        try {
            String[] sortParams = sort.split(",");
            Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc")
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));
            log.info("Fetching cards for user: {}, status: {}, page: {}, size: {}, sort: {}",
                    authentication.getName(), status, page, size, sort);
            List<CardDto> cards = cardService.getCardsByUserEmail(
                    authentication.getName(), status, pageable);
            return ResponseEntity.ok(cards);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid sort parameter: {}", sort);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort parameter");
        }
    }

    @GetMapping("/cards-by-user")
    @Operation(summary = "Получение карт по email пользователя",
            description = "Возвращает список карт для указанного пользователя с фильтрацией по статусу")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список карт успешно получен"),
            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещен"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @Parameters({
            @Parameter(name = "email", description = "Email пользователя", required = true),
            @Parameter(name = "status", description = "Фильтр по статусу карты"),
            @Parameter(name = "page", description = "Номер страницы", example = "0"),
            @Parameter(name = "size", description = "Размер страницы", example = "10"),
            @Parameter(name = "sort", description = "Сортировка (поле,направление)",
                    example = "expiryDate,asc")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<CardDto>> getCardsByUser(
            @RequestParam String email,
            @RequestParam(required = false) CardStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "expiryDate,asc") String sort) {
        try {
            String[] sortParams = sort.split(",");
            Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc")
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));
            log.info("Request cards for user: {}, status: {}, page: {}, size: {}, sort: {}",
                    email, status, page, size, sort);
            List<CardDto> cards = cardService.getCardsByUserEmail(email, status, pageable);
            return ResponseEntity.ok(cards);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid sort parameter: {}", sort);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort parameter");
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удаление карты",
            description = "Удаляет карту по указанному идентификатору")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Карта успешно удалена"),
            @ApiResponse(responseCode = "400", description = "Неверный ID карты"),
            @ApiResponse(responseCode = "404", description = "Карта не найдена"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещен"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @Parameters({
            @Parameter(name = "id", description = "UUID карты", required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteCard(
            @PathVariable UUID id) {
        log.info("Attempt to delete card with ID: {}", id);

        cardService.deleteCard(id);

        log.info("Card with ID: {} successfully deleted", id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/limits")
    @Operation(summary = "Обновление лимитов карты",
            description = "Частично обновляет дневной и/или месячный лимит карты. Можно обновлять оба лимита или только один.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Лимиты успешно обновлены",
                    content = @Content(schema = @Schema(implementation = CardDto.class))),
            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса"),
            @ApiResponse(responseCode = "404", description = "Карта не найдена"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещен")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Данные для обновления лимитов",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CardLimitDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Обновление только дневного лимита",
                                    value = "{\"dailyLimit\": 1000}",
                                    summary = "Пример обновления дневного лимита"
                            ),
                            @ExampleObject(
                                    name = "Обновление только месячного лимита",
                                    value = "{\"monthlyLimit\": 5000}",
                                    summary = "Пример обновления месячного лимита"
                            ),
                            @ExampleObject(
                                    name = "Обновление обоих лимитов",
                                    value = "{\"dailyLimit\": 1000, \"monthlyLimit\": 5000}",
                                    summary = "Пример обновления обоих лимитов"
                            )
                    }
            )
    )
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<CardDto> updateCardLimits(
            @Parameter(description = "UUID карты", required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID id,
            @RequestBody CardLimitDto cardLimitDto) {
        log.info("Attempt to update limits for card ID: {}, limits: {}", id, cardLimitDto);
        CardDto updatedCard = cardService.setCardLimit(id, cardLimitDto);
        log.info("Card limits updated successfully for card ID: {}", id);
        return ResponseEntity.ok(updatedCard);
    }
}

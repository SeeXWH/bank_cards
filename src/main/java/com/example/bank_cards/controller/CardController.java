package com.example.bank_cards.controller;

import com.example.bank_cards.dto.CardCreateDto;
import com.example.bank_cards.dto.CardDto;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.service.CardService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}

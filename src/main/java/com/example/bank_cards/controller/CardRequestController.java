package com.example.bank_cards.controller;

import com.example.bank_cards.dto.BlockCardRequestDto;
import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.enums.RequestStatus;
import com.example.bank_cards.service.CardRequestService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/card-requests")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "CardRequestController")
public class CardRequestController {

    private final CardRequestService cardRequestService;

    @PostMapping("/create-card")
    @Operation(summary = "Запрос на создание новой карты",
            description = "Создает запрос на выпуск новой карты для текущего пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Запрос успешно создан"),
            @ApiResponse(responseCode = "400", description = "Неверный запрос"),
            @ApiResponse(responseCode = "401", description = "Неавторизованный доступ"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> createCardRequest(Authentication authentication) {
        String email = authentication.getName();
        log.info("Creating new card request for user: {}", email);
        cardRequestService.crateRequestToCreateCard(email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/block-card")
    @Operation(summary = "Запрос на блокировку карты",
            description = "Создает запрос на блокировку указанной карты")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Запрос на блокировку успешно создан"),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (не указан номер карты)"),
            @ApiResponse(responseCode = "401", description = "Неавторизованный доступ"),
            @ApiResponse(responseCode = "404", description = "Карта не найдена"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Данные для блокировки карты",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = BlockCardRequestDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Пример запроса",
                                    value = "{\"cardNumber\": \"1234567890123456\"}",
                                    description = "Пример запроса на блокировку карты"
                            )
                    }
            )
    )
    public ResponseEntity<Void> blockCardRequest(
            Authentication authentication,
            @RequestBody BlockCardRequestDto blockCardRequestDto) {
        String email = authentication.getName();
        log.info("Creating block card request for user: {}, card: {}", email, blockCardRequestDto.getCardNumber());
        cardRequestService.createRequestToBlockCard(email, blockCardRequestDto);
        return ResponseEntity.ok().build();
    }


    @PutMapping("/set-status")
    @Operation(summary = "Изменение статуса запроса",
            description = "Обновляет статус указанного запроса")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Статус запроса успешно обновлен"),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (не указан ID запроса или статус)"),
            @ApiResponse(responseCode = "401", description = "Неавторизованный доступ"),
            @ApiResponse(responseCode = "404", description = "Запрос не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> updateRequestStatus(
            Authentication authentication,
            @Parameter(description = "ID запроса", required = true)
            @RequestParam UUID requestId,
            @RequestParam RequestStatus requestStatus) {

        String email = authentication.getName();
        log.info("Updating request status for user: {}, requestId: {}, new status: {}",
                email, requestId, requestStatus);

        cardRequestService.setRequestStatus(requestId, requestStatus);
        return ResponseEntity.ok().build();
    }


}

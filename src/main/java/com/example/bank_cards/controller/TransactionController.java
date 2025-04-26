package com.example.bank_cards.controller;

import com.example.bank_cards.dto.DebitRequestDto;
import com.example.bank_cards.dto.TopUpRequestDto;
import com.example.bank_cards.dto.TransferRequestDto;
import com.example.bank_cards.service.TransactionService;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transaction")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "TransactionController")
public class TransactionController {
    private final TransactionService transactionService;

    @PostMapping("/transfer-between-cards")
    @Operation(summary = "Перевод между картами",
            description = "Выполняет перевод указанной суммы с одной карты на другую")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Перевод успешно выполнен"),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (ID карт или сумма не указаны)"),
            @ApiResponse(responseCode = "404", description = "Одна из карт не найдена"),
            @ApiResponse(responseCode = "403", description = "Операция запрещена"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Данные для перевода средств между картами",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = TransferRequestDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Пример перевода",
                                    value = "{\"sendCardId\": \"123e4567-e89b-12d3-a456-426614174000\", " +
                                            "\"receiveCardId\": \"987e6543-e21b-32d3-c456-556614174111\", " +
                                            "\"amount\": 100.00}",
                                    description = "Перевод 100 единиц с одной карты на другую"
                            )
                    }
            )
    )
    public ResponseEntity<Void> transferBetweenCards(@RequestBody TransferRequestDto transferRequestDto, Authentication authentication) {
        String email = authentication.getName();
        transactionService.transferBetweenCards(
                transferRequestDto.getSendCardId(),
                transferRequestDto.getReceiveCardId(),
                transferRequestDto.getAmount(),
                email
        );
        return ResponseEntity.ok().build();
    }

    @PutMapping("/debit-from-card")
    @Operation(summary = "Списание средств с карты",
            description = "Списывает указанную сумму с баланса карты")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Средства успешно списаны"),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (ID карты или сумма не указаны)"),
            @ApiResponse(responseCode = "404", description = "Карта не найдена"),
            @ApiResponse(responseCode = "403", description = "Операция запрещена"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Данные для списания средств с карты",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = DebitRequestDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Пример списания",
                                    value = "{\"cardId\": \"123e4567-e89b-12d3-a456-426614174000\", \"amount\": 50.00}",
                                    description = "Списание 50 единиц с указанной карты"
                            )
                    }
            )
    )
    public ResponseEntity<Void> debitFromCard(@RequestBody DebitRequestDto debitRequestDto, Authentication authentication) {
        String email = authentication.getName();
        transactionService.debitFromCard(
                debitRequestDto.getCardId(),
                debitRequestDto.getAmount(),
                email
        );
        return ResponseEntity.ok().build();
    }


    @PutMapping("/top-up-card")
    @Operation(summary = "Пополнение карты",
            description = "Пополняет указанную карту на заданную сумму. Проверяется, является ли пользователь владельцем карты.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Карта успешно пополнена"),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (ID карты или сумма не указаны)"),
            @ApiResponse(responseCode = "403", description = "Пользователь не является владельцем карты"),
            @ApiResponse(responseCode = "404", description = "Карта или пользователь не найдены"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Данные для пополнения карты",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = TopUpRequestDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Пример пополнения",
                                    value = "{\"cardId\": \"123e4567-e89b-12d3-a456-426614174000\", \"amount\": 150.00}",
                                    description = "Пополнение карты пользователя на сумму 150"
                            )
                    }
            )
    )
    public ResponseEntity<Void> topUpCard(@RequestBody TopUpRequestDto topUpRequestDto, Authentication authentication) {
        String email = authentication.getName();
        transactionService.topUpCard(
                topUpRequestDto.getCardId(),
                topUpRequestDto.getAmount(),
                email
        );
        return ResponseEntity.ok().build();
    }


}

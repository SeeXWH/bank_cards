package com.example.bank_cards.controller;

import com.example.bank_cards.dto.*;
import com.example.bank_cards.enums.TransactionType;
import com.example.bank_cards.serviceInterface.TransactionServiceImpl;
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
import jakarta.validation.constraints.NotNull; 
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/transaction")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "TransactionController", description = "Контроллер для управления транзакциями") 
public class TransactionController {
    private final TransactionServiceImpl transactionService;

    @PostMapping("/transfer-between-cards")
    @Operation(summary = "Перевод между картами",
            description = "Выполняет перевод указанной суммы с одной карты на другую. Обе карты должны принадлежать аутентифицированному пользователю.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Перевод успешно выполнен (тело ответа пустое)",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (например, не указаны ID карт, сумма не положительная)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Transfer amount must be positive"))), 
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "403", description = "Операция запрещена (пользователь не владеет обеими картами)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Forbidden: You are not allowed to perform transfers between these cards"))), 
            @ApiResponse(responseCode = "404", description = "Одна из карт или пользователь не найдены",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: Card not found"))), 
            @ApiResponse(responseCode = "422", description = "Операция не может быть обработана (карта заблокирована/просрочена, недостаточно средств)", 
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unprocessable Entity: Insufficient funds to complete the transaction"))), 
            @ApiResponse(responseCode = "423", description = "Карта заблокирована", 
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Locked: The card is blocked"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Данные для перевода средств: ID карты отправителя, ID карты получателя и сумма перевода.",
            required = true, 
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = TransferRequestDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Пример перевода",
                                    value = "{\"sendCardId\": \"123e4567-e89b-12d3-a456-426614174000\", " +
                                            "\"receiveCardId\": \"987e6543-e21b-32d3-c456-556614174111\", " +
                                            "\"amount\": 100.50}", 
                                    summary = "Перевод 100.50 с карты на карту" 
                            )
                    }
            )
    )
    @PreAuthorize("isAuthenticated()") 
    public ResponseEntity<Void> transferBetweenCards(@RequestBody @Valid @NotNull(message = "Request body cannot be null") TransferRequestDto transferRequestDto,
                                                     Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"); 
        }
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
            description = "Списывает указанную сумму с баланса карты. Карта должна принадлежать аутентифицированному пользователю.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Средства успешно списаны (тело ответа пустое)",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (например, ID карты не указан, сумма не положительная)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Debit amount must be positive"))), 
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "403", description = "Операция запрещена (пользователь не владелец карты)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Forbidden: You are not owner of this card"))), 
            @ApiResponse(responseCode = "404", description = "Карта или пользователь не найдены",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: Card not found"))), 
            @ApiResponse(responseCode = "422", description = "Операция не может быть обработана (карта просрочена, недостаточно средств, превышен лимит)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unprocessable Entity: The transaction amount exceeds the daily limit"))), 
            @ApiResponse(responseCode = "423", description = "Карта заблокирована",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Locked: The card is blocked"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Данные для списания средств: ID карты и сумма.",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = DebitRequestDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Пример списания",
                                    value = "{\"cardId\": \"123e4567-e89b-12d3-a456-426614174000\", \"amount\": 50.75}",
                                    summary = "Списание 50.75 с указанной карты"
                            )
                    }
            )
    )
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> debitFromCard(@RequestBody @Valid @NotNull(message = "Request body cannot be null") DebitRequestDto debitRequestDto,
                                              Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
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
            description = "Пополняет указанную карту на заданную сумму. Карта должна принадлежать аутентифицированному пользователю.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Карта успешно пополнена (тело ответа пустое)",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (например, ID карты не указан, сумма не положительная)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Top-up amount must be positive"))), 
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "403", description = "Операция запрещена (пользователь не владелец карты)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Forbidden: You are not owner of this card"))), 
            @ApiResponse(responseCode = "404", description = "Карта или пользователь не найдены",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: Card not found"))), 
            @ApiResponse(responseCode = "422", description = "Операция не может быть обработана (карта просрочена)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unprocessable Entity: The receiving card has expired"))), 
            @ApiResponse(responseCode = "423", description = "Карта заблокирована",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Locked: The receiving card is blocked"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Данные для пополнения карты: ID карты и сумма.",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = TopUpRequestDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Пример пополнения",
                                    value = "{\"cardId\": \"123e4567-e89b-12d3-a456-426614174000\", \"amount\": 150.00}",
                                    summary = "Пополнение карты на сумму 150"
                            )
                    }
            )
    )
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> topUpCard(@RequestBody @Valid @NotNull(message = "Request body cannot be null") TopUpRequestDto topUpRequestDto,
                                          Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        String email = authentication.getName();
        transactionService.topUpCard(
                topUpRequestDto.getCardId(),
                topUpRequestDto.getAmount(),
                email
        );
        return ResponseEntity.ok().build();
    }

    @GetMapping("/my-transactions")
    @Operation(summary = "Получение транзакций текущего пользователя",
            description = "Возвращает постраничный список транзакций, где участвуют карты аутентифицированного пользователя. Доступна фильтрация по различным параметрам.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список транзакций успешно получен (может быть пустым)",
                    content = @Content(mediaType = "application/json", 
                            schema = @Schema(type = "array", implementation = TransactionDto.class))), 
            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса (пагинация, сортировка, формат даты)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Invalid sort parameter"))), 
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "404", description = "Пользователь (из аутентификации) не найден",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: User not found"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @Parameters({
            
            @Parameter(name = "type", description = "Фильтр по типу транзакции (CREDIT, DEBIT, TRANSFER) (опционально).",
                    required = false, schema = @Schema(implementation = TransactionType.class)),
            @Parameter(name = "amountFrom", description = "Фильтр по минимальной сумме транзакции (включительно) (опционально).",
                    required = false, schema = @Schema(type = "number", format = "double", example = "10.00")),
            @Parameter(name = "amountTo", description = "Фильтр по максимальной сумме транзакции (включительно) (опционально).",
                    required = false, schema = @Schema(type = "number", format = "double", example = "1000.00")),
            @Parameter(name = "createdAtFrom", description = "Фильтр по начальной дате и времени создания (формат ISO: yyyy-MM-dd'T'HH:mm:ss) (опционально).",
                    required = false, schema = @Schema(type = "string", format = "date-time", example = "2023-01-01T00:00:00")),
            @Parameter(name = "createdAtTo", description = "Фильтр по конечной дате и времени создания (формат ISO: yyyy-MM-dd'T'HH:mm:ss) (опционально).",
                    required = false, schema = @Schema(type = "string", format = "date-time", example = "2023-12-31T23:59:59")),
            @Parameter(name = "cardId", description = "Фильтр по ID карты, участвующей в транзакции (как отправитель или получатель) (опционально).",
                    required = false, schema = @Schema(implementation = UUID.class, example = "123e4567-e89b-12d3-a456-426614174000")),
            @Parameter(name = "page", description = "Номер страницы (начиная с 0)", example = "0",
                    required = false, schema = @Schema(type = "integer", defaultValue = "0")),
            @Parameter(name = "size", description = "Количество элементов на странице", example = "10",
                    required = false, schema = @Schema(type = "integer", defaultValue = "10")),
            @Parameter(name = "sort", description = "Параметры сортировки (поле,направление). Доступные поля: createdAt, amount, type. По умолчанию 'createdAt,desc'.",
                    example = "createdAt,desc", required = false, schema = @Schema(type = "string", defaultValue = "createdAt,desc"))
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TransactionDto>> getCurrentUserTransactions(
            Authentication authentication,
            
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) BigDecimal amountFrom,
            @RequestParam(required = false) BigDecimal amountTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdAtTo,
            @RequestParam(required = false) UUID cardId,
            
            @RequestParam(defaultValue = "0") @PositiveOrZero(message = "page must be greater than or equal to zero") int page,
            @RequestParam(defaultValue = "10") @PositiveOrZero(message = "size must be greater than or equal to zero") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        String userEmail = authentication.getName();

        try {
            
            String[] sortParams = sort.split(",");
            String sortField = sortParams[0];
            List<String> allowedSortFields = List.of("createdAt", "amount", "type"); 
            if (!allowedSortFields.contains(sortField)) {
                throw new IllegalArgumentException("Invalid sort field: " + sortField);
            }
            Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc") 
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC; 
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

            
            TransactionFilter filter = new TransactionFilter();
            filter.setType(type);
            filter.setAmountFrom(amountFrom);
            filter.setAmountTo(amountTo);
            filter.setCreatedAtFrom(createdAtFrom);
            filter.setCreatedAtTo(createdAtTo);
            filter.setCardId(cardId);

            
            List<TransactionDto> transactions = transactionService.getTransactions(userEmail, filter, pageable);

            return ResponseEntity.ok(transactions);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            log.error("Invalid request parameters for user {}: sort='{}', page={}, size={}. Error: {}", userEmail, sort, page, size, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort or pagination parameter: " + e.getMessage()); 
        }
        
    }

    @GetMapping("/transactions-by-user")
    @Operation(summary = "Получение транзакций по пользователю (Админ)",
            description = "Возвращает постраничный список транзакций для всех пользователей (если email не указан) или для конкретного пользователя (если email указан). Доступна фильтрация. Требуются права Администратора.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список транзакций успешно получен (может быть пустым)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(type = "array", implementation = TransactionDto.class))),
            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса (пагинация, сортировка, формат даты)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Invalid sort parameter"))), 
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "403", description = "Доступ запрещен (недостаточно прав)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Forbidden"))), 
            @ApiResponse(responseCode = "404", description = "Пользователь с указанным email не найден (если email был указан в фильтре)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: User not found"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @Parameters({
            
            @Parameter(name = "userEmail", description = "Фильтр по email пользователя (опционально). Если не указан, возвращаются транзакции всех пользователей.",
                    required = false, schema = @Schema(type = "string", example = "user@example.com")),
            @Parameter(name = "type", description = "Фильтр по типу транзакции (CREDIT, DEBIT, TRANSFER) (опционально).",
                    required = false, schema = @Schema(implementation = TransactionType.class)),
            @Parameter(name = "amountFrom", description = "Фильтр по минимальной сумме транзакции (включительно) (опционально).",
                    required = false, schema = @Schema(type = "number", format = "double", example = "10.00")),
            @Parameter(name = "amountTo", description = "Фильтр по максимальной сумме транзакции (включительно) (опционально).",
                    required = false, schema = @Schema(type = "number", format = "double", example = "1000.00")),
            @Parameter(name = "createdAtFrom", description = "Фильтр по начальной дате и времени создания (формат ISO: yyyy-MM-dd'T'HH:mm:ss) (опционально).",
                    required = false, schema = @Schema(type = "string", format = "date-time", example = "2023-01-01T00:00:00")),
            @Parameter(name = "createdAtTo", description = "Фильтр по конечной дате и времени создания (формат ISO: yyyy-MM-dd'T'HH:mm:ss) (опционально).",
                    required = false, schema = @Schema(type = "string", format = "date-time", example = "2023-12-31T23:59:59")),
            @Parameter(name = "cardId", description = "Фильтр по ID карты, участвующей в транзакции (как отправитель или получатель) (опционально).",
                    required = false, schema = @Schema(implementation = UUID.class, example = "123e4567-e89b-12d3-a456-426614174000")),
            @Parameter(name = "page", description = "Номер страницы (начиная с 0)", example = "0",
                    required = false, schema = @Schema(type = "integer", defaultValue = "0")),
            @Parameter(name = "size", description = "Количество элементов на странице", example = "10",
                    required = false, schema = @Schema(type = "integer", defaultValue = "10")),
            @Parameter(name = "sort", description = "Параметры сортировки (поле,направление). Доступные поля: createdAt, amount, type. По умолчанию 'createdAt,desc'.",
                    example = "createdAt,desc", required = false, schema = @Schema(type = "string", defaultValue = "createdAt,desc"))
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')") 
    public ResponseEntity<List<TransactionDto>> getTransactionsByUser(
            
            @RequestParam(required = false) String userEmail, 
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) BigDecimal amountFrom,
            @RequestParam(required = false) BigDecimal amountTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdAtTo,
            @RequestParam(required = false) UUID cardId,
            
            @RequestParam(defaultValue = "0") @PositiveOrZero(message = "page must be greater than or equal to zero") int page,
            @RequestParam(defaultValue = "10") @PositiveOrZero(message = "size must be greater than or equal to zero") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        try {
            
            String[] sortParams = sort.split(",");
            String sortField = sortParams[0];
            List<String> allowedSortFields = List.of("createdAt", "amount", "type");
            if (!allowedSortFields.contains(sortField)) {
                throw new IllegalArgumentException("Invalid sort field: " + sortField);
            }
            Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc")
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

            
            TransactionFilter filter = new TransactionFilter();
            filter.setType(type);
            filter.setAmountFrom(amountFrom);
            filter.setAmountTo(amountTo);
            filter.setCreatedAtFrom(createdAtFrom);
            filter.setCreatedAtTo(createdAtTo);
            filter.setCardId(cardId);

            
            List<TransactionDto> transactions = transactionService.getTransactions(userEmail, filter, pageable);

            return ResponseEntity.ok(transactions);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            log.error("Invalid request parameters for admin transaction search: userEmail='{}', sort='{}', page={}, size={}. Error: {}", userEmail, sort, page, size, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort or pagination parameter: " + e.getMessage()); 
        }
        
    }
}
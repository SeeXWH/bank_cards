package com.example.bank_cards.controller;

import com.example.bank_cards.dto.BlockCardRequestDto;
import com.example.bank_cards.enums.RequestStatus;
import com.example.bank_cards.enums.RequestType;
import com.example.bank_cards.model.CardRequest;
import com.example.bank_cards.serviceInterface.CardRequestServiceImpl;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/card-requests")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "CardRequestController", description = "Контроллер для управления запросами (заявками) по картам")
public class CardRequestController {

    private final CardRequestServiceImpl cardRequestService;

    @PostMapping("/create-card")
    @Operation(summary = "Запрос на создание новой карты",
            description = "Создает запрос на выпуск новой карты для текущего аутентифицированного пользователя. Запрос будет рассмотрен администратором.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Запрос на создание карты успешно создан (тело ответа пустое)",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "404", description = "Пользователь (из аутентификации) не найден в системе",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: User not found"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> createCardRequest(Authentication authentication) {
        String email = authentication.getName();
        cardRequestService.createRequestToCreateCard(email); 
        return ResponseEntity.ok().build();
    }

    @PostMapping("/block-card")
    @Operation(summary = "Запрос на блокировку карты",
            description = "Создает запрос на блокировку карты по ее номеру. Запрос может создать только владелец карты. Запрос будет рассмотрен администратором.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Запрос на блокировку успешно создан (тело ответа пустое)",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (например, не указан номер карты в теле)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "cardNumber cannot be blank"))), 
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "403", description = "Доступ запрещен (попытка заблокировать чужую карту)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Forbidden: You do not have permission to block this card."))), 
            @ApiResponse(responseCode = "404", description = "Карта с указанным номером или пользователь не найдены",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: Card not found"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Номер карты, которую необходимо заблокировать.",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = BlockCardRequestDto.class),
                    examples = {
                            @ExampleObject(
                                    name = "Пример запроса на блокировку",
                                    value = "{\"cardNumber\": \"1111222233334444\"}",
                                    summary = "Блокировка карты 1111222233334444"
                            )
                    }
            )
    )
    @PreAuthorize("isAuthenticated()") 
    public ResponseEntity<Void> blockCardRequest(
            Authentication authentication,
            
            @RequestBody @Valid @NotNull(message = "Request body cannot be null") BlockCardRequestDto blockCardRequestDto) {
        String email = authentication.getName();
        cardRequestService.createRequestToBlockCard(email, blockCardRequestDto);
        return ResponseEntity.ok().build();
    }


    @PutMapping("/set-status")
    @Operation(summary = "Изменение статуса запроса (Админ)",
            description = "Обновляет статус указанного запроса (на APPROVED и REJECTED). Доступно только Администраторам.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Статус запроса успешно обновлен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CardRequest.class))),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (не указан ID запроса или статус, неверный формат UUID)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "requestId cannot be null"))), 
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "403", description = "Доступ запрещен (недостаточно прав)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Forbidden"))), 
            @ApiResponse(responseCode = "404", description = "Запрос с указанным ID не найден",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: Request not found"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @Parameters({
            
            @Parameter(name = "requestId", description = "UUID запроса, статус которого нужно обновить", required = true,
                    example = "d290f1ee-6c54-4b01-90e6-d701748f0851", schema = @Schema(implementation = UUID.class)),
            @Parameter(name = "requestStatus", description = "Новый статус для запроса", required = true,
                    schema = @Schema(implementation = RequestStatus.class, example = "APPROVED"))
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<CardRequest> updateRequestStatus(
            @RequestParam @NotNull(message = "requestId cannot be null") UUID requestId,
            @RequestParam @NotNull(message = "requestStatus cannot be null") RequestStatus requestStatus) {
        CardRequest cardRequest = cardRequestService.setRequestStatus(requestId, requestStatus);
        return ResponseEntity.ok(cardRequest);
    }


    @GetMapping("/card-requests-by-user")
    @Operation(summary = "Получение заявок по email пользователя (Админ)",
            description = "Возвращает постраничный список заявок для указанного пользователя с возможностью фильтрации по типу, статусу и дате создания. Доступно только Администраторам.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список заявок успешно получен (может быть пустым)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(type = "array", implementation = CardRequest.class))),
            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса (формат даты, пагинация, сортировка)",
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
            
            @Parameter(name = "email", description = "Email пользователя"),
            @Parameter(name = "type", description = "Фильтр по типу заявки"),
            @Parameter(name = "status", description = "Фильтр по статусу заявки"),
            @Parameter(name = "from", description = "Начальная дата (ISO формат)", example = "2025-01-01T00:00:00"),
            @Parameter(name = "to", description = "Конечная дата (ISO формат)", example = "2025-12-31T23:59:59"),
            @Parameter(name = "page", description = "Номер страницы", example = "0"),
            @Parameter(name = "size", description = "Размер страницы", example = "10"),
            @Parameter(name = "sort", description = "Сортировка (поле,направление)", example = "createdAt,desc")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<CardRequest>> getCardRequestsByUser(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) RequestType type,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") @PositiveOrZero(message = "page cannot be negative") int page,
            @RequestParam(defaultValue = "10") @PositiveOrZero(message = "size cannot be negative") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        try {
            
            String[] sortParams = sort.split(",");
            Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc") 
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));
            List<CardRequest> requests = cardRequestService.getCardRequestsWithFilter(
                    email, type, status, from, to, pageable
            );
            return ResponseEntity.ok(requests);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) { 
            log.error("Invalid sort parameter: {}", sort, e);
            
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort parameter"); 
        }
        
    }

    @GetMapping("/my-card-requests")
    @Operation(summary = "Получение заявок текущего пользователя",
            description = "Возвращает постраничный список заявок, созданных текущим аутентифицированным пользователем, с возможностью фильтрации по типу, статусу и дате создания.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список заявок успешно получен (может быть пустым)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(type = "array", implementation = CardRequest.class))),
            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса (формат даты, пагинация, сортировка)",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Invalid sort parameter"))), 
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Unauthorized"))), 
            @ApiResponse(responseCode = "404", description = "Пользователь (из аутентификации) не найден в системе",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Not Found: User not found"))), 
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = "Internal Server Error"))) 
    })
    @Parameters({
            
            @Parameter(name = "type", description = "Фильтр по типу заявки"),
            @Parameter(name = "status", description = "Фильтр по статусу заявки"),
            @Parameter(name = "from", description = "Начальная дата (ISO формат)", example = "2025-01-01T00:00:00"),
            @Parameter(name = "to", description = "Конечная дата (ISO формат)", example = "2025-12-31T23:59:59"),
            @Parameter(name = "page", description = "Номер страницы", example = "0"),
            @Parameter(name = "size", description = "Размер страницы", example = "10"),
            @Parameter(name = "sort", description = "Сортировка (поле,направление)", example = "createdAt,desc")
    })
    @PreAuthorize("isAuthenticated()") 
    public ResponseEntity<List<CardRequest>> getCurrentUserCardRequests(
            Authentication authentication,
            @RequestParam(required = false) RequestType type,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") @PositiveOrZero(message = "page cannot be negative") int page,
            @RequestParam(defaultValue = "10") @PositiveOrZero(message = "size cannot be negative") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"); 
        }
        String email = authentication.getName();

        try {
            
            String[] sortParams = sort.split(",");
            Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc") 
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));

            List<CardRequest> requests = cardRequestService.getCardRequestsWithFilter(
                    email, type, status, from, to, pageable
            );
            return ResponseEntity.ok(requests);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) { 
            log.error("Invalid sort parameter for user {}: {}", email, sort, e);
            
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort parameter"); 
        }
        
    }
}
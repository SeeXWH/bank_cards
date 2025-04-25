package com.example.bank_cards.controller;

import com.example.bank_cards.dto.BlockCardRequestDto;
import com.example.bank_cards.enums.RequestStatus;
import com.example.bank_cards.enums.RequestType;
import com.example.bank_cards.model.CardRequest;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
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
    public ResponseEntity<CardRequest> updateRequestStatus(
            Authentication authentication,
            @Parameter(description = "ID запроса", required = true)
            @RequestParam UUID requestId,
            @RequestParam RequestStatus requestStatus) {

        String email = authentication.getName();
        log.info("Updating request status for user: {}, requestId: {}, new status: {}",
                email, requestId, requestStatus);

        CardRequest cardRequest = cardRequestService.setRequestStatus(requestId, requestStatus);
        return ResponseEntity.ok(cardRequest);
    }


    @GetMapping("/card-requests-by-user")
    @Operation(summary = "Получение заявок на карты по email пользователя",
            description = "Возвращает список заявок для указанного пользователя с возможностью фильтрации по типу, статусу и дате")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список заявок успешно получен"),
            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещен"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        try {
            String[] sortParams = sort.split(",");
            Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc")
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));

            log.info("Request card requests for user: {}, type: {}, status: {}, range: {} - {}, page: {}, size: {}, sort: {}",
                    email, type, status, from, to, page, size, sort);

            List<CardRequest> requests = cardRequestService.getCardRequestsWithFilter(
                    email, type, status, from, to, pageable
            );
            return ResponseEntity.ok(requests);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid sort parameter: {}", sort);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort parameter");
        }
    }

    @GetMapping("/my-card-requests")
    @Operation(summary = "Получение заявок текущего пользователя",
            description = "Возвращает список заявок текущего пользователя с возможностью фильтрации по типу, статусу и дате")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список заявок успешно получен"),
            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса"),
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
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
    public ResponseEntity<List<CardRequest>> getCurrentUserCardRequests(
            Authentication authentication,
            @RequestParam(required = false) RequestType type,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        try {
            String email = authentication.getName();
            String[] sortParams = sort.split(",");
            Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc")
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));

            log.info("Fetching card requests for current user: {}, type: {}, status: {}, range: {} - {}, page: {}, size: {}, sort: {}",
                    email, type, status, from, to, page, size, sort);

            List<CardRequest> requests = cardRequestService.getCardRequestsWithFilter(
                    email, type, status, from, to, pageable
            );
            return ResponseEntity.ok(requests);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid sort parameter: {}", sort);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort parameter");
        }
    }



}

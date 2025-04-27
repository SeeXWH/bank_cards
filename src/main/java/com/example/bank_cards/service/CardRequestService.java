package com.example.bank_cards.service;

import com.example.bank_cards.dto.BlockCardRequestDto;
import com.example.bank_cards.enums.RequestStatus;
import com.example.bank_cards.enums.RequestType;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.model.CardRequest;
import com.example.bank_cards.repository.CardRequestRepository;
import com.example.bank_cards.serviceInterface.CardRequestServiceImpl;
import com.example.bank_cards.serviceInterface.CardServiceImpl;
import com.example.bank_cards.serviceInterface.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Сервис для управления запросами (заявками), связанными с картами.
 * Предоставляет функциональность для создания запросов на выпуск или блокировку карты,
 * обновления статуса запросов и получения списка запросов с фильтрацией.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class CardRequestService implements CardRequestServiceImpl {

    private final CardRequestRepository cardRequestRepository;
    private final UserServiceImpl userService;
    private final CardServiceImpl cardService;

    /**
     * Создает запрос на выпуск новой банковской карты для пользователя.
     * Пользователь определяется по email. Запрос сохраняется со статусом PENDING.
     *
     * @param email Email пользователя, для которого создается запрос.
     * @throws ResponseStatusException если пользователь с указанным email не найден.
     */
    @Override
    @Transactional
    public void createRequestToCreateCard(String email) { 
        AppUser user = userService.getUserByEmail(email);
        log.info("Creating card creation request for user: {}", user.getEmail());
        CardRequest cardRequest = new CardRequest();
        cardRequest.setType(RequestType.CREATE_CARD);
        cardRequest.setOwner(user);
        cardRequest.setStatus(RequestStatus.PENDING);
        cardRequestRepository.save(cardRequest);
        log.debug("Card creation request saved for user: {}", user.getEmail());
    }
    
    /**
     * Создает запрос на блокировку существующей банковской карты.
     * Проверяет, принадлежит ли карта пользователю, инициирующему запрос.
     * Запрос сохраняется со статусом PENDING.
     *
     * @param email              Email пользователя, инициирующего запрос.
     * @param blockCardRequestDto DTO с номером карты, которую нужно заблокировать.
     * @throws ResponseStatusException если пользователь с указанным email не найден.
     * @throws ResponseStatusException если карта с указанным номером не найдена.
     * @throws ResponseStatusException если пользователь пытается заблокировать карту, которая ему не принадлежит (FORBIDDEN).
     */
    @Override
    @Transactional
    public void createRequestToBlockCard(String email, BlockCardRequestDto blockCardRequestDto) {
        AppUser user = userService.getUserByEmail(email);
        Card card = cardService.findCardByNumber(blockCardRequestDto.getCardNumber());
        if (!Objects.equals(card.getOwner().getId(), user.getId())) {
            log.warn("User {} tried to block card {} not owned by them", user.getEmail(), blockCardRequestDto.getCardNumber()); 
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to block this card." 
            );
        }
        log.info("Creating block card request for card: {} by user: {}", card.getId(), user.getEmail());
        CardRequest cardRequest = new CardRequest();
        cardRequest.setCardId(card.getId());
        cardRequest.setType(RequestType.BLOCK_CARD);
        cardRequest.setOwner(user);
        cardRequest.setStatus(RequestStatus.PENDING);
        cardRequestRepository.save(cardRequest);
        log.debug("Block card request saved for card: {}", card.getId());
    }

    /**
     * Устанавливает новый статус для существующего запроса.
     *
     * @param requestId     UUID запроса, статус которого нужно обновить.
     * @param requestStatus Новый статус запроса (APPROVED и REJECTED).
     * @return Обновленный объект CardRequest.
     * @throws ResponseStatusException если запрос с указанным UUID не найден (NOT_FOUND).
     */
    @Override
    @Transactional
    public CardRequest setRequestStatus(UUID requestId, RequestStatus requestStatus) {
        CardRequest cardRequest = cardRequestRepository.findById(requestId)
                .orElseThrow(() -> {
                    log.warn("Card request not found: {}", requestId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"); 
                });
        log.info("Updating status of card request {} to {}", requestId, requestStatus);
        cardRequest.setStatus(requestStatus);
        CardRequest updatedRequest = cardRequestRepository.save(cardRequest);
        log.debug("Card request {} status updated to {}", requestId, requestStatus);
        return updatedRequest;
    }

    /**
     * Возвращает список запросов с возможностью фильтрации и пагинацией.
     * Позволяет фильтровать по email пользователя, типу запроса, статусу запроса и диапазону дат создания.
     *
     * @param userEmail     Email пользователя для фильтрации (опционально). Если указан, пользователь должен существовать.
     * @param typeFilter    Тип запроса для фильтрации (опционально).
     * @param statusFilter  Статус запроса для фильтрации (опционально).
     * @param from          Начальная дата и время создания запроса для фильтрации (опционально).
     * @param to            Конечная дата и время создания запроса для фильтрации (опционально).
     * @param pageable      Параметры пагинации и сортировки.
     * @return Список {@link CardRequest}, удовлетворяющих критериям фильтрации, или пустой список.
     * @throws ResponseStatusException если указан `userEmail`, но пользователь с таким email не найден.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CardRequest> getCardRequestsWithFilter(
            String userEmail,
            RequestType typeFilter,
            RequestStatus statusFilter,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {
        log.info("Fetching card requests. userEmail: {}, type: {}, status: {}, from: {}, to: {}",
                userEmail, typeFilter, statusFilter, from, to);
        Specification<CardRequest> spec = Specification.where(null);
        if (StringUtils.hasText(userEmail)) {
            userService.getUserByEmail(userEmail);
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("owner").get("email"), userEmail));
            log.debug("Added email filter: {}", userEmail);
        }
        if (typeFilter != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("type"), typeFilter));
            log.debug("Added type filter: {}", typeFilter);
        }
        if (statusFilter != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("status"), statusFilter));
            log.debug("Added status filter: {}", statusFilter);
        }
        if (from != null && to != null) {
            spec = spec.and((root, query, cb) ->
                    cb.between(root.get("createdAt"), from, to));
            log.debug("Added date range filter: {} to {}", from, to);
        } else if (from != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            log.debug("Added start date filter: {}", from);
        } else if (to != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("createdAt"), to));
            log.debug("Added end date filter: {}", to);
        }
        Page<CardRequest> page = cardRequestRepository.findAll(spec, pageable);
        if (page.isEmpty()) {
            log.info("No card requests found for given parameters");
            return Collections.emptyList();
        }
        log.debug("Found {} card requests", page.getTotalElements());
        return page.getContent().stream().toList();
    }
}
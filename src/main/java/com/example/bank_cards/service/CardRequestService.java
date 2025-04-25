package com.example.bank_cards.service;

import com.example.bank_cards.dto.BlockCardRequestDto;
import com.example.bank_cards.enums.RequestStatus;
import com.example.bank_cards.enums.RequestType;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.model.CardRequest;
import com.example.bank_cards.repository.CardRequestRepository;
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

@Service
@RequiredArgsConstructor
@Log4j2
public class CardRequestService {

    private final CardRequestRepository cardRequestRepository;
    private final UserService userService;
    private final CardService cardService;

    @Transactional()
    public void crateRequestToCreateCard(String email) {
        AppUser user = userService.getUserByEmail(email);
        CardRequest cardRequest = new CardRequest();
        cardRequest.setType(RequestType.CREATE_CARD);
        cardRequest.setOwner(user);
        cardRequest.setStatus(RequestStatus.PENDING);
        cardRequestRepository.save(cardRequest);
    }

    @Transactional()
    public void createRequestToBlockCard(String email, BlockCardRequestDto blockCardRequestDto) {
        if (!StringUtils.hasText(blockCardRequestDto.getCardNumber())) {
            log.warn("Request failed: card number is blank.");
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "card number cannot be null or empty"
            );
        }

        AppUser user = userService.getUserByEmail(email);
        Card card = cardService.findCardByNumber(blockCardRequestDto.getCardNumber());
        if (!Objects.equals(card.getOwner().getId(), user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to block this card."
            );
        }

        CardRequest cardRequest = new CardRequest();
        cardRequest.setCardId(card.getId());
        cardRequest.setType(RequestType.BLOCK_CARD);
        cardRequest.setOwner(user);
        cardRequest.setStatus(RequestStatus.PENDING);
        cardRequestRepository.save(cardRequest);
    }

    @Transactional
    public CardRequest setRequestStatus(UUID requestId, RequestStatus requestStatus) {
        if (requestId == null || requestStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id or status cannot be null or empty");
        }
        CardRequest cardRequest = cardRequestRepository.findById(requestId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "request not found"));
        cardRequest.setStatus(requestStatus);
        return cardRequestRepository.save(cardRequest);
    }

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

        Specification<CardRequest> spec = Specification.where(null); // стартуем с пустого фильтра

        if (StringUtils.hasText(userEmail)) {
            userService.getUserByEmail(userEmail); // валидация, что такой пользователь есть
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("owner").get("email"), userEmail));
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

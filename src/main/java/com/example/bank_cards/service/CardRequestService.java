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

    @Transactional
    public void crateRequestToCreateCard(String email) {
        if (!StringUtils.hasText(email)) {
            log.warn("Attempt to create card request with empty email");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email cannot be empty");
        }
        AppUser user = userService.getUserByEmail(email);
        log.info("Creating card creation request for user: {}", user.getEmail());
        CardRequest cardRequest = new CardRequest();
        cardRequest.setType(RequestType.CREATE_CARD);
        cardRequest.setOwner(user);
        cardRequest.setStatus(RequestStatus.PENDING);
        cardRequestRepository.save(cardRequest);
        log.debug("Card creation request saved for user: {}", user.getEmail());
    }

    @Transactional
    public void createRequestToBlockCard(String email, BlockCardRequestDto blockCardRequestDto) {
        if (!StringUtils.hasText(blockCardRequestDto.getCardNumber())) {
            log.warn("Attempt to create block card request with empty card number");
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "card number cannot be null or empty"
            );
        }
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

    @Transactional
    public CardRequest setRequestStatus(UUID requestId, RequestStatus requestStatus) {
        if (requestId == null || requestStatus == null) {
            log.warn("Attempt to update request status with null id or status");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id or status cannot be null or empty");
        }
        CardRequest cardRequest = cardRequestRepository.findById(requestId)
                .orElseThrow(() -> {
                    log.warn("Card request not found: {}", requestId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "request not found");
                });
        log.info("Updating status of card request {} to {}", requestId, requestStatus);
        cardRequest.setStatus(requestStatus);
        CardRequest updatedRequest = cardRequestRepository.save(cardRequest);
        log.debug("Card request {} status updated to {}", requestId, requestStatus);
        return updatedRequest;
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

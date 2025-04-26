package com.example.bank_cards.service;

import com.example.bank_cards.dto.CardCreateDto;
import com.example.bank_cards.dto.CardDto;
import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.dto.CardLimitDto;
import com.example.bank_cards.repository.CardRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Log4j2
public class CardService {
    private final Random random = new Random();
    private final CardRepository cardRepository;
    private final UserService userService;
    private final CardEncryptionService cardEncryptionService;

    @Transactional
    public CardDto createCard(CardCreateDto cardCreateDto) {
        validateCardCreationRequest(cardCreateDto);
        AppUser owner = userService.getUserByEmail(cardCreateDto.getEmail());
        Card card = buildNewCard(owner, cardCreateDto.getExpiryDate());
        cardRepository.save(card);
        return buildNewCardDto(card);
    }

    @Transactional
    public CardDto setCardStatus(UUID id, CardStatus cardStatus) {
        if (id == null || cardStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id or status cannot be null or empty");
        }
        if (cardStatus == CardStatus.EXPIRED){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot manually set status to EXPIRED");
        }
        Card card = cardRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
        card.setStatus(cardStatus);
        cardRepository.save(card);
        return buildNewCardDto(card);
    }

    private void validateCardCreationRequest(CardCreateDto dto) {
        if (dto == null) {
            log.warn("Card creation failed: request is null");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request cannot be null");
        }

        if (!StringUtils.hasText(dto.getEmail())) {
            log.warn("Card creation failed: email is blank");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email cannot be empty");
        }

        if (dto.getExpiryDate() == null) {
            log.warn("Card creation failed: expiry date is null");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry date cannot be null");
        }

        if (dto.getExpiryDate().isBefore(LocalDate.now())) {
            log.warn("Card creation failed: expiry date {} is in the past", dto.getExpiryDate());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry date cannot be in the past");
        }
    }

    @Transactional(readOnly = true)
    public List<CardDto> getCardsByUserEmail(String userEmail,
                                             CardStatus statusFilter,
                                             Pageable pageable) {
        if (!StringUtils.hasText(userEmail)) {
            log.warn("Attempt to get cards with empty user email");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User email cannot be empty");
        }

        log.info("Fetching cards for user: {}, status filter: {}", userEmail, statusFilter);
        userService.getUserByEmail(userEmail);

        Specification<Card> spec = (root, query, cb) ->
                cb.equal(root.get("owner").get("email"), userEmail);
        if (statusFilter != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("status"), statusFilter));
            log.debug("Added status filter: {}", statusFilter);
        }
        Page<Card> cardsPage = cardRepository.findAll(spec, pageable);
        if (cardsPage.isEmpty()) {
            log.info("No cards found for user: {}", userEmail);
            return Collections.emptyList();
        }
        log.debug("Found {} cards for user: {}", cardsPage.getTotalElements(), userEmail);
        return cardsPage.getContent().stream()
                .map(this::buildNewCardDto)
                .toList();
    }

    @Transactional
    public void deleteCard(UUID id) {
        if (id == null) {
            log.warn("Attempt to delete card with null id");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id cannot be null or empty");
        }
        Card card = cardRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
        cardRepository.delete(card);
    }

    @Transactional
    public CardDto setCardLimit(UUID id, CardLimitDto cardLimit) {
        if (id == null || cardLimit == null) {
            log.warn("Attempt to set limit with null id");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id or limit cannot be null or empty");
        }
        Card card = cardRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
        if (cardLimit.getDailyLimit() != null) {
            card.setDailyLimit(cardLimit.getDailyLimit());
        }
        if (cardLimit.getMonthlyLimit() != null) {
            card.setMonthlyLimit(cardLimit.getMonthlyLimit());
        }
        cardRepository.save(card);
        return buildNewCardDto(card);
    }

    @Transactional
    public String getCardNumber(UUID id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id cannot be null or empty");
        }
        Card card = cardRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
        String cryptNumber = card.getCardNumber();
        return cardEncryptionService.decryptCardNumber(cryptNumber);
    }
    @Transactional(readOnly = true)
    public Card findCardByNumber(String cardNumber) {
        if (!StringUtils.hasText(cardNumber)) {
            log.warn("Request failed: card number is blank.");
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "card number cannot be null or empty"
            );
        }
        String cryptNumber = cardEncryptionService.encryptCardNumber(cardNumber);
        return cardRepository.findByCardNumber(cryptNumber).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
    }

    @Transactional(readOnly = true)
    public Card findCardById(UUID id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id cannot be null or empty");
        }
        return cardRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
    }
    @Transactional
    public void updateCard(Card card){
        if (card == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "card cannot be null or empty");
        }
        if (!cardRepository.existsById(card.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found");
        }
        cardRepository.save(card);
    }


    private Card buildNewCard(AppUser owner, LocalDate expiryDate) {
        Card card = new Card();
        String number = generateUniqueCardNumber();
        String cryptNumber = cardEncryptionService.encryptCardNumber(number);
        card.setCardNumber(cryptNumber);
        card.setExpiryDate(expiryDate);
        card.setOwner(owner);
        card.setBalance(BigDecimal.ZERO);
        card.setStatus(CardStatus.ACTIVE);
        return card;
    }

    private CardDto buildNewCardDto(Card card) {
        String cardNumber = cardEncryptionService.maskCardNumber(card.getCardNumber());
        CardDto cardDto = new CardDto();
        cardDto.setId(card.getId());
        cardDto.setCardNumber(cardNumber);
        cardDto.setExpiryDate(card.getExpiryDate());
        cardDto.setDailyLimit(card.getDailyLimit());
        cardDto.setMonthlyLimit(card.getMonthlyLimit());
        cardDto.setStatus(card.getStatus());
        cardDto.setBalance(card.getBalance());
        cardDto.setOwnerName(card.getOwner().getName());
        return cardDto;
    }


    public String generateUniqueCardNumber() {
        String cardNumber;
        do {
            cardNumber = generateCardNumber();
        } while (cardRepository.existsByCardNumber(cardNumber));

        return cardNumber;
    }

    private String generateCardNumber() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(String.format("%04d", random.nextInt(10000)));
        }
        return sb.toString();
    }


}

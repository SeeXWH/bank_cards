package com.example.bank_cards.service;

import com.example.bank_cards.dto.CardCreateDto;
import com.example.bank_cards.dto.CardDto;
import com.example.bank_cards.dto.CardLimitDto;
import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.repository.CardRepository;
import com.example.bank_cards.serviceInterface.CardEncryptionServiceImpl;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Log4j2
public class CardService implements CardServiceImpl {

    private final Random random = new Random();
    private final CardRepository cardRepository;
    private final UserServiceImpl userService;
    private final CardEncryptionServiceImpl cardEncryptionService;

    @Override
    @Transactional
    public CardDto createCard(CardCreateDto dto) {
        validateCardCreationRequest(dto);
        AppUser owner = userService.getUserByEmail(dto.getEmail());
        Card card = buildNewCard(owner, dto.getExpiryDate());
        cardRepository.save(card);
        log.info("Created new card for user: {}", owner.getEmail());
        return buildCardDto(card);
    }

    @Override
    @Transactional
    public CardDto setCardStatus(UUID id, CardStatus status) {
        if (id == null || status == null) {
            log.warn("Set card status failed: id or status is null.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID and status cannot be null");
        }
        if (status == CardStatus.EXPIRED) {
            log.warn("Attempt to manually set status to EXPIRED.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot manually set status to EXPIRED");
        }
        Card card = getCardById(id);
        card.setStatus(status);
        cardRepository.save(card);
        log.info("Card status updated for card id: {}", id);
        return buildCardDto(card);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CardDto> getCardsByUserEmail(String email, CardStatus status, Pageable pageable) {
        if (!StringUtils.hasText(email)) {
            log.warn("Get cards failed: email is blank.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email cannot be empty");
        }
        userService.getUserByEmail(email);

        Specification<Card> spec = (root, query, cb) -> cb.equal(root.get("owner").get("email"), email);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        Page<Card> cardsPage = cardRepository.findAll(spec, pageable);
        if (cardsPage.isEmpty()) {
            log.info("No cards found for user: {}", email);
            return Collections.emptyList();
        }
        log.info("Found {} cards for user: {}", cardsPage.getTotalElements(), email);
        return cardsPage.map(this::buildCardDto).getContent();
    }

    @Override
    @Transactional
    public void deleteCard(UUID id) {
        Card card = getCardById(id);
        cardRepository.delete(card);
        log.info("Deleted card with id: {}", id);
    }

    @Override
    @Transactional
    public CardDto setCardLimit(UUID id, CardLimitDto limitDto) {
        if (id == null || limitDto == null) {
            log.warn("Set card limit failed: id or limit is null.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID and limit cannot be null");
        }
        Card card = getCardById(id);
        if (limitDto.getDailyLimit() != null) {
            card.setDailyLimit(limitDto.getDailyLimit());
        }
        if (limitDto.getMonthlyLimit() != null) {
            card.setMonthlyLimit(limitDto.getMonthlyLimit());
        }
        cardRepository.save(card);
        log.info("Updated limits for card id: {}", id);
        return buildCardDto(card);
    }

    @Override
    @Transactional
    public String getCardNumber(UUID id) {
        Card card = getCardById(id);
        return cardEncryptionService.decryptCardNumber(card.getCardNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public Card findCardByNumber(String cardNumber) {
        if (!StringUtils.hasText(cardNumber)) {
            log.warn("Find card by number failed: card number is blank.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card number cannot be empty");
        }
        String encryptedNumber = cardEncryptionService.encryptCardNumber(cardNumber);
        return cardRepository.findByCardNumber(encryptedNumber)
                .orElseThrow(() -> {
                    log.warn("Card not found with number: {}", cardNumber);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found");
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Card findCardById(UUID id) {
        if (id == null) {
            log.warn("Find card by id failed: id is null.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID cannot be null");
        }
        return cardRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Card not found with id: {}", id);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found");
                });
    }

    @Override
    @Transactional
    public void updateCard(Card card) {
        if (card == null) {
            log.warn("Update card failed: card is null.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card cannot be null");
        }
        if (!cardRepository.existsById(card.getId())) {
            log.warn("Update card failed: card not found with id: {}", card.getId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found");
        }
        cardRepository.save(card);
        log.info("Card updated: id {}", card.getId());
    }

    @Override
    public void validateCardCreationRequest(CardCreateDto dto) {
        if (dto == null) {
            log.warn("Card creation failed: request is null.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request cannot be null");
        }
        if (!StringUtils.hasText(dto.getEmail())) {
            log.warn("Card creation failed: email is blank.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email cannot be empty");
        }
        if (dto.getExpiryDate() == null || dto.getExpiryDate().isBefore(LocalDate.now())) {
            log.warn("Card creation failed: invalid expiry date: {}", dto.getExpiryDate());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid expiry date");
        }
    }

    @Override
    public Card getCardById(UUID id) {
        if (id == null) {
            log.warn("Get card by id failed: id is null.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID cannot be null");
        }
        return cardRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Card not found with id: {}", id);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found");
                });
    }

    @Override
    public Card buildNewCard(AppUser owner, LocalDate expiryDate) {
        String number = generateUniqueCardNumber();
        String encryptedNumber = cardEncryptionService.encryptCardNumber(number);

        Card card = new Card();
        card.setCardNumber(encryptedNumber);
        card.setExpiryDate(expiryDate);
        card.setOwner(owner);
        card.setBalance(BigDecimal.ZERO);
        card.setStatus(CardStatus.ACTIVE);
        return card;
    }

    @Override
    public CardDto buildCardDto(Card card) {
        CardDto dto = new CardDto();
        dto.setId(card.getId());
        dto.setCardNumber(cardEncryptionService.maskCardNumber(card.getCardNumber()));
        dto.setExpiryDate(card.getExpiryDate());
        dto.setDailyLimit(card.getDailyLimit());
        dto.setMonthlyLimit(card.getMonthlyLimit());
        dto.setStatus(card.getStatus());
        dto.setBalance(card.getBalance());
        dto.setOwnerName(card.getOwner().getName());
        return dto;
    }

    @Override
    public String generateUniqueCardNumber() {
        String cardNumber;
        do {
            cardNumber = generateRawCardNumber();
        } while (cardRepository.existsByCardNumber(cardEncryptionService.encryptCardNumber(cardNumber)));
        return cardNumber;
    }

    @Override
    public String generateRawCardNumber() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(String.format("%04d", random.nextInt(10000)));
        }
        return sb.toString();
    }
}

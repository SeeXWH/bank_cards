package com.example.bank_cards.service;

import com.example.bank_cards.dto.CardCreateDto;
import com.example.bank_cards.dto.CardDto;
import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.repository.CardRepository;
import com.example.bank_cards.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Log4j2
public class CardService {
    private final Random random = new Random();
    private final CardRepository cardRepository;
    private final UserService userService;


    @Transactional
    public CardDto createCard(CardCreateDto cardCreateDto) {
        validateCardCreationRequest(cardCreateDto);
        AppUser owner = userService.getUserByEmail(cardCreateDto.getEmail());
        Card card = buildNewCard(owner, cardCreateDto.getExpiryDate());
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

    private Card buildNewCard(AppUser owner, LocalDate expiryDate) {
        Card card = new Card();
        card.setCardNumber(generateUniqueCardNumber());
        card.setExpiryDate(expiryDate);
        card.setCardLimit(null);
        card.setOwner(owner);
        card.setBalance(BigDecimal.ZERO);
        card.setStatus(CardStatus.ACTIVE);
        return card;
    }

    private CardDto buildNewCardDto(Card card) {
        CardDto cardDto = new CardDto();
        cardDto.setCardNumber(card.getCardNumber());
        cardDto.setExpiryDate(card.getExpiryDate());
        cardDto.setCardLimit(card.getCardLimit());
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
        for (int i = 0; i < 5; i++) {
            sb.append(String.format("%03d", random.nextInt(1000)));
            if (i < 4) sb.append(" ");
        }
        return sb.toString();
    }
}

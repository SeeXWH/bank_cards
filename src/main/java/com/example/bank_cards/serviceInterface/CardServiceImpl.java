package com.example.bank_cards.serviceInterface;

import com.example.bank_cards.dto.CardCreateDto;
import com.example.bank_cards.dto.CardDto;
import com.example.bank_cards.dto.CardLimitDto;
import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.model.Card;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CardServiceImpl {

    CardDto createCard(CardCreateDto dto);

    CardDto setCardStatus(UUID id, CardStatus status);

    List<CardDto> getCardsByUserEmail(String email, CardStatus status, Pageable pageable);

    void deleteCard(UUID id);

    CardDto setCardLimit(UUID id, CardLimitDto limitDto);

    String getCardNumber(UUID id);

    Card findCardByNumber(String cardNumber);

    Card findCardById(UUID id);

    void updateCard(Card card);

    Card getCardById(UUID id);

    Card buildNewCard(AppUser owner, LocalDate expiryDate);

    CardDto buildCardDto(Card card);

    String generateUniqueCardNumber();

    String generateRawCardNumber();
}
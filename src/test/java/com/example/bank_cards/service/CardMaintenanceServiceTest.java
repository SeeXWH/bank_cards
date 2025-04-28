package com.example.bank_cards.service;

import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.repository.CardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardMaintenanceServiceTest {

    @Mock
    private CardRepository cardRepository;

    @InjectMocks
    private CardMaintenanceService cardMaintenanceService;

    private Card cardToExpire1;
    private Card cardToExpire2;
    private Card activeCard;

    @BeforeEach
    void setUp() {
        cardToExpire1 = new Card();
        cardToExpire1.setId(UUID.randomUUID());
        cardToExpire1.setStatus(CardStatus.ACTIVE);
        cardToExpire1.setExpiryDate(LocalDate.now().minusDays(1));
        cardToExpire2 = new Card();
        cardToExpire2.setId(UUID.randomUUID());
        cardToExpire2.setStatus(CardStatus.ACTIVE);
        cardToExpire2.setExpiryDate(LocalDate.now().minusMonths(1));
        activeCard = new Card();
        activeCard.setId(UUID.randomUUID());
        activeCard.setStatus(CardStatus.ACTIVE);
        activeCard.setExpiryDate(LocalDate.now().plusYears(1));
    }

    

    @Test
    void expireOverdueCards_shouldExpireEligibleCards() {
        List<Card> eligibleCards = new ArrayList<>();
        eligibleCards.add(cardToExpire1);
        eligibleCards.add(cardToExpire2);
        when(cardRepository.findCardsEligibleForExpiration(any(LocalDate.class)))
                .thenReturn(eligibleCards);
        cardMaintenanceService.expireOverdueCards();
        verify(cardRepository).findCardsEligibleForExpiration(eq(LocalDate.now()));
        assertEquals(CardStatus.EXPIRED, cardToExpire1.getStatus());
        assertEquals(CardStatus.EXPIRED, cardToExpire2.getStatus());
    }

    @Test
    void expireOverdueCards_shouldDoNothingWhenNoEligibleCards() {
        when(cardRepository.findCardsEligibleForExpiration(any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        cardMaintenanceService.expireOverdueCards();
        verify(cardRepository).findCardsEligibleForExpiration(eq(LocalDate.now()));
    }

    

    @Test
    void resetDailyLimits_shouldCallRepositoryMethod() {
        int expectedUpdateCount = 5;
        when(cardRepository.resetAllDailySpendingLimits()).thenReturn(expectedUpdateCount);
        cardMaintenanceService.resetDailyLimits();
        verify(cardRepository).resetAllDailySpendingLimits();
    }

    @Test
    void resetDailyLimits_shouldHandleZeroUpdates() {
        int expectedUpdateCount = 0;
        when(cardRepository.resetAllDailySpendingLimits()).thenReturn(expectedUpdateCount);
        cardMaintenanceService.resetDailyLimits();
        verify(cardRepository).resetAllDailySpendingLimits();
    }

    

    @Test
    void resetMonthlyLimits_shouldCallRepositoryMethod() {
        int expectedUpdateCount = 10;
        when(cardRepository.resetAllMonthlySpendingLimits()).thenReturn(expectedUpdateCount);
        cardMaintenanceService.resetMonthlyLimits();
        verify(cardRepository).resetAllMonthlySpendingLimits();
    }

    @Test
    void resetMonthlyLimits_shouldHandleZeroUpdates() {
        int expectedUpdateCount = 0;
        when(cardRepository.resetAllMonthlySpendingLimits()).thenReturn(expectedUpdateCount);
        cardMaintenanceService.resetMonthlyLimits();
        verify(cardRepository).resetAllMonthlySpendingLimits();
    }
}
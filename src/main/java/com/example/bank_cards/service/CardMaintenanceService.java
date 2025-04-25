package com.example.bank_cards.service;

import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardMaintenanceService {

    private final CardRepository cardRepository;

    @Scheduled(cron = "0 0 1 * * *", zone = "Europe/Moscow")
    @Transactional
    public void expireOverdueCards() {
        LocalDate today = LocalDate.now();
        List<Card> cardsToExpire = cardRepository.findCardsEligibleForExpiration(today);
        if (!cardsToExpire.isEmpty()) {
            for (Card card : cardsToExpire) {
                card.setStatus(CardStatus.EXPIRED);
            }
            log.info("SCHEDULER: Successfully marked {} cards as EXPIRED.", cardsToExpire.size());
        } else {
            log.info("SCHEDULER: No cards found to expire today.");
        }
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Europe/Moscow")
    @Transactional
    public void resetDailyLimits() {
        int updatedCount = cardRepository.resetAllDailySpendingLimits();
        if (updatedCount > 0) {
            log.info("SCHEDULER: Successfully reset daily spending for {} cards.", updatedCount);
        } else {
            log.info("SCHEDULER: No cards required daily spending reset.");
        }
    }

    @Scheduled(cron = "0 10 0 1 * *", zone = "Europe/Moscow")
    @Transactional
    public void resetMonthlyLimits() {
        int updatedCount = cardRepository.resetAllMonthlySpendingLimits();
        if (updatedCount > 0) {
            log.info("SCHEDULER: Successfully reset monthly spending for {} cards.", updatedCount);
        } else {
            log.info("SCHEDULER: No cards required monthly spending reset.");
        }

    }
}

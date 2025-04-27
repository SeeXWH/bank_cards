package com.example.bank_cards.service;

import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.repository.CardRepository;
import com.example.bank_cards.serviceInterface.CardMaintenanceServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Сервис для выполнения плановых задач по обслуживанию банковских карт.
 * <p>
 * Отвечает за автоматическое изменение статуса карт с истекшим сроком действия
 * и сброс дневных/месячных лимитов трат по картам согласно расписанию.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardMaintenanceService implements CardMaintenanceServiceImpl {

    private final CardRepository cardRepository;

    /**
     * Плановая задача для установки статуса "EXPIRED" для карт с истекшим сроком действия.
     * <p>
     * Выполняется ежедневно в 01:00 по московскому времени ({@code cron = "0 0 1 * * *", zone = "Europe/Moscow"}).
     * Метод выполняется в рамках транзакции.
     * </p>
     * <p>
     * Логика:
     * 1. Получает текущую дату.
     * 2. Находит все карты, у которых дата истечения срока действия ({@code expiryDate}) меньше текущей даты
     *    и статус которых еще не "EXPIRED".
     * 3. Если такие карты найдены, устанавливает для каждой из них статус {@link CardStatus#EXPIRED}.
     * 4. Логирует количество обновленных карт или сообщение об отсутствии карт для обновления.
     * </p>
     */
    @Override
    @Scheduled(cron = "0 0 1 * * *", zone = "Europe/Moscow") 
    @Transactional
    public void expireOverdueCards() {
        LocalDate today = LocalDate.now();
        log.info("SCHEDULER: Starting task to expire overdue cards (expiry date < {}).", today);
        List<Card> cardsToExpire = cardRepository.findCardsEligibleForExpiration(today);
        if (!cardsToExpire.isEmpty()) {
            for (Card card : cardsToExpire) {
                card.setStatus(CardStatus.EXPIRED);
                log.debug("SCHEDULER: Marking card ID {} as EXPIRED.", card.getId());
            }
            log.info("SCHEDULER: Successfully marked {} cards as EXPIRED.", cardsToExpire.size());
        } else {
            log.info("SCHEDULER: No cards found to expire today.");
        }
    }

    /**
     * Плановая задача для сброса счетчика дневных трат по всем картам.
     * <p>
     * Выполняется ежедневно в 00:05 по московскому времени ({@code cron = "0 5 0 * * *", zone = "Europe/Moscow"}).
     * Метод выполняется в рамках транзакции.
     * </p>
     * <p>
     * Логика:
     * 1. Вызывает метод репозитория {@link CardRepository#resetAllDailySpendingLimits()} для обновления поля
     *    {@code dailySpent} (установки его в 0) для всех карт.
     * 2. Логирует количество обновленных карт или сообщение об отсутствии карт для обновления.
     * </p>
     */
    @Override
    @Scheduled(cron = "0 5 0 * * *", zone = "Europe/Moscow") 
    @Transactional
    public void resetDailyLimits() {
        log.info("SCHEDULER: Starting task to reset daily spending limits.");
        int updatedCount = cardRepository.resetAllDailySpendingLimits();
        if (updatedCount > 0) {
            log.info("SCHEDULER: Successfully reset daily spending for {} cards.", updatedCount);
        } else {
            log.info("SCHEDULER: No cards required daily spending reset (updated count: {}).", updatedCount);
        }
    }

    /**
     * Плановая задача для сброса счетчика месячных трат по всем картам.
     * <p>
     * Выполняется ежемесячно 1-го числа в 00:10 по московскому времени ({@code cron = "0 10 0 1 * *", zone = "Europe/Moscow"}).
     * Метод выполняется в рамках транзакции.
     * </p>
     * <p>
     * Логика:
     * 1. Вызывает метод репозитория {@link CardRepository#resetAllMonthlySpendingLimits()} для обновления поля
     *    {@code monthlySpent} (установки его в 0) для всех карт.
     * 2. Логирует количество обновленных карт или сообщение об отсутствии карт для обновления.
     * </p>
     */
    @Override
    @Scheduled(cron = "0 10 0 1 * *", zone = "Europe/Moscow") 
    @Transactional
    public void resetMonthlyLimits() {
        log.info("SCHEDULER: Starting task to reset monthly spending limits.");
        int updatedCount = cardRepository.resetAllMonthlySpendingLimits();
        if (updatedCount > 0) {
            log.info("SCHEDULER: Successfully reset monthly spending for {} cards.", updatedCount);
        } else {
            log.info("SCHEDULER: No cards required monthly spending reset (updated count: {}).", updatedCount);
        }
    }
}
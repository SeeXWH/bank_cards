package com.example.bank_cards.repository;


import com.example.bank_cards.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardRepository extends JpaRepository<Card, UUID>, JpaSpecificationExecutor<Card> {

    boolean existsByCardNumber(String cardNumber);

     Optional<Card> findByCardNumber(String cardNumber);

     List<Card> findAllByOwnerId(UUID ownerId);


    @Query("SELECT c FROM Card c WHERE c.expiryDate < :cutoffDate AND c.status <> com.example.bank_cards.enums.CardStatus.EXPIRED")
    List<Card> findCardsEligibleForExpiration(@Param("cutoffDate") LocalDate cutoffDate);


    @Modifying
    @Query("UPDATE Card c SET c.currentDailySpending = java.math.BigDecimal.ZERO WHERE c.dailyLimit IS NOT NULL AND c.currentDailySpending > java.math.BigDecimal.ZERO")
    int resetAllDailySpendingLimits();

    @Modifying
    @Query("UPDATE Card c SET c.currentMonthlySpending = java.math.BigDecimal.ZERO WHERE c.monthlyLimit IS NOT NULL AND c.currentMonthlySpending > java.math.BigDecimal.ZERO")
    int resetAllMonthlySpendingLimits();

    List<Card> findByOwnerId(UUID id);
}

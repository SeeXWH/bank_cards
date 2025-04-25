package com.example.bank_cards.service;

import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.enums.TransactionType;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Log4j2
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final CardService cardService;


    private void verifyingPossibilityOfTransaction(Card card, BigDecimal transactionAmount, TransactionType transactionType) {
        if (card.getStatus() == CardStatus.BLOCKED) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "The card has blocked");
        }
        if (card.getStatus() == CardStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The card has expired");
        }
        if (card.getBalance().compareTo(transactionAmount) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds to complete the transaction");
        }
        if (transactionType == TransactionType.DEBIT) {
            if (card.getDailyLimit() != null && card.getCurrentDailySpending().add(transactionAmount).compareTo(card.getDailyLimit()) > 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The amount debited exceeds the daily limit");
            }
            if (card.getMonthlyLimit() != null && card.getCurrentMonthlySpending().add(transactionAmount).compareTo(card.getMonthlyLimit()) > 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The amount debited exceeds the monthly limit");

            }
        }
    }
}


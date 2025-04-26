package com.example.bank_cards.service;

import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.enums.TransactionType;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.model.Transaction;
import com.example.bank_cards.repository.CardRepository;
import com.example.bank_cards.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Log4j2
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final CardService cardService;
    private final UserService userService;


    @Transactional
    public void transferBetweenCards(UUID sendCartId, UUID receiveCartId, BigDecimal amount, String email) {
        if (sendCartId == null || receiveCartId == null || amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cards or amount cannot be null");
        }
        AppUser user = userService.getUserByEmail(email);
        Card sendCard = cardService.findCardById(sendCartId);
        Card receiveCard = cardService.findCardById(receiveCartId);
        if (!sendCard.getOwner().equals(user) || !receiveCard.getOwner().equals(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "you are not allowed to transfer cards");
        }
        verifyingPossibilityOfTransaction(sendCard, amount, TransactionType.TRANSFER);
        verifyingPossibilityOfTransaction(receiveCard);
        sendCard.setBalance(sendCard.getBalance().subtract(amount));
        receiveCard.setBalance(receiveCard.getBalance().add(amount));
        cardService.updateCard(sendCard);
        cardService.updateCard(receiveCard);
        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setType(TransactionType.TRANSFER);
        transaction.setSendCard(sendCard);
        transaction.setReceiveCard(receiveCard);
        transactionRepository.save(transaction);
    }

    @Transactional
    public void debitFromCard(UUID cardId, BigDecimal amount, String email) {
        if (cardId == null || amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "card or amount cannot be null");
        }
        AppUser appUser = userService.getUserByEmail(email);
        Card card = cardService.findCardById(cardId);
        if (!card.getOwner().equals(appUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not owner of this card");
        }
        verifyingPossibilityOfTransaction(card, amount, TransactionType.DEBIT);
        card.setBalance(card.getBalance().subtract(amount));
        card.setCurrentDailySpending(card.getCurrentDailySpending().add(amount));
        card.setCurrentMonthlySpending(card.getCurrentMonthlySpending().add(amount));
        cardService.updateCard(card);
        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setType(TransactionType.DEBIT);
        transaction.setSendCard(card);
        transaction.setReceiveCard(null);
        transactionRepository.save(transaction);
    }

    @Transactional
    public void topUpCard(UUID cardId, BigDecimal amount, String email) {
        if (cardId == null || amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "card or amount cannot be null");
        }
        AppUser appUser = userService.getUserByEmail(email);
        Card card = cardService.findCardById(cardId);
        if (!card.getOwner().equals(appUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not owner of this card");
        }
        verifyingPossibilityOfTransaction(card);
        card.setBalance(card.getBalance().add(amount));
        cardService.updateCard(card);
        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setType(TransactionType.CREDIT);
        transaction.setSendCard(null);
        transaction.setReceiveCard(card);
        transactionRepository.save(transaction);
    }


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
    private void verifyingPossibilityOfTransaction(Card card) {
        if (card.getStatus() == CardStatus.BLOCKED) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "The card has blocked");
        }
        if (card.getStatus() == CardStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The card has expired");
        }
    }
}


package com.example.bank_cards.service;

import com.example.bank_cards.dto.TransactionDto;
import com.example.bank_cards.dto.TransactionFilter;
import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.enums.TransactionType;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.model.Transaction;
import com.example.bank_cards.repository.TransactionRepository;
import com.example.bank_cards.serviceInterface.CardEncryptionServiceImpl;
import com.example.bank_cards.serviceInterface.CardServiceImpl;
import com.example.bank_cards.serviceInterface.TransactionServiceImpl;
import com.example.bank_cards.serviceInterface.UserServiceImpl;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Log4j2
public class TransactionService implements TransactionServiceImpl {
    private final TransactionRepository transactionRepository;
    private final CardServiceImpl cardService;
    private final UserServiceImpl userService;
    private final CardEncryptionServiceImpl cardEncryptionService;

    @Override
    @Transactional
    public void transferBetweenCards(UUID sendCartId, UUID receiveCartId, BigDecimal amount, String email) {
        log.info("Starting transfer between cards. SenderCardId: {}, ReceiverCardId: {}, Amount: {}, UserEmail: {}", sendCartId, receiveCartId, amount, email);

        if (sendCartId == null || receiveCartId == null || amount == null) {
            log.error("Transfer failed: cards or amount is null");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cards or amount cannot be null");
        }

        AppUser user = userService.getUserByEmail(email);
        Card sendCard = cardService.findCardById(sendCartId);
        Card receiveCard = cardService.findCardById(receiveCartId);

        if (!sendCard.getOwner().equals(user) || !receiveCard.getOwner().equals(user)) {
            log.error("Transfer failed: user {} does not own both cards", email);
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

        log.info("Transfer successful. TransactionId: {}", transaction.getId());
    }

    @Override
    @Transactional
    public void debitFromCard(UUID cardId, BigDecimal amount, String email) {
        log.info("Starting debit from card. CardId: {}, Amount: {}, UserEmail: {}", cardId, amount, email);

        if (cardId == null || amount == null) {
            log.error("Debit failed: card or amount is null");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "card or amount cannot be null");
        }

        AppUser appUser = userService.getUserByEmail(email);
        Card card = cardService.findCardById(cardId);

        if (!card.getOwner().equals(appUser)) {
            log.error("Debit failed: user {} does not own the card {}", email, cardId);
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

        log.info("Debit successful. TransactionId: {}", transaction.getId());
    }

    @Override
    @Transactional
    public void topUpCard(UUID cardId, BigDecimal amount, String email) {
        log.info("Starting top-up to card. CardId: {}, Amount: {}, UserEmail: {}", cardId, amount, email);

        if (cardId == null || amount == null) {
            log.error("Top-up failed: card or amount is null");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "card or amount cannot be null");
        }

        AppUser appUser = userService.getUserByEmail(email);
        Card card = cardService.findCardById(cardId);

        if (!card.getOwner().equals(appUser)) {
            log.error("Top-up failed: user {} does not own the card {}", email, cardId);
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

        log.info("Top-up successful. TransactionId: {}", transaction.getId());
    }

    @Override
    public void verifyingPossibilityOfTransaction(Card card, BigDecimal transactionAmount, TransactionType transactionType) {
        if (card.getStatus() == CardStatus.BLOCKED) {
            log.warn("Transaction verification failed: Card {} is BLOCKED", card.getId());
            throw new ResponseStatusException(HttpStatus.LOCKED, "The card has blocked");
        }
        if (card.getStatus() == CardStatus.EXPIRED) {
            log.warn("Transaction verification failed: Card {} is EXPIRED", card.getId());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The card has expired");
        }
        if (card.getBalance().compareTo(transactionAmount) < 0) {
            log.warn("Transaction verification failed: Insufficient funds on Card {}", card.getId());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds to complete the transaction");
        }
        if (transactionType == TransactionType.DEBIT) {
            if (card.getDailyLimit() != null && card.getCurrentDailySpending().add(transactionAmount).compareTo(card.getDailyLimit()) > 0) {
                log.warn("Daily limit exceeded on Card {}", card.getId());
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The amount debited exceeds the daily limit");
            }
            if (card.getMonthlyLimit() != null && card.getCurrentMonthlySpending().add(transactionAmount).compareTo(card.getMonthlyLimit()) > 0) {
                log.warn("Monthly limit exceeded on Card {}", card.getId());
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The amount debited exceeds the monthly limit");
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactions(String userEmail, TransactionFilter filter, Pageable pageable) {
        log.info("Fetching transactions, userEmail: {}, filters: {}", userEmail, filter);

        Specification<Transaction> spec = Specification.where(null);

        if (StringUtils.hasText(userEmail)) {
            AppUser user = userService.getUserByEmail(userEmail);
            spec = (root, query, cb) -> {
                Join<Transaction, Card> sendCardJoin = root.join("sendCard", JoinType.LEFT);
                Join<Transaction, Card> receiveCardJoin = root.join("receiveCard", JoinType.LEFT);
                Predicate userSendPredicate = cb.equal(sendCardJoin.get("owner").get("id"), user.getId());
                Predicate userReceivePredicate = cb.equal(receiveCardJoin.get("owner").get("id"), user.getId());
                return cb.or(userSendPredicate, userReceivePredicate);
            };
        }

        if (filter.getCardId() != null) {
            spec = spec.and((root, query, cb) -> {
                Join<Transaction, Card> sendCardJoin = root.join("sendCard", JoinType.LEFT);
                Join<Transaction, Card> receiveCardJoin = root.join("receiveCard", JoinType.LEFT);
                Predicate sendPredicate = cb.equal(sendCardJoin.get("id"), filter.getCardId());
                Predicate receivePredicate = cb.equal(receiveCardJoin.get("id"), filter.getCardId());
                return cb.or(sendPredicate, receivePredicate);
            });
        }
        if (filter.getType() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("type"), filter.getType()));
        }
        if (filter.getAmountFrom() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("amount"), filter.getAmountFrom()));
        }
        if (filter.getAmountTo() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("amount"), filter.getAmountTo()));
        }
        if (filter.getCreatedAtFrom() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getCreatedAtFrom()));
        }
        if (filter.getCreatedAtTo() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("createdAt"), filter.getCreatedAtTo()));
        }

        Page<Transaction> transactionsPage = transactionRepository.findAll(spec, pageable);

        if (transactionsPage.isEmpty()) {
            log.info("No transactions found for user {}", userEmail);
            return Collections.emptyList();
        }

        log.info("Fetched {} transactions for user {}", transactionsPage.getTotalElements(), userEmail);

        return transactionsPage.getContent().stream()
                .map(this::transactionToDto)
                .toList();
    }

    @Override
    public void verifyingPossibilityOfTransaction(Card card) {
        if (card.getStatus() == CardStatus.BLOCKED) {
            log.warn("Card {} is BLOCKED", card.getId());
            throw new ResponseStatusException(HttpStatus.LOCKED, "The card has blocked");
        }
        if (card.getStatus() == CardStatus.EXPIRED) {
            log.warn("Card {} is EXPIRED", card.getId());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The card has expired");
        }
    }

    @Override
    public TransactionDto transactionToDto(Transaction transaction) {
        TransactionDto transactionDto = new TransactionDto();
        transactionDto.setId(transaction.getId());
        transactionDto.setAmount(transaction.getAmount());
        transactionDto.setType(transaction.getType());
        if (transaction.getSendCard() != null){
            String sendCardNumber = cardEncryptionService.maskCardNumber(transaction.getSendCard().getCardNumber());
            transactionDto.setSendCardNumber(sendCardNumber);
        }
        if (transaction.getReceiveCard() != null){
            String receiveCardNumber = cardEncryptionService.maskCardNumber(transaction.getReceiveCard().getCardNumber());
            transactionDto.setReceiveCardNumber(receiveCardNumber);
        }
        transactionDto.setCreatedAt(transaction.getCreatedAt());
        return transactionDto;
    }
}

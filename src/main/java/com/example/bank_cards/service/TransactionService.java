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

/**
 * Сервис для управления транзакциями по банковским картам.
 * Предоставляет функциональность для выполнения переводов, списаний, пополнений,
 * а также получения истории транзакций с фильтрацией.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class TransactionService implements TransactionServiceImpl {
    private final TransactionRepository transactionRepository;
    private final CardServiceImpl cardService;
    private final UserServiceImpl userService;
    private final CardEncryptionServiceImpl cardEncryptionService;

    /**
     * Выполняет перевод средств между двумя картами одного пользователя.
     * Проверяет принадлежность обеих карт пользователю, доступность карт и достаточность средств на карте отправителя.
     * Обновляет балансы обеих карт и сохраняет транзакцию типа TRANSFER.
     *
     * @param sendCardId   UUID карты отправителя.
     * @param receiveCardId UUID карты получателя.
     * @param amount       Сумма перевода. Должна быть положительной.
     * @param email        Email пользователя, инициирующего перевод.
     * @throws ResponseStatusException Если пользователь не найден (NOT_FOUND).
     * @throws ResponseStatusException Если одна из карт не найдена (NOT_FOUND).
     * @throws ResponseStatusException Если пользователь не владеет обеими картами (FORBIDDEN).
     * @throws ResponseStatusException Если карта отправителя заблокирована (LOCKED) или просрочена (UNPROCESSABLE_ENTITY).
     * @throws ResponseStatusException Если карта получателя заблокирована (LOCKED) или просрочена (UNPROCESSABLE_ENTITY).
     * @throws ResponseStatusException Если на карте отправителя недостаточно средств (UNPROCESSABLE_ENTITY).
     */
    @Override
    @Transactional
    public void transferBetweenCards(UUID sendCardId, UUID receiveCardId, BigDecimal amount, String email) {
        log.info("Starting transfer between cards. SenderCardId: {}, ReceiverCardId: {}, Amount: {}, UserEmail: {}", sendCardId, receiveCardId, amount, email);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Transfer failed: amount must be positive. Amount: {}", amount);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transfer amount must be positive");
        }
        AppUser user = userService.getUserByEmail(email);
        Card sendCard = cardService.findCardById(sendCardId);
        Card receiveCard = cardService.findCardById(receiveCardId);
        if (!sendCard.getOwner().equals(user) || !receiveCard.getOwner().equals(user)) {
            log.error("Transfer failed: user {} does not own both cards (Sender Owner: {}, Receiver Owner: {}, User: {})",
                    email, sendCard.getOwner().getId(), receiveCard.getOwner().getId(), user.getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to perform transfers between these cards"); 
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

    /**
     * Выполняет списание средств с карты пользователя.
     * Проверяет принадлежность карты пользователю, доступность карты, достаточность средств и лимиты (дневной, месячный).
     * Обновляет баланс карты, текущие траты и сохраняет транзакцию типа DEBIT.
     *
     * @param cardId UUID карты, с которой производится списание.
     * @param amount Сумма списания. Должна быть положительной.
     * @param email  Email пользователя, инициирующего списание.
     * @throws ResponseStatusException Если пользователь не найден (NOT_FOUND).
     * @throws ResponseStatusException Если карта не найдена (NOT_FOUND).
     * @throws ResponseStatusException Если пользователь не владеет картой (FORBIDDEN).
     * @throws ResponseStatusException Если карта заблокирована (LOCKED) или просрочена (UNPROCESSABLE_ENTITY).
     * @throws ResponseStatusException Если недостаточно средств (UNPROCESSABLE_ENTITY).
     * @throws ResponseStatusException Если превышен дневной или месячный лимит (UNPROCESSABLE_ENTITY).
     */
    @Override
    @Transactional
    public void debitFromCard(UUID cardId, BigDecimal amount, String email) {
        log.info("Starting debit from card. CardId: {}, Amount: {}, UserEmail: {}", cardId, amount, email);
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

    /**
     * Выполняет пополнение карты пользователя.
     * Проверяет принадлежность карты пользователю и доступность карты.
     * Обновляет баланс карты и сохраняет транзакцию типа CREDIT.
     *
     * @param cardId UUID карты для пополнения.
     * @param amount Сумма пополнения. Должна быть положительной.
     * @param email  Email пользователя, инициирующего пополнение.
     * @throws ResponseStatusException Если пользователь не найден (NOT_FOUND).
     * @throws ResponseStatusException Если карта не найдена (NOT_FOUND).
     * @throws ResponseStatusException Если пользователь не владеет картой (FORBIDDEN).
     * @throws ResponseStatusException Если карта заблокирована (LOCKED) или просрочена (UNPROCESSABLE_ENTITY).
     */
    @Override
    @Transactional
    public void topUpCard(UUID cardId, BigDecimal amount, String email) {
        log.info("Starting top-up to card. CardId: {}, Amount: {}, UserEmail: {}", cardId, amount, email);
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

    /**
     * Проверяет возможность выполнения транзакции списания или перевода с указанной карты.
     * Проверяет статус карты, достаточность средств и лимиты (только для DEBIT).
     *
     * @param card              Карта, с которой происходит операция.
     * @param transactionAmount Сумма транзакции.
     * @param transactionType   Тип транзакции (DEBIT или TRANSFER).
     * @throws ResponseStatusException Если карта заблокирована (LOCKED).
     * @throws ResponseStatusException Если карта просрочена (UNPROCESSABLE_ENTITY).
     * @throws ResponseStatusException Если недостаточно средств (UNPROCESSABLE_ENTITY).
     * @throws ResponseStatusException Если превышен дневной или месячный лимит для типа DEBIT (UNPROCESSABLE_ENTITY).
     */
    @Override
    public void verifyingPossibilityOfTransaction(Card card, BigDecimal transactionAmount, TransactionType transactionType) {
        if (card.getStatus() == CardStatus.BLOCKED) {
            log.warn("Transaction verification failed: Card {} is BLOCKED", card.getId());
            throw new ResponseStatusException(HttpStatus.LOCKED, "The card is blocked"); 
        }
        if (card.getStatus() == CardStatus.EXPIRED) {
            log.warn("Transaction verification failed: Card {} is EXPIRED", card.getId());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The card has expired"); 
        }
        if (card.getBalance().compareTo(transactionAmount) < 0) {
            log.warn("Transaction verification failed: Insufficient funds on Card {}. Required: {}, Available: {}", card.getId(), transactionAmount, card.getBalance());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds to complete the transaction"); 
        }
        if (transactionType == TransactionType.DEBIT) {
            if (card.getDailyLimit() != null && card.getCurrentDailySpending().add(transactionAmount).compareTo(card.getDailyLimit()) > 0) {
                log.warn("Daily limit exceeded on Card {}. Current: {}, Transaction: {}, Limit: {}", card.getId(), card.getCurrentDailySpending(), transactionAmount, card.getDailyLimit());
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The transaction amount exceeds the daily limit"); 
            }
            if (card.getMonthlyLimit() != null && card.getCurrentMonthlySpending().add(transactionAmount).compareTo(card.getMonthlyLimit()) > 0) {
                log.warn("Monthly limit exceeded on Card {}. Current: {}, Transaction: {}, Limit: {}", card.getId(), card.getCurrentMonthlySpending(), transactionAmount, card.getMonthlyLimit());
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The transaction amount exceeds the monthly limit"); 
            }
        }
    }

    /**
     * Возвращает список транзакций с возможностью фильтрации и пагинацией.
     * Позволяет фильтровать по email пользователя (если не null, возвращает транзакции, где пользователь - отправитель или получатель),
     * ID карты (возвращает транзакции, где карта - отправитель или получатель), типу транзакции, диапазону сумм и диапазону дат создания.
     *
     * @param userEmail Email пользователя для фильтрации (если null, фильтр по пользователю не применяется - для админа).
     * @param filter    Объект с параметрами фильтрации (тип, сумма от/до, дата от/до, ID карты).
     * @param pageable  Параметры пагинации и сортировки.
     * @return Список {@link TransactionDto}, удовлетворяющих критериям фильтрации, или пустой список.
     * @throws ResponseStatusException Если указан `userEmail`, но пользователь с таким email не найден.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactions(String userEmail, TransactionFilter filter, Pageable pageable) {
        log.info("Fetching transactions. userEmail: {}, filter: {}, pageable: {}", userEmail, filter, pageable);
        Specification<Transaction> spec = Specification.where(null);
        if (StringUtils.hasText(userEmail)) {
            AppUser user = userService.getUserByEmail(userEmail); 
            spec = spec.and((root, query, cb) -> {
                Join<Transaction, Card> sendCardJoin = root.join("sendCard", JoinType.LEFT);
                Join<Transaction, Card> receiveCardJoin = root.join("receiveCard", JoinType.LEFT);
                Predicate userIsSenderOwner = cb.equal(sendCardJoin.get("owner").get("id"), user.getId());
                Predicate userIsReceiverOwner = cb.equal(receiveCardJoin.get("owner").get("id"), user.getId());
                return cb.or(userIsSenderOwner, userIsReceiverOwner);
            });
        }
        if (filter.getCardId() != null) {
            spec = spec.and((root, query, cb) -> {
                Join<Transaction, Card> sendCardJoin = root.join("sendCard", JoinType.LEFT);
                Join<Transaction, Card> receiveCardJoin = root.join("receiveCard", JoinType.LEFT);
                Predicate cardIsSender = cb.equal(sendCardJoin.get("id"), filter.getCardId());
                Predicate cardIsReceiver = cb.equal(receiveCardJoin.get("id"), filter.getCardId());
                return cb.or(cardIsSender, cardIsReceiver);
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
            log.info("No transactions found for the given criteria. userEmail: {}, filter: {}", userEmail, filter);
            return Collections.emptyList();
        }
        log.info("Fetched {} transactions. userEmail: {}, filter: {}, page: {}/{}",
                transactionsPage.getTotalElements(), userEmail, filter, transactionsPage.getNumber(), transactionsPage.getTotalPages());
        return transactionsPage.getContent().stream()
                .map(this::transactionToDto)
                .toList();
    }

    /**
     * Проверяет возможность выполнения транзакции пополнения или получения перевода для указанной карты.
     * Проверяет только статус карты (не заблокирована, не просрочена).
     *
     * @param card Карта, на которую поступают средства.
     * @throws ResponseStatusException Если карта заблокирована (LOCKED).
     * @throws ResponseStatusException Если карта просрочена (UNPROCESSABLE_ENTITY).
     */
    @Override
    public void verifyingPossibilityOfTransaction(Card card) {
        if (card.getStatus() == CardStatus.BLOCKED) {
            log.warn("Card {} is BLOCKED, cannot receive funds", card.getId());
            throw new ResponseStatusException(HttpStatus.LOCKED, "The receiving card is blocked"); 
        }
        if (card.getStatus() == CardStatus.EXPIRED) {
            log.warn("Card {} is EXPIRED, cannot receive funds", card.getId());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The receiving card has expired"); 
        }
    }

    /**
     * Преобразует сущность {@link Transaction} в {@link TransactionDto}.
     * Маскирует номера карт отправителя и получателя.
     *
     * @param transaction Сущность транзакции.
     * @return DTO транзакции с замаскированными номерами карт.
     */
    @Override
    public TransactionDto transactionToDto(Transaction transaction) {
        TransactionDto transactionDto = new TransactionDto();
        transactionDto.setId(transaction.getId());
        transactionDto.setAmount(transaction.getAmount());
        transactionDto.setType(transaction.getType());
        if (transaction.getSendCard() != null){
            String sendCardNumber = cardEncryptionService.maskCardNumber(transaction.getSendCard().getCardNumber());
            transactionDto.setSendCardNumber(sendCardNumber);
        } else {
            transactionDto.setSendCardNumber(null); 
        }
        if (transaction.getReceiveCard() != null){
            String receiveCardNumber = cardEncryptionService.maskCardNumber(transaction.getReceiveCard().getCardNumber());
            transactionDto.setReceiveCardNumber(receiveCardNumber);
        } else {
            transactionDto.setReceiveCardNumber(null); 
        }
        transactionDto.setCreatedAt(transaction.getCreatedAt());
        return transactionDto;
    }
}
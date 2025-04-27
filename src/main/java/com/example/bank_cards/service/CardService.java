package com.example.bank_cards.service;

import com.example.bank_cards.dto.CardCreateDto;
import com.example.bank_cards.dto.CardDto;
import com.example.bank_cards.dto.CardLimitDto;
import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.repository.CardRepository;
import com.example.bank_cards.serviceInterface.CardEncryptionServiceImpl;
import com.example.bank_cards.serviceInterface.CardServiceImpl;
import com.example.bank_cards.serviceInterface.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Сервис для управления банковскими картами.
 * Предоставляет функциональность для создания, обновления, поиска, удаления карт,
 * а также управления их статусами и лимитами.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class CardService implements CardServiceImpl {

    private final Random random = new Random();
    private final CardRepository cardRepository;
    private final UserServiceImpl userService;
    private final CardEncryptionServiceImpl cardEncryptionService;

    /**
     * Создает новую банковскую карту для пользователя.
     * Генерирует уникальный номер карты, шифрует его и сохраняет карту в базе данных.
     *
     * @param dto DTO с данными для создания карты (email пользователя и дата окончания срока действия).
     * @return DTO созданной карты с замаскированным номером.
     * @throws ResponseStatusException если пользователь с указанным email не найден.
     */
    @Override
    @Transactional
    public CardDto createCard(CardCreateDto dto) {
        AppUser owner = userService.getUserByEmail(dto.getEmail());
        Card card = buildNewCard(owner, dto.getExpiryDate());
        cardRepository.save(card);
        log.info("Created new card for user: {}", owner.getEmail());
        return buildCardDto(card);
    }

    /**
     * Устанавливает статус для указанной карты.
     * Запрещает установку статуса EXPIRED вручную.
     *
     * @param id     UUID карты.
     * @param status Новый статус карты (ACTIVE, BLOCKED и т.д.).
     * @return DTO обновленной карты.
     * @throws ResponseStatusException если карта с указанным ID не найдена или попытка установить статус EXPIRED.
     */
    @Override
    @Transactional
    public CardDto setCardStatus(UUID id, CardStatus status) {
        if (status == CardStatus.EXPIRED) {
            log.warn("Attempt to manually set status to EXPIRED.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot manually set status to EXPIRED");
        }
        Card card = getCardById(id);
        card.setStatus(status);
        cardRepository.save(card);
        log.info("Card status updated for card id: {}", id);
        return buildCardDto(card);
    }

    /**
     * Возвращает список карт пользователя с пагинацией и возможностью фильтрации по статусу.
     *
     * @param email    Email пользователя, чьи карты нужно найти.
     * @param status   Статус карты для фильтрации (опционально).
     * @param pageable Параметры пагинации и сортировки.
     * @return Список DTO карт пользователя или пустой список, если карты не найдены.
     * @throws ResponseStatusException если пользователь с указанным email не найден.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CardDto> getCardsByUserEmail(String email, CardStatus status, Pageable pageable) {
        userService.getUserByEmail(email); 
        Specification<Card> spec = (root, query, cb) -> cb.equal(root.get("owner").get("email"), email);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        Page<Card> cardsPage = cardRepository.findAll(spec, pageable);
        if (cardsPage.isEmpty()) {
            log.info("No cards found for user: {}", email);
            return Collections.emptyList();
        }
        log.info("Found {} cards for user: {}", cardsPage.getTotalElements(), email);
        return cardsPage.map(this::buildCardDto).getContent();
    }

    /**
     * Удаляет карту по её идентификатору.
     *
     * @param id UUID карты для удаления.
     * @throws ResponseStatusException если карта с указанным ID не найдена.
     */
    @Override
    @Transactional
    public void deleteCard(UUID id) {
        Card card = getCardById(id);
        cardRepository.delete(card);
        log.info("Deleted card with id: {}", id);
    }

    /**
     * Устанавливает или обновляет дневной и/или месячный лимиты для карты.
     * Если лимит в DTO равен null, соответствующий лимит карты не изменяется.
     *
     * @param id       UUID карты.
     * @param limitDto DTO с новыми значениями лимитов.
     * @return DTO обновленной карты.
     * @throws ResponseStatusException если карта с указанным ID не найдена.
     */
    @Override
    @Transactional
    public CardDto setCardLimit(UUID id, CardLimitDto limitDto) {
        Card card = getCardById(id);
        if (limitDto.getDailyLimit() != null) {
            card.setDailyLimit(limitDto.getDailyLimit());
        }
        if (limitDto.getMonthlyLimit() != null) {
            card.setMonthlyLimit(limitDto.getMonthlyLimit());
        }
        cardRepository.save(card);
        log.info("Updated limits for card id: {}", id);
        return buildCardDto(card);
    }

    /**
     * Возвращает полный, расшифрованный номер карты.
     *
     * @param id UUID карты.
     * @return Расшифрованный номер карты.
     * @throws ResponseStatusException если карта с указанным ID не найдена.
     */
    @Override
    @Transactional 
    public String getCardNumber(UUID id) {
        Card card = getCardById(id);
        return cardEncryptionService.decryptCardNumber(card.getCardNumber());
    }

    /**
     * Находит карту по её полному номеру (после шифрования номера).
     *
     * @param cardNumber Полный номер карты.
     * @return Найденная сущность Card.
     * @throws ResponseStatusException если карта с таким номером не найдена.
     */
    @Override
    @Transactional(readOnly = true)
    public Card findCardByNumber(String cardNumber) {
        String encryptedNumber = cardEncryptionService.encryptCardNumber(cardNumber);
        return cardRepository.findByCardNumber(encryptedNumber)
                .orElseThrow(() -> {
                    log.warn("Card not found with provided number (lookup using encrypted value)");
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found");
                });
    }

    /**
     * Находит карту по её UUID.
     * Используется внутри сервиса, когда нужна полная сущность Card.
     *
     * @param id UUID карты.
     * @return Найденная сущность Card.
     * @throws ResponseStatusException если карта с указанным ID не найдена.
     */
    @Override
    @Transactional(readOnly = true)
    public Card findCardById(UUID id) {
        return cardRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Card not found with id: {}", id);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found");
                });
    }

    /**
     * Обновляет данные существующей карты в базе данных.
     * Предназначен для внутренних операций, например, при обновлении баланса из другого сервиса.
     *
     * @param card Сущность Card с обновленными данными.
     * @throws ResponseStatusException если карта с ID из объекта `card` не существует в базе данных.
     */
    @Override
    @Transactional
    public void updateCard(Card card) {
        if (!cardRepository.existsById(card.getId())) {
            log.warn("Update card failed: card not found with id: {}", card.getId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found");
        }
        cardRepository.save(card);
        log.info("Card updated: id {}", card.getId());
    }


    /**
     * Вспомогательный метод для получения карты по ID.
     * Используется внутри сервиса для уменьшения дублирования кода поиска карты.
     *
     * @param id UUID карты.
     * @return Найденная сущность Card.
     * @throws ResponseStatusException если карта с указанным ID не найдена.
     */
    @Override
    public Card getCardById(UUID id) {
        return cardRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Card not found with id: {}", id);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found");
                });
    }

    /**
     * Создает новый объект Card (без сохранения в БД).
     * Устанавливает владельца, срок действия, генерирует и шифрует номер,
     * устанавливает нулевой баланс и статус ACTIVE.
     *
     * @param owner      Владелец карты (сущность AppUser).
     * @param expiryDate Дата окончания срока действия карты.
     * @return Новый объект Card.
     */
    @Override
    public Card buildNewCard(AppUser owner, LocalDate expiryDate) {
        String number = generateUniqueCardNumber();
        String encryptedNumber = cardEncryptionService.encryptCardNumber(number);
        Card card = new Card();
        card.setCardNumber(encryptedNumber); 
        card.setExpiryDate(expiryDate);
        card.setOwner(owner);
        card.setBalance(BigDecimal.ZERO);
        card.setStatus(CardStatus.ACTIVE);
        return card;
    }

    /**
     * Преобразует сущность Card в CardDto.
     * Маскирует номер карты для безопасного отображения.
     *
     * @param card Сущность Card.
     * @return DTO карты с замаскированным номером.
     */
    @Override
    public CardDto buildCardDto(Card card) {
        CardDto dto = new CardDto();
        dto.setId(card.getId());
        dto.setCardNumber(cardEncryptionService.maskCardNumber(card.getCardNumber()));
        dto.setExpiryDate(card.getExpiryDate());
        dto.setDailyLimit(card.getDailyLimit());
        dto.setMonthlyLimit(card.getMonthlyLimit());
        dto.setStatus(card.getStatus());
        dto.setBalance(card.getBalance());
        dto.setOwnerName(card.getOwner().getName()); 
        return dto;
    }

    /**
     * Генерирует уникальный 16-значный номер карты.
     * Проверяет уникальность сгенерированного номера в базе данных перед возвратом.
     *
     * @return Уникальный номер карты (строка из 16 цифр).
     */
    @Override
    public String generateUniqueCardNumber() {
        String cardNumber;
        String encryptedNumber;
        do {
            cardNumber = generateRawCardNumber();
            encryptedNumber = cardEncryptionService.encryptCardNumber(cardNumber);
            
        } while (cardRepository.existsByCardNumber(encryptedNumber));
        return cardNumber; 
    }

    /**
     * Генерирует случайный 16-значный номер карты в виде строки.
     * Формат: 4 группы по 4 цифры.
     *
     * @return Сгенерированный 16-значный номер карты.
     */
    @Override
    public String generateRawCardNumber() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(String.format("%04d", random.nextInt(10000)));
            
            
        }
        return sb.toString();
    }
}
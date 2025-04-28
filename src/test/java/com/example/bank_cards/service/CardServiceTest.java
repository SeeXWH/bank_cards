package com.example.bank_cards.service;

import com.example.bank_cards.dto.CardCreateDto;
import com.example.bank_cards.dto.CardDto;
import com.example.bank_cards.dto.CardLimitDto;
import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.model.AppUser;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.repository.CardRepository;
import com.example.bank_cards.serviceInterface.CardEncryptionServiceImpl;
import com.example.bank_cards.serviceInterface.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private UserServiceImpl userService;

    @Mock
    private CardEncryptionServiceImpl cardEncryptionService;

    @InjectMocks
    private CardService cardService;

    private AppUser testUser;
    private Card testCard;
    private final UUID testCardId = UUID.randomUUID();
    private final String testEmail = "test@example.com";
    private final String testCardNumber = "1234567812345678";
    private final String encryptedCardNumber = "encrypted12345678";
    private final String maskedCardNumber = "****-****-****-5678";

    @BeforeEach
    void setUp() {
        testUser = new AppUser();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail(testEmail);
        testUser.setName("Test User");

        testCard = new Card();
        testCard.setId(testCardId);
        testCard.setOwner(testUser);
        testCard.setCardNumber(encryptedCardNumber);
        testCard.setExpiryDate(LocalDate.now().plusYears(3));
        testCard.setStatus(CardStatus.ACTIVE);
        testCard.setBalance(BigDecimal.ZERO);
    }

    @Test
    void createCard_ShouldCreateNewCard() {

        CardCreateDto dto = new CardCreateDto(testEmail, LocalDate.now().plusYears(3));
        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardEncryptionService.encryptCardNumber(anyString())).thenReturn(encryptedCardNumber);
        when(cardEncryptionService.maskCardNumber(encryptedCardNumber)).thenReturn(maskedCardNumber);
        when(cardRepository.save(any(Card.class))).thenReturn(testCard);


        CardDto result = cardService.createCard(dto);


        assertNotNull(result);
        assertEquals(maskedCardNumber, result.getCardNumber());
        assertEquals(testUser.getName(), result.getOwnerName());
        assertEquals(CardStatus.ACTIVE, result.getStatus());
        verify(cardRepository, times(1)).save(any(Card.class));
    }

    @Test
    void createCard_ShouldThrowWhenUserNotFound() {

        CardCreateDto dto = new CardCreateDto("nonexistent@example.com", LocalDate.now().plusYears(3));
        when(userService.getUserByEmail("nonexistent@example.com"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));


        assertThrows(ResponseStatusException.class, () -> cardService.createCard(dto));
    }

    @Test
    void setCardStatus_ShouldUpdateStatus() {

        when(cardRepository.findById(testCardId)).thenReturn(Optional.of(testCard));
        when(cardEncryptionService.maskCardNumber(encryptedCardNumber)).thenReturn(maskedCardNumber);


        CardDto result = cardService.setCardStatus(testCardId, CardStatus.BLOCKED);


        assertEquals(CardStatus.BLOCKED, result.getStatus());
        verify(cardRepository, times(1)).save(testCard);
    }

    @Test
    void setCardStatus_ShouldThrowWhenCardNotFound() {

        when(cardRepository.findById(testCardId)).thenReturn(Optional.empty());


        assertThrows(ResponseStatusException.class,
                () -> cardService.setCardStatus(testCardId, CardStatus.BLOCKED));
    }

    @Test
    void getCardsByUserEmail_ShouldReturnCards() {

        Pageable pageable = Pageable.unpaged();
        List<Card> cards = Collections.singletonList(testCard);
        Page<Card> page = new PageImpl<>(cards);

        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(cardEncryptionService.maskCardNumber(encryptedCardNumber)).thenReturn(maskedCardNumber);


        List<CardDto> result = cardService.getCardsByUserEmail(testEmail, null, pageable);


        assertEquals(1, result.size());
        assertEquals(testCardId, result.get(0).getId());
    }

    @Test
    void getCardsByUserEmail_ShouldReturnEmptyListWhenNoCards() {

        Pageable pageable = Pageable.unpaged();
        Page<Card> emptyPage = new PageImpl<>(Collections.emptyList());

        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);


        List<CardDto> result = cardService.getCardsByUserEmail(testEmail, null, pageable);


        assertTrue(result.isEmpty());
    }

    @Test
    void deleteCard_ShouldDeleteCard() {

        when(cardRepository.findById(testCardId)).thenReturn(Optional.of(testCard));


        cardService.deleteCard(testCardId);


        verify(cardRepository, times(1)).delete(testCard);
    }

    @Test
    void setCardLimit_ShouldUpdateLimits() {

        CardLimitDto limitDto = new CardLimitDto(BigDecimal.valueOf(1000), BigDecimal.valueOf(5000));
        when(cardRepository.findById(testCardId)).thenReturn(Optional.of(testCard));
        when(cardEncryptionService.maskCardNumber(encryptedCardNumber)).thenReturn(maskedCardNumber);


        CardDto result = cardService.setCardLimit(testCardId, limitDto);


        assertEquals(BigDecimal.valueOf(1000), result.getDailyLimit());
        assertEquals(BigDecimal.valueOf(5000), result.getMonthlyLimit());
        verify(cardRepository, times(1)).save(testCard);
    }

    @Test
    void setCardLimit_ShouldUpdateOnlyProvidedLimits() {

        CardLimitDto limitDto = new CardLimitDto(BigDecimal.valueOf(1000), null);
        when(cardRepository.findById(testCardId)).thenReturn(Optional.of(testCard));
        when(cardEncryptionService.maskCardNumber(encryptedCardNumber)).thenReturn(maskedCardNumber);


        CardDto result = cardService.setCardLimit(testCardId, limitDto);


        assertEquals(BigDecimal.valueOf(1000), result.getDailyLimit());
        assertNull(result.getMonthlyLimit());
    }

    @Test
    void getCardNumber_ShouldReturnDecryptedNumber() {

        when(cardRepository.findById(testCardId)).thenReturn(Optional.of(testCard));
        when(cardEncryptionService.decryptCardNumber(encryptedCardNumber)).thenReturn(testCardNumber);


        String result = cardService.getCardNumber(testCardId);


        assertEquals(testCardNumber, result);
    }

    @Test
    void findCardByNumber_ShouldReturnCard() {

        when(cardEncryptionService.encryptCardNumber(testCardNumber)).thenReturn(encryptedCardNumber);
        when(cardRepository.findByCardNumber(encryptedCardNumber)).thenReturn(Optional.of(testCard));


        Card result = cardService.findCardByNumber(testCardNumber);


        assertEquals(testCard, result);
    }

    @Test
    void findCardByNumber_ShouldThrowWhenCardNotFound() {

        when(cardEncryptionService.encryptCardNumber(testCardNumber)).thenReturn(encryptedCardNumber);
        when(cardRepository.findByCardNumber(encryptedCardNumber)).thenReturn(Optional.empty());


        assertThrows(ResponseStatusException.class, () -> cardService.findCardByNumber(testCardNumber));
    }

    @Test
    void updateCard_ShouldUpdateCard() {

        when(cardRepository.existsById(testCardId)).thenReturn(true);


        cardService.updateCard(testCard);


        verify(cardRepository, times(1)).save(testCard);
    }

    @Test
    void updateCard_ShouldThrowWhenCardNotFound() {

        when(cardRepository.existsById(testCardId)).thenReturn(false);


        assertThrows(ResponseStatusException.class, () -> cardService.updateCard(testCard));
    }

    @Test
    void buildNewCard_ShouldBuildCardWithCorrectProperties() {

        LocalDate expiryDate = LocalDate.now().plusYears(3);
        when(cardEncryptionService.encryptCardNumber(anyString())).thenReturn(encryptedCardNumber);


        Card result = cardService.buildNewCard(testUser, expiryDate);


        assertNotNull(result);
        assertEquals(encryptedCardNumber, result.getCardNumber());
        assertEquals(expiryDate, result.getExpiryDate());
        assertEquals(testUser, result.getOwner());
        assertEquals(BigDecimal.ZERO, result.getBalance());
        assertEquals(CardStatus.ACTIVE, result.getStatus());
    }

    @Test
    void buildCardDto_ShouldBuildDtoWithMaskedNumber() {

        when(cardEncryptionService.maskCardNumber(encryptedCardNumber)).thenReturn(maskedCardNumber);


        CardDto result = cardService.buildCardDto(testCard);


        assertEquals(testCardId, result.getId());
        assertEquals(maskedCardNumber, result.getCardNumber());
        assertEquals(testCard.getExpiryDate(), result.getExpiryDate());
        assertEquals(testUser.getName(), result.getOwnerName());
    }

    @Test
    void generateRawCardNumber_ShouldReturn16DigitString() {

        String result = cardService.generateRawCardNumber();


        assertNotNull(result);
        assertEquals(16, result.length());
        assertTrue(result.matches("\\d{16}"));
    }

    @Test
    void generateUniqueCardNumber_ShouldReturnUniqueNumber() {

        when(cardEncryptionService.encryptCardNumber(anyString())).thenReturn(encryptedCardNumber);
        when(cardRepository.existsByCardNumber(encryptedCardNumber)).thenReturn(false);


        String result = cardService.generateUniqueCardNumber();


        assertNotNull(result);
        assertEquals(16, result.length());
        verify(cardRepository, atLeastOnce()).existsByCardNumber(encryptedCardNumber);
    }
}
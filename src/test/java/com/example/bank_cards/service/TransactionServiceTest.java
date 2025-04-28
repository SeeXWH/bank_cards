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
import com.example.bank_cards.serviceInterface.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private CardServiceImpl cardService;
    @Mock
    private UserServiceImpl userService;
    @Mock
    private CardEncryptionServiceImpl cardEncryptionService;

    @InjectMocks
    private TransactionService transactionService;

    private AppUser testUser;
    private Card sendCard;
    private Card receiveCard;
    private Card debitCard;
    private Card topUpCard;
    private final String testEmail = "user@example.com";
    private final UUID userId = UUID.randomUUID();
    private final UUID sendCardId = UUID.randomUUID();
    private final UUID receiveCardId = UUID.randomUUID();
    private final UUID debitCardId = UUID.randomUUID();
    private final UUID topUpCardId = UUID.randomUUID();
    private final BigDecimal transferAmount = new BigDecimal("100.00");
    private final BigDecimal debitAmount = new BigDecimal("50.00");
    private final BigDecimal topUpAmount = new BigDecimal("200.00");
    private final String maskedSendCardNumber = "4000********1111";
    private final String maskedReceiveCardNumber = "5000********2222";

    @BeforeEach
    void setUp() {
        testUser = new AppUser();
        testUser.setId(userId);
        testUser.setEmail(testEmail);
        sendCard = new Card();
        sendCard.setId(sendCardId);
        sendCard.setOwner(testUser);
        sendCard.setBalance(new BigDecimal("500.00"));
        sendCard.setStatus(CardStatus.ACTIVE);
        sendCard.setCardNumber("4000111122223333");
        sendCard.setExpiryDate(LocalDate.now().plusYears(1));
        sendCard.setCurrentDailySpending(BigDecimal.ZERO);
        sendCard.setCurrentMonthlySpending(BigDecimal.ZERO);
        receiveCard = new Card();
        receiveCard.setId(receiveCardId);
        receiveCard.setOwner(testUser);
        receiveCard.setBalance(new BigDecimal("200.00"));
        receiveCard.setStatus(CardStatus.ACTIVE);
        receiveCard.setCardNumber("5000222233334444");
        receiveCard.setExpiryDate(LocalDate.now().plusYears(1));
        debitCard = new Card();
        debitCard.setId(debitCardId);
        debitCard.setOwner(testUser);
        debitCard.setBalance(new BigDecimal("1000.00"));
        debitCard.setStatus(CardStatus.ACTIVE);
        debitCard.setCardNumber("6000333344445555");
        debitCard.setExpiryDate(LocalDate.now().plusYears(1));
        debitCard.setDailyLimit(new BigDecimal("500"));
        debitCard.setMonthlyLimit(new BigDecimal("2000"));
        debitCard.setCurrentDailySpending(new BigDecimal("10"));
        debitCard.setCurrentMonthlySpending(new BigDecimal("50"));
        topUpCard = new Card();
        topUpCard.setId(topUpCardId);
        topUpCard.setOwner(testUser);
        topUpCard.setBalance(new BigDecimal("50.00"));
        topUpCard.setStatus(CardStatus.ACTIVE);
        topUpCard.setCardNumber("7000444455556666");
        topUpCard.setExpiryDate(LocalDate.now().plusYears(1));

    }

    

    @Test
    void transferBetweenCards_Success() {
        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardService.findCardById(sendCardId)).thenReturn(sendCard);
        when(cardService.findCardById(receiveCardId)).thenReturn(receiveCard);
        doNothing().when(cardService).updateCard(any(Card.class));
        assertDoesNotThrow(() -> transactionService.transferBetweenCards(sendCardId, receiveCardId, transferAmount, testEmail));
        verify(userService).getUserByEmail(testEmail);
        verify(cardService).findCardById(sendCardId);
        verify(cardService).findCardById(receiveCardId);
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardService, times(2)).updateCard(cardCaptor.capture());
        List<Card> updatedCards = cardCaptor.getAllValues();
        Card updatedSendCard = updatedCards.stream().filter(c -> c.getId().equals(sendCardId)).findFirst().orElseThrow();
        Card updatedReceiveCard = updatedCards.stream().filter(c -> c.getId().equals(receiveCardId)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("400.00"), updatedSendCard.getBalance()); 
        assertEquals(new BigDecimal("300.00"), updatedReceiveCard.getBalance());
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction savedTransaction = transactionCaptor.getValue();
        assertEquals(transferAmount, savedTransaction.getAmount());
        assertEquals(TransactionType.TRANSFER, savedTransaction.getType());
        assertEquals(sendCard, savedTransaction.getSendCard());
        assertEquals(receiveCard, savedTransaction.getReceiveCard());
    }

    @Test
    void transferBetweenCards_Fail_NegativeAmount() {
        BigDecimal negativeAmount = new BigDecimal("-100");
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            transactionService.transferBetweenCards(sendCardId, receiveCardId, negativeAmount, testEmail);
        });
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("amount must be positive"));
        verifyNoInteractions(userService, cardService, transactionRepository); 
    }

    @Test
    void transferBetweenCards_Fail_UserNotFound() {
        when(userService.getUserByEmail(testEmail)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            transactionService.transferBetweenCards(sendCardId, receiveCardId, transferAmount, testEmail);
        });
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(userService).getUserByEmail(testEmail);
        verifyNoInteractions(cardService, transactionRepository);
    }

    @Test
    void transferBetweenCards_Fail_SenderCardNotFound() {
        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardService.findCardById(sendCardId)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            transactionService.transferBetweenCards(sendCardId, receiveCardId, transferAmount, testEmail);
        });
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(userService).getUserByEmail(testEmail);
        verify(cardService).findCardById(sendCardId);
        verify(cardService, never()).findCardById(receiveCardId); 
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void transferBetweenCards_Fail_ReceiverCardNotFound() {
        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardService.findCardById(sendCardId)).thenReturn(sendCard);
        when(cardService.findCardById(receiveCardId)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            transactionService.transferBetweenCards(sendCardId, receiveCardId, transferAmount, testEmail);
        });
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(userService).getUserByEmail(testEmail);
        verify(cardService).findCardById(sendCardId);
        verify(cardService).findCardById(receiveCardId);
        verifyNoInteractions(transactionRepository);
    }


    @Test
    void transferBetweenCards_Fail_UserNotOwner() {
        AppUser anotherUser = new AppUser(); 
        anotherUser.setId(UUID.randomUUID());
        receiveCard.setOwner(anotherUser);
        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardService.findCardById(sendCardId)).thenReturn(sendCard);
        when(cardService.findCardById(receiveCardId)).thenReturn(receiveCard);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            transactionService.transferBetweenCards(sendCardId, receiveCardId, transferAmount, testEmail);
        });
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertTrue(exception.getReason().contains("not allowed to perform transfers"));
        verify(userService).getUserByEmail(testEmail);
        verify(cardService).findCardById(sendCardId);
        verify(cardService).findCardById(receiveCardId);
        verify(cardService, never()).updateCard(any());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void transferBetweenCards_Fail_SenderCardBlocked() {
        sendCard.setStatus(CardStatus.BLOCKED); 
        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardService.findCardById(sendCardId)).thenReturn(sendCard);
        when(cardService.findCardById(receiveCardId)).thenReturn(receiveCard);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            
            transactionService.transferBetweenCards(sendCardId, receiveCardId, transferAmount, testEmail);
        });
        assertEquals(HttpStatus.LOCKED, exception.getStatusCode());
        assertTrue(exception.getReason().contains("The card is blocked"));
        verify(userService).getUserByEmail(testEmail);
        verify(cardService).findCardById(sendCardId);
        verify(cardService).findCardById(receiveCardId);
        verify(cardService, never()).updateCard(any());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void transferBetweenCards_Fail_ReceiverCardExpired() {
        receiveCard.setStatus(CardStatus.EXPIRED); 
        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardService.findCardById(sendCardId)).thenReturn(sendCard);
        when(cardService.findCardById(receiveCardId)).thenReturn(receiveCard);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            transactionService.transferBetweenCards(sendCardId, receiveCardId, transferAmount, testEmail);
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
        assertTrue(exception.getReason().contains("The receiving card has expired"));
        verify(userService).getUserByEmail(testEmail);
        verify(cardService).findCardById(sendCardId);
        verify(cardService).findCardById(receiveCardId);
        verify(cardService, never()).updateCard(any());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void transferBetweenCards_Fail_InsufficientFunds() {
        sendCard.setBalance(new BigDecimal("50.00")); 
        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardService.findCardById(sendCardId)).thenReturn(sendCard);
        when(cardService.findCardById(receiveCardId)).thenReturn(receiveCard);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            transactionService.transferBetweenCards(sendCardId, receiveCardId, transferAmount, testEmail);
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Insufficient funds"));
        verify(userService).getUserByEmail(testEmail);
        verify(cardService).findCardById(sendCardId);
        verify(cardService).findCardById(receiveCardId);
        verify(cardService, never()).updateCard(any());
        verifyNoInteractions(transactionRepository);
    }


    

    @Test
    void debitFromCard_Success() {
        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardService.findCardById(debitCardId)).thenReturn(debitCard);
        doNothing().when(cardService).updateCard(any(Card.class));
        assertDoesNotThrow(() -> transactionService.debitFromCard(debitCardId, debitAmount, testEmail));
        verify(userService).getUserByEmail(testEmail);
        verify(cardService).findCardById(debitCardId);
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardService).updateCard(cardCaptor.capture());
        Card updatedCard = cardCaptor.getValue();
        assertEquals(new BigDecimal("950.00"), updatedCard.getBalance()); 
        assertEquals(new BigDecimal("60.00"), updatedCard.getCurrentDailySpending()); 
        assertEquals(new BigDecimal("100.00"), updatedCard.getCurrentMonthlySpending());
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction savedTransaction = transactionCaptor.getValue();
        assertEquals(debitAmount, savedTransaction.getAmount());
        assertEquals(TransactionType.DEBIT, savedTransaction.getType());
        assertEquals(debitCard, savedTransaction.getSendCard());
        assertNull(savedTransaction.getReceiveCard()); 
    }

    @Test
    void debitFromCard_Fail_UserNotOwner() {
        AppUser anotherUser = new AppUser();
        anotherUser.setId(UUID.randomUUID());
        debitCard.setOwner(anotherUser);
        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardService.findCardById(debitCardId)).thenReturn(debitCard);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            transactionService.debitFromCard(debitCardId, debitAmount, testEmail);
        });
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertTrue(exception.getReason().contains("You are not owner"));
        verify(cardService, never()).updateCard(any());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void debitFromCard_Fail_DailyLimitExceeded() {
        debitCard.setCurrentDailySpending(new BigDecimal("480.00")); 
        BigDecimal largeDebitAmount = new BigDecimal("30.00");
        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardService.findCardById(debitCardId)).thenReturn(debitCard);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            transactionService.debitFromCard(debitCardId, largeDebitAmount, testEmail);
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
        assertTrue(exception.getReason().contains("exceeds the daily limit"));
        verify(cardService, never()).updateCard(any());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void debitFromCard_Fail_MonthlyLimitExceeded() {
        debitCard.setCurrentMonthlySpending(new BigDecimal("1980.00")); 
        BigDecimal largeDebitAmount = new BigDecimal("30.00");
        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardService.findCardById(debitCardId)).thenReturn(debitCard);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            transactionService.debitFromCard(debitCardId, largeDebitAmount, testEmail);
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
        assertTrue(exception.getReason().contains("exceeds the monthly limit"));
        verify(cardService, never()).updateCard(any());
        verifyNoInteractions(transactionRepository);
    }

    

    

    @Test
    void topUpCard_Success() {
        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardService.findCardById(topUpCardId)).thenReturn(topUpCard);
        doNothing().when(cardService).updateCard(any(Card.class));
        assertDoesNotThrow(() -> transactionService.topUpCard(topUpCardId, topUpAmount, testEmail));
        verify(userService).getUserByEmail(testEmail);
        verify(cardService).findCardById(topUpCardId);
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardService).updateCard(cardCaptor.capture());
        Card updatedCard = cardCaptor.getValue();
        assertEquals(new BigDecimal("250.00"), updatedCard.getBalance());
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction savedTransaction = transactionCaptor.getValue();
        assertEquals(topUpAmount, savedTransaction.getAmount());
        assertEquals(TransactionType.CREDIT, savedTransaction.getType());
        assertNull(savedTransaction.getSendCard()); 
        assertEquals(topUpCard, savedTransaction.getReceiveCard());
    }

    @Test
    void topUpCard_Fail_UserNotOwner() {
        AppUser anotherUser = new AppUser();
        anotherUser.setId(UUID.randomUUID());
        topUpCard.setOwner(anotherUser);
        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardService.findCardById(topUpCardId)).thenReturn(topUpCard);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            transactionService.topUpCard(topUpCardId, topUpAmount, testEmail);
        });
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertTrue(exception.getReason().contains("You are not owner"));
        verify(cardService, never()).updateCard(any());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void topUpCard_Fail_CardBlocked() {
        topUpCard.setStatus(CardStatus.BLOCKED);
        when(userService.getUserByEmail(testEmail)).thenReturn(testUser);
        when(cardService.findCardById(topUpCardId)).thenReturn(topUpCard);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            transactionService.topUpCard(topUpCardId, topUpAmount, testEmail);
        });
        assertEquals(HttpStatus.LOCKED, exception.getStatusCode());
        assertTrue(exception.getReason().contains("The receiving card is blocked"));
        verify(cardService, never()).updateCard(any());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void verifyingPossibilityOfTransaction_Debit_Success() {
        assertDoesNotThrow(() -> transactionService.verifyingPossibilityOfTransaction(debitCard, debitAmount, TransactionType.DEBIT));
    }

    @Test
    void verifyingPossibilityOfTransaction_Transfer_Success() {
        sendCard.setDailyLimit(new BigDecimal("100")); 
        sendCard.setCurrentDailySpending(BigDecimal.ZERO);
        assertDoesNotThrow(() -> transactionService.verifyingPossibilityOfTransaction(sendCard, transferAmount, TransactionType.TRANSFER));
    }

    @Test
    void verifyingPossibilityOfTransaction_Fail_Blocked() {
        sendCard.setStatus(CardStatus.BLOCKED);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> {
            transactionService.verifyingPossibilityOfTransaction(sendCard, transferAmount, TransactionType.TRANSFER);
        });
        assertEquals(HttpStatus.LOCKED, e.getStatusCode());
        assertTrue(e.getReason().contains("The card is blocked"));
    }

    @Test
    void verifyingPossibilityOfTransaction_Fail_Expired() {
        sendCard.setStatus(CardStatus.EXPIRED);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> {
            transactionService.verifyingPossibilityOfTransaction(sendCard, transferAmount, TransactionType.TRANSFER);
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertTrue(e.getReason().contains("The card has expired"));
    }

    @Test
    void verifyingPossibilityOfTransaction_Fail_InsufficientFunds() {
        sendCard.setBalance(new BigDecimal("50"));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> {
            transactionService.verifyingPossibilityOfTransaction(sendCard, transferAmount, TransactionType.TRANSFER);
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertTrue(e.getReason().contains("Insufficient funds"));
    }

    @Test
    void verifyingPossibilityOfTransaction_Fail_DailyLimitExceeded_ForDebit() {
        debitCard.setCurrentDailySpending(new BigDecimal("480")); 
        BigDecimal amount = new BigDecimal("30"); 
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> {
            transactionService.verifyingPossibilityOfTransaction(debitCard, amount, TransactionType.DEBIT);
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertTrue(e.getReason().contains("exceeds the daily limit"));
    }

    @Test
    void verifyingPossibilityOfTransaction_Success_DailyLimitExceeded_ForTransfer() {
        sendCard.setDailyLimit(new BigDecimal("100"));
        sendCard.setCurrentDailySpending(new BigDecimal("80"));
        BigDecimal amount = new BigDecimal("30"); 
        assertDoesNotThrow(() -> transactionService.verifyingPossibilityOfTransaction(sendCard, amount, TransactionType.TRANSFER));
    }

    @Test
    void verifyingPossibilityOfTransaction_Fail_MonthlyLimitExceeded_ForDebit() {
        debitCard.setCurrentMonthlySpending(new BigDecimal("1980")); 
        BigDecimal amount = new BigDecimal("30"); 
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> {
            transactionService.verifyingPossibilityOfTransaction(debitCard, amount, TransactionType.DEBIT);
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertTrue(e.getReason().contains("exceeds the monthly limit"));
    }

    
    @Test
    void verifyingPossibilityOfTransaction_Receive_Success() {
        assertDoesNotThrow(() -> transactionService.verifyingPossibilityOfTransaction(receiveCard));
    }

    @Test
    void verifyingPossibilityOfTransaction_Receive_Fail_Blocked() {
        receiveCard.setStatus(CardStatus.BLOCKED);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> {
            transactionService.verifyingPossibilityOfTransaction(receiveCard);
        });
        assertEquals(HttpStatus.LOCKED, e.getStatusCode());
        assertTrue(e.getReason().contains("The receiving card is blocked"));
    }

    @Test
    void verifyingPossibilityOfTransaction_Receive_Fail_Expired() {
        receiveCard.setStatus(CardStatus.EXPIRED);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> {
            transactionService.verifyingPossibilityOfTransaction(receiveCard);
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        assertTrue(e.getReason().contains("The receiving card has expired"));
    }


    

    @Test
    void getTransactions_Success_NoFilters() {
        Transaction tx1 = new Transaction(UUID.randomUUID(), sendCard, receiveCard, LocalDateTime.now().minusDays(1), new BigDecimal("100"), TransactionType.TRANSFER);
        Transaction tx2 = new Transaction(UUID.randomUUID(), null, topUpCard, LocalDateTime.now().minusHours(5), new BigDecimal("50"), TransactionType.CREDIT);
        List<Transaction> transactions = List.of(tx1, tx2);
        Page<Transaction> transactionPage = new PageImpl<>(transactions, PageRequest.of(0, 10), transactions.size());
        TransactionFilter filter = new TransactionFilter();
        Pageable pageable = PageRequest.of(0, 10);
        when(transactionRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(transactionPage);
        when(cardEncryptionService.maskCardNumber(sendCard.getCardNumber())).thenReturn(maskedSendCardNumber);
        when(cardEncryptionService.maskCardNumber(receiveCard.getCardNumber())).thenReturn(maskedReceiveCardNumber);
        when(cardEncryptionService.maskCardNumber(topUpCard.getCardNumber())).thenReturn("7000********6666");
        List<TransactionDto> result = transactionService.getTransactions(null, filter, pageable);
        assertEquals(2, result.size());
        verify(transactionRepository).findAll(any(Specification.class), eq(pageable));
        verify(userService, never()).getUserByEmail(anyString());
        TransactionDto dto1 = result.stream().filter(d -> d.getId().equals(tx1.getId())).findFirst().orElseThrow();
        assertEquals(tx1.getAmount(), dto1.getAmount());
        assertEquals(tx1.getType(), dto1.getType());
        assertEquals(maskedSendCardNumber, dto1.getSendCardNumber());
        assertEquals(maskedReceiveCardNumber, dto1.getReceiveCardNumber());
        assertEquals(tx1.getCreatedAt(), dto1.getCreatedAt());
        TransactionDto dto2 = result.stream().filter(d -> d.getId().equals(tx2.getId())).findFirst().orElseThrow();
        assertEquals(tx2.getAmount(), dto2.getAmount());
        assertEquals(tx2.getType(), dto2.getType());
        assertNull(dto2.getSendCardNumber());
        assertEquals("7000********6666", dto2.getReceiveCardNumber());
        assertEquals(tx2.getCreatedAt(), dto2.getCreatedAt());
    }

    @Test
    void getTransactions_Success_WithUserFilter() {
        Transaction tx1 = new Transaction(UUID.randomUUID(), sendCard, null, LocalDateTime.now().minusDays(1), new BigDecimal("100"), TransactionType.DEBIT); 
        List<Transaction> transactions = List.of(tx1);
        Page<Transaction> transactionPage = new PageImpl<>(transactions, PageRequest.of(0, 10), transactions.size());
        TransactionFilter filter = new TransactionFilter();
        Pageable pageable = PageRequest.of(0, 10);
        when(userService.getUserByEmail(testEmail)).thenReturn(testUser); 
        when(transactionRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(transactionPage);
        when(cardEncryptionService.maskCardNumber(sendCard.getCardNumber())).thenReturn(maskedSendCardNumber);
        List<TransactionDto> result = transactionService.getTransactions(testEmail, filter, pageable);
        assertEquals(1, result.size());
        verify(userService).getUserByEmail(testEmail); 
        verify(transactionRepository).findAll(any(Specification.class), eq(pageable));
        assertEquals(tx1.getId(), result.get(0).getId());
        assertEquals(maskedSendCardNumber, result.get(0).getSendCardNumber());
        assertNull(result.get(0).getReceiveCardNumber());
    }

    @Test
    void getTransactions_Success_WithCardFilter() {
        Transaction tx1 = new Transaction(UUID.randomUUID(), sendCard, receiveCard, LocalDateTime.now().minusDays(1), new BigDecimal("100"), TransactionType.TRANSFER); 
        List<Transaction> transactions = List.of(tx1);
        Page<Transaction> transactionPage = new PageImpl<>(transactions, PageRequest.of(0, 10), transactions.size());
        TransactionFilter filter = new TransactionFilter();
        filter.setCardId(sendCardId); 
        Pageable pageable = PageRequest.of(0, 10);
        when(transactionRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(transactionPage);
        when(cardEncryptionService.maskCardNumber(sendCard.getCardNumber())).thenReturn(maskedSendCardNumber);
        when(cardEncryptionService.maskCardNumber(receiveCard.getCardNumber())).thenReturn(maskedReceiveCardNumber);
        List<TransactionDto> result = transactionService.getTransactions(null, filter, pageable);
        assertEquals(1, result.size());
        verify(transactionRepository).findAll(any(Specification.class), eq(pageable));
        assertEquals(tx1.getId(), result.get(0).getId());
        assertEquals(maskedSendCardNumber, result.get(0).getSendCardNumber());
    }

    @Test
    void getTransactions_Success_EmptyResult() {
        Page<Transaction> emptyPage = Page.empty(PageRequest.of(0, 10));
        TransactionFilter filter = new TransactionFilter();
        Pageable pageable = PageRequest.of(0, 10);
        when(transactionRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);
        List<TransactionDto> result = transactionService.getTransactions(null, filter, pageable);
        assertTrue(result.isEmpty());
        verify(transactionRepository).findAll(any(Specification.class), eq(pageable));
        verifyNoInteractions(cardEncryptionService); 
    }

    @Test
    void getTransactions_Fail_UserNotFoundForFilter() {
        TransactionFilter filter = new TransactionFilter();
        Pageable pageable = PageRequest.of(0, 10);
        when(userService.getUserByEmail(testEmail)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            transactionService.getTransactions(testEmail, filter, pageable);
        });
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(userService).getUserByEmail(testEmail);
        verifyNoInteractions(transactionRepository, cardEncryptionService); 
    }


    
    @Test
    void transactionToDto_MapsCorrectly() {
        Transaction transaction = new Transaction(UUID.randomUUID(), sendCard, receiveCard, LocalDateTime.now(), transferAmount, TransactionType.TRANSFER);
        when(cardEncryptionService.maskCardNumber(sendCard.getCardNumber())).thenReturn(maskedSendCardNumber);
        when(cardEncryptionService.maskCardNumber(receiveCard.getCardNumber())).thenReturn(maskedReceiveCardNumber);
        TransactionDto dto = transactionService.transactionToDto(transaction);
        assertEquals(transaction.getId(), dto.getId());
        assertEquals(transaction.getAmount(), dto.getAmount());
        assertEquals(transaction.getType(), dto.getType());
        assertEquals(transaction.getCreatedAt(), dto.getCreatedAt());
        assertEquals(maskedSendCardNumber, dto.getSendCardNumber());
        assertEquals(maskedReceiveCardNumber, dto.getReceiveCardNumber());
    }

    @Test
    void transactionToDto_MapsDebitCorrectly() {
        Transaction transaction = new Transaction(UUID.randomUUID(), debitCard, null, LocalDateTime.now(), debitAmount, TransactionType.DEBIT);
        when(cardEncryptionService.maskCardNumber(debitCard.getCardNumber())).thenReturn("6000********5555");
        TransactionDto dto = transactionService.transactionToDto(transaction);
        assertEquals(transaction.getId(), dto.getId());
        assertEquals(transaction.getAmount(), dto.getAmount());
        assertEquals(transaction.getType(), dto.getType());
        assertEquals(transaction.getCreatedAt(), dto.getCreatedAt());
        assertEquals("6000********5555", dto.getSendCardNumber());
        assertNull(dto.getReceiveCardNumber()); 
    }

    @Test
    void transactionToDto_MapsCreditCorrectly() {
        Transaction transaction = new Transaction(UUID.randomUUID(), null, topUpCard, LocalDateTime.now(), topUpAmount, TransactionType.CREDIT);
        when(cardEncryptionService.maskCardNumber(topUpCard.getCardNumber())).thenReturn("7000********6666");
        TransactionDto dto = transactionService.transactionToDto(transaction);
        assertEquals(transaction.getId(), dto.getId());
        assertEquals(transaction.getAmount(), dto.getAmount());
        assertEquals(transaction.getType(), dto.getType());
        assertEquals(transaction.getCreatedAt(), dto.getCreatedAt());
        assertNull(dto.getSendCardNumber()); 
        assertEquals("7000********6666", dto.getReceiveCardNumber());
    }
}
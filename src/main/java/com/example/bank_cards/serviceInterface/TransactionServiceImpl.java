package com.example.bank_cards.serviceInterface;

import com.example.bank_cards.dto.TransactionDto;
import com.example.bank_cards.dto.TransactionFilter;
import com.example.bank_cards.enums.TransactionType;
import com.example.bank_cards.model.Card;
import com.example.bank_cards.model.Transaction;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TransactionServiceImpl {
    void transferBetweenCards(UUID sendCardId, UUID receiveCardId, BigDecimal amount, String email);

    void debitFromCard(UUID cardId, BigDecimal amount, String email);

    void topUpCard(UUID cardId, BigDecimal amount, String email);

    void verifyingPossibilityOfTransaction(Card card, BigDecimal transactionAmount, TransactionType transactionType);

    List<TransactionDto> getTransactions(String userEmail, TransactionFilter filter, Pageable pageable);

    void verifyingPossibilityOfTransaction(Card card);

    TransactionDto transactionToDto(Transaction transaction);
}

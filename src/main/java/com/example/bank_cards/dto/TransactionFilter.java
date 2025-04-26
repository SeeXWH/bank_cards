package com.example.bank_cards.dto;

import com.example.bank_cards.enums.TransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TransactionFilter {
    private UUID cardId;
    private TransactionType type;
    private BigDecimal amountFrom;
    private BigDecimal amountTo;
    private LocalDateTime createdAtFrom;
    private LocalDateTime createdAtTo;
}
package com.example.bank_cards.dto;

import com.example.bank_cards.enums.TransactionType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TransactionDto {
    private UUID id;
    @Enumerated(EnumType.STRING)
    private TransactionType type;
    private String sendCardNumber;
    private String receiveCardNumber;
    private BigDecimal amount;
    private LocalDateTime createdAt;
}

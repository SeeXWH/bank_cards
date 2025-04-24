package com.example.bank_cards.dto;

import com.example.bank_cards.enums.CardStatus;
import jakarta.persistence.Embedded;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CardDto
{
    private UUID id;
    private String cardNumber;
    private String ownerName;
    private BigDecimal balance;
    private LocalDate expiryDate;
    private CardStatus status;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
}

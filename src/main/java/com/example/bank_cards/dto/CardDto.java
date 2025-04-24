package com.example.bank_cards.dto;

import com.example.bank_cards.enums.CardStatus;
import com.example.bank_cards.model.CardLimit;
import jakarta.persistence.Embedded;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CardDto
{
    private String cardNumber;
    private String ownerName;
    private BigDecimal balance;
    private LocalDate expiryDate;
    private CardStatus status;
    @Embedded
    private CardLimit cardLimit;
}

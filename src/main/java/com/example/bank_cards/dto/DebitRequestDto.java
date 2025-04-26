package com.example.bank_cards.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class DebitRequestDto {

    private UUID cardId;

    private BigDecimal amount;
}

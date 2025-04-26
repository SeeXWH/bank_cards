package com.example.bank_cards.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TopUpRequestDto {
    private UUID cardId;

    private BigDecimal amount;
}

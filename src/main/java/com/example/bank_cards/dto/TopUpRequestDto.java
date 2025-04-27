package com.example.bank_cards.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TopUpRequestDto {
    @NotNull(message = "id cart cannot be null or empty")
    private UUID cardId;
    @NotNull(message = "amount cannot be nul ")
    @Positive(message = "amount cannot be negative or zero")
    private BigDecimal amount;
}

package com.example.bank_cards.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class DebitRequestDto {

    @NotNull(message = "id cart cannot be null or empty")
    private UUID cardId;
    @NotNull(message = "amount cannot be nul ")
    @Positive(message = "amount cannot be negative or zero")
    private BigDecimal amount;
}

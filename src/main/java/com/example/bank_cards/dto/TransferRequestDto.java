package com.example.bank_cards.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransferRequestDto {

    @NotNull(message = "id send cart cannot be null or empty")
    private UUID sendCardId;
    @NotNull(message = "id receive cart cannot be null or empty")
    private UUID receiveCardId;
    @NotNull(message = "amount cannot be nul ")
    @Positive(message = "amount cannot be negative or zero")
    private BigDecimal amount;
}

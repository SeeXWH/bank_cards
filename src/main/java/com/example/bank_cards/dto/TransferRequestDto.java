package com.example.bank_cards.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransferRequestDto {
    private UUID sendCardId;

    private UUID receiveCardId;

    private BigDecimal amount;
}

package com.example.bank_cards.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BlockCardRequestDto {
    @NotBlank(message = "card number cannot be null or empty")
    private String cardNumber;
}

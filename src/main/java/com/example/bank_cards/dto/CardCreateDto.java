package com.example.bank_cards.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CardCreateDto {
    @NotEmpty(message = "email cannot be null or empty")
    @Email(message = "email must be email format")
    private String email;
    @NotNull(message = "expireDate cannot be null or empty")
    @Future(message = "expireDate must be in future")
    private LocalDate expiryDate;
}

package com.example.bank_cards.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardLimitDto {

    @DecimalMin(value = "0.0", message = "Daily limit must be non-negative")
    private BigDecimal dailyLimit;
    @DecimalMin(value = "0.0", message = "Monthly limit must be non-negative")
    private BigDecimal monthlyLimit;
}

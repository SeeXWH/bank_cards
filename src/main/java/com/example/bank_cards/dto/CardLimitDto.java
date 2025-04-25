package com.example.bank_cards.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardLimitDto {
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
}

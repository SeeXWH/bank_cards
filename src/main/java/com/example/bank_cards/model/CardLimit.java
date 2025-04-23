package com.example.bank_cards.model;

import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
public class CardLimit {
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
}

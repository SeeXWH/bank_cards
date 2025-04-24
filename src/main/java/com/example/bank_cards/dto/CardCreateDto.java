package com.example.bank_cards.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CardCreateDto {
    private String email;
    private LocalDate expiryDate;
}

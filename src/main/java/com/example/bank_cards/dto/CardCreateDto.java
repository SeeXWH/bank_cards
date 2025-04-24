package com.example.bank_cards.dto;

import com.example.bank_cards.model.CardLimit;
import jakarta.persistence.Embedded;
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

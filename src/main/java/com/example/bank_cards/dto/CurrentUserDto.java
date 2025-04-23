package com.example.bank_cards.dto;

import com.example.bank_cards.enums.Role;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CurrentUserDto {
    private String name;

    private String email;

    @Enumerated(EnumType.STRING)
    private Role role;
}

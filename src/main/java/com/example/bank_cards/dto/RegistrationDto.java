package com.example.bank_cards.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegistrationDto {
    @NotBlank(message = "name cannot be null or empty")
    private String name;
    @NotBlank(message = "email cannot be null or empty")
    @Email(message = "Email should be a valid email address format")
    private String email;
    @NotBlank(message = "password cannot be null or empty")
    private String password;
}

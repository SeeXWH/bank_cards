package com.example.bank_cards.serviceInterface;

import com.example.bank_cards.dto.LoginDto;
import com.example.bank_cards.dto.RegistrationDto;
import com.example.bank_cards.enums.Role;
import com.example.bank_cards.model.AppUser;

public interface UserServiceImpl {
    String registerUser(RegistrationDto user);

    String authenticateUser(LoginDto user);

    AppUser getUserByEmail(String email);

    void changeRoleUser(String email, Role role);

    void lockUser(String email);

    void unlockUser(String email);
}

package com.example.bank_cards.service;

import org.jasypt.util.text.AES256TextEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CardEncryptionService {

    private final AES256TextEncryptor encryptor;

    public CardEncryptionService(@Value("${jasypt.encryptor.password}") String password) {
        this.encryptor = new AES256TextEncryptor();
        this.encryptor.setPassword(password);
    }

    public String encryptCardNumber(String cardNumber) {
        if (!cardNumber.matches("\\d{16}")) {
            throw new IllegalArgumentException("Card number must be 16 digits");
        }
        return encryptor.encrypt(cardNumber);
    }

    public String decryptCardNumber(String encryptedCardNumber) {
        return encryptor.decrypt(encryptedCardNumber);
    }

    public String maskCardNumber(String cardNumber) {
        String decrypted = decryptCardNumber(cardNumber);
        return decrypted.substring(0, 4) + "******" + decrypted.substring(12);
    }
}
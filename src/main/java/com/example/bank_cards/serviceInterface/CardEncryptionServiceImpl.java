package com.example.bank_cards.serviceInterface;


public interface CardEncryptionServiceImpl {


    String encryptCardNumber(String cardNumber);

    String decryptCardNumber(String encryptedCardNumber);

    String maskCardNumber(String encryptedCardNumber);
}
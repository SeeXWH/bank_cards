package com.example.bank_cards.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Service
@Slf4j
public class CardEncryptionService {

    private final SecretKeySpec key;

    public CardEncryptionService(@Value("${encryption.secret}") String secret) {
        byte[] keyBytes = new byte[32];
        byte[] secretBytes = secret.getBytes();
        System.arraycopy(secretBytes, 0, keyBytes, 0, Math.min(secretBytes.length, keyBytes.length));
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encryptCardNumber(String cardNumber) {
        if (!cardNumber.matches("\\d{16}")) {
            throw new IllegalArgumentException("Card number must be 16 digits");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(cardNumber.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.warn("error encrypting card number {}", e.getMessage());
            throw new RuntimeException("Encryption error", e);
        }
    }

    public String decryptCardNumber(String encryptedCardNumber) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedCardNumber));
            return new String(decrypted);
        } catch (Exception e) {
            log.warn("error decrypting card number {}", e.getMessage());
            throw new RuntimeException("Decryption error", e);
        }
    }

    public String maskCardNumber(String encryptedCardNumber) {
        String decrypted = decryptCardNumber(encryptedCardNumber);
        return decrypted.substring(0, 4) + "******" + decrypted.substring(12);
    }
}

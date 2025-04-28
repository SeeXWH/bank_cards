package com.example.bank_cards.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.BadPaddingException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CardEncryptionServiceTest {

    private CardEncryptionService cardEncryptionService;
    private final String testSecret = "ThisIsAValid32ByteSecretKeyYeah"; 
    private final String validCardNumber = "1234567890123456";
    private String encryptedValidCardNumber;

    @BeforeEach
    void setUp() {
        cardEncryptionService = new CardEncryptionService(testSecret);
        encryptedValidCardNumber = cardEncryptionService.encryptCardNumber(validCardNumber);
    }

    

    @Test
    void encryptCardNumber_Success() {
        String encrypted = cardEncryptionService.encryptCardNumber(validCardNumber);
        assertNotNull(encrypted);
        assertFalse(encrypted.isEmpty());
        assertNotEquals(validCardNumber, encrypted);
        assertDoesNotThrow(() -> Base64.getDecoder().decode(encrypted));
        String decrypted = cardEncryptionService.decryptCardNumber(encrypted);
        assertEquals(validCardNumber, decrypted);
    }

    @Test
    void encryptCardNumber_Fail_NullInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            cardEncryptionService.encryptCardNumber(null);
        });
        assertEquals("Card number must be exactly 16 digits", exception.getMessage());
    }

    @Test
    void encryptCardNumber_Fail_TooShort() {
        String shortNumber = "123456789012345"; 
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            cardEncryptionService.encryptCardNumber(shortNumber);
        });
        assertEquals("Card number must be exactly 16 digits", exception.getMessage());
    }

    @Test
    void encryptCardNumber_Fail_TooLong() {
        String longNumber = "12345678901234567"; 
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            cardEncryptionService.encryptCardNumber(longNumber);
        });
        assertEquals("Card number must be exactly 16 digits", exception.getMessage());
    }

    @Test
    void encryptCardNumber_Fail_NonDigits() {
        String nonDigitNumber = "123456789012345A";
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            cardEncryptionService.encryptCardNumber(nonDigitNumber);
        });
        assertEquals("Card number must be exactly 16 digits", exception.getMessage());
    }

    

    @Test
    void decryptCardNumber_Success() {
        String decrypted = cardEncryptionService.decryptCardNumber(encryptedValidCardNumber);
        assertEquals(validCardNumber, decrypted);
    }

    @Test
    void decryptCardNumber_Fail_NullInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            cardEncryptionService.decryptCardNumber(null);
        });
        assertEquals("Encrypted card number cannot be null or empty", exception.getMessage());
    }

    @Test
    void decryptCardNumber_Fail_EmptyInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            cardEncryptionService.decryptCardNumber("");
        });
        assertEquals("Encrypted card number cannot be null or empty", exception.getMessage());
    }

    @Test
    void decryptCardNumber_Fail_BlankInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            cardEncryptionService.decryptCardNumber("   ");
        });
        assertEquals("Encrypted card number cannot be null or empty", exception.getMessage());
    }


    @Test
    void decryptCardNumber_Fail_InvalidBase64() {
        String invalidBase64 = "ThisIsNotValidBase64!";
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            cardEncryptionService.decryptCardNumber(invalidBase64);
        });
        assertTrue(exception.getMessage().contains("Decryption error occurred"));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void decryptCardNumber_Fail_CorruptedDataOrWrongKey() {
        String corruptedData = encryptedValidCardNumber.substring(0, encryptedValidCardNumber.length() - 2) + "==";
        if (corruptedData.equals(encryptedValidCardNumber)) { 
            corruptedData = encryptedValidCardNumber.substring(1);
        }
        try{
            Base64.getDecoder().decode(corruptedData);
        } catch(IllegalArgumentException e){
            corruptedData = Base64.getEncoder().encodeToString("corrupted1234567".getBytes());
        }
        String finalCorruptedData = corruptedData; 
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            cardEncryptionService.decryptCardNumber(finalCorruptedData);
        });
        assertTrue(exception.getMessage().contains("Decryption error occurred"));
        assertTrue(exception.getCause() instanceof BadPaddingException ||
                exception.getCause() instanceof javax.crypto.IllegalBlockSizeException ||
                exception.getCause() instanceof IllegalArgumentException);
    }

    

    @Test
    void maskCardNumber_Success() {
        String masked = cardEncryptionService.maskCardNumber(encryptedValidCardNumber);
        assertEquals("1234******3456", masked);
    }

    @Test
    void maskCardNumber_Fail_DecryptsToShortString() {
        String invalidBase64 = "ThisIsNotValidBase64!";
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            cardEncryptionService.maskCardNumber(invalidBase64);
        });
        assertTrue(exception.getMessage().contains("Decryption error occurred"));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause()); 
    }

    @Test
    void maskCardNumber_Fail_NullInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            cardEncryptionService.maskCardNumber(null);
        });
        assertEquals("Encrypted card number cannot be null or empty", exception.getMessage());
    }

    @Test
    void maskCardNumber_Fail_EmptyInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            cardEncryptionService.maskCardNumber("");
        });
        assertEquals("Encrypted card number cannot be null or empty", exception.getMessage());
    }

}
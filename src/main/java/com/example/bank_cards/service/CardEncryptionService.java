package com.example.bank_cards.service;

import com.example.bank_cards.serviceInterface.CardEncryptionServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Сервис для шифрования, дешифрования и маскирования номеров банковских карт.
 * <p>
 * Использует симметричный алгоритм шифрования AES (режим ECB с дополнением PKCS5Padding)
 * с ключом, получаемым из конфигурационного свойства {@code encryption.secret}.
 * </p>
 */
@Service
@Slf4j
public class CardEncryptionService implements CardEncryptionServiceImpl {

    /**
     * Секретный ключ AES, используемый для шифрования и дешифрования.
     * Генерируется из свойства {@code encryption.secret}.
     */
    private final SecretKeySpec key;


    /**
     * Конструктор сервиса шифрования.
     * <p>
     * Инициализирует {@link SecretKeySpec} для алгоритма AES, используя
     * секретную строку, полученную из свойства приложения {@code encryption.secret}.
     * Используются первые 32 байта секрета для формирования ключа AES-256.
     * Если секрет короче 32 байт, ключ будет дополнен нулевыми байтами.
     * </p>
     *
     * @param secret Секретная строка из конфигурации ({@code encryption.secret}).
     */
    public CardEncryptionService(@Value("${encryption.secret}") String secret) {
        byte[] keyBytes = new byte[32]; 
        byte[] secretBytes = secret.getBytes();
        System.arraycopy(secretBytes, 0, keyBytes, 0, Math.min(secretBytes.length, keyBytes.length));
        this.key = new SecretKeySpec(keyBytes, "AES");
        log.info("CardEncryptionService initialized with AES key derived from provided secret.");
    }

    /**
     * Шифрует 16-значный номер банковской карты.
     * <p>
     * Проверяет, что входная строка состоит ровно из 16 цифр.
     * Использует алгоритм AES/ECB/PKCS5Padding для шифрования.
     * Результат кодируется в формат Base64.
     * </p>
     *
     * @param cardNumber Номер банковской карты в виде строки из 16 цифр.
     * @return Зашифрованный номер карты, закодированный в Base64.
     * @throws IllegalArgumentException если {@code cardNumber} не состоит из 16 цифр.
     * @throws RuntimeException         если происходит ошибка в процессе шифрования.
     */
    @Override
    public String encryptCardNumber(String cardNumber) {
        if (cardNumber == null || !cardNumber.matches("\\d{16}")) {
            log.error("Invalid card number format provided for encryption. Expected 16 digits.");
            throw new IllegalArgumentException("Card number must be exactly 16 digits");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(cardNumber.getBytes());
            String encoded = Base64.getEncoder().encodeToString(encrypted);
            log.trace("Card number encrypted successfully."); 
            return encoded;
        } catch (Exception e) {
            log.error("Error encrypting card number: {}", e.getMessage(), e);
            throw new RuntimeException("Encryption error occurred processing card number.", e);
        }
    }

    /**
     * Дешифрует зашифрованный номер банковской карты.
     * <p>
     * Принимает строку в формате Base64, декодирует её и дешифрует
     * с использованием алгоритма AES/ECB/PKCS5Padding и того же ключа,
     * который использовался для шифрования.
     * </p>
     *
     * @param encryptedCardNumber Зашифрованный номер карты в формате Base64.
     * @return Исходный (дешифрованный) номер карты.
     * @throws RuntimeException если происходит ошибка в процессе дешифрования (например,
     *                          неверный формат Base64, ошибка дополнения, неверный ключ).
     */
    @Override
    public String decryptCardNumber(String encryptedCardNumber) {
        if (encryptedCardNumber == null || encryptedCardNumber.trim().isEmpty()) {
            log.error("Encrypted card number provided for decryption is null or empty.");
            throw new IllegalArgumentException("Encrypted card number cannot be null or empty");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decoded = Base64.getDecoder().decode(encryptedCardNumber);
            byte[] decrypted = cipher.doFinal(decoded);
            String result = new String(decrypted);
            log.trace("Card number decrypted successfully.");
            return result;
        } catch (Exception e) {
            log.error("Error decrypting card number: {}", e.getMessage(), e);
            throw new RuntimeException("Decryption error occurred processing card number.", e);
        }
    }

    /**
     * Маскирует номер банковской карты, оставляя видимыми только первые 4 и последние 4 цифры.
     * <p>
     * Сначала дешифрует переданный зашифрованный номер карты, а затем
     * применяет маску формата "XXXX******XXXX".
     * </p>
     *
     * @param encryptedCardNumber Зашифрованный номер карты в формате Base64.
     * @return Маскированный номер карты (например, "1234******5678").
     * @throws RuntimeException если происходит ошибка в процессе дешифрования.
     * @throws IndexOutOfBoundsException если дешифрованный номер карты имеет длину менее 16 символов
     *                                   (что не должно происходить при корректном шифровании/дешифровании 16-значных номеров).
     */
    @Override
    public String maskCardNumber(String encryptedCardNumber) {
        log.trace("Attempting to mask card number.");
        String decrypted = decryptCardNumber(encryptedCardNumber); 
        if (decrypted.length() < 16) {
            log.error("Decrypted card number length is less than 16 ({}), cannot mask correctly.", decrypted.length());
            throw new IllegalStateException("Decrypted card number has unexpected length: " + decrypted.length());
        }
        String masked = decrypted.substring(0, 4) + "******" + decrypted.substring(decrypted.length() - 4);
        log.trace("Card number masked successfully.");
        return masked;
    }
}
package com.openride.user.service;

import com.openride.commons.exception.TechnicalException;
import com.openride.user.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encrypting and decrypting sensitive data using AES-256-GCM.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EncryptionService {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecurityProperties securityProperties;

    /**
     * Encrypts plaintext using AES-256-GCM.
     *
     * @param plaintext text to encrypt
     * @return base64-encoded ciphertext with IV prepended
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            SecretKey secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(securityProperties.getEncryption().getAlgorithm());
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage(), e);
            throw new TechnicalException("ENCRYPTION_FAILED", "Failed to encrypt data");
        }
    }

    /**
     * Decrypts ciphertext using AES-256-GCM.
     *
     * @param ciphertext base64-encoded ciphertext with IV prepended
     * @return decrypted plaintext
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return null;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] cipherBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherBytes);

            SecretKey secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(securityProperties.getEncryption().getAlgorithm());
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plaintext = cipher.doFinal(cipherBytes);

            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage(), e);
            throw new TechnicalException("DECRYPTION_FAILED", "Failed to decrypt data");
        }
    }

    /**
     * Gets the secret key from configuration.
     *
     * @return SecretKey instance
     */
    private SecretKey getSecretKey() {
        String key = securityProperties.getEncryption().getKey();
        if (key == null || key.length() != 32) {
            throw new TechnicalException(
                "INVALID_ENCRYPTION_KEY",
                "Encryption key must be exactly 32 characters"
            );
        }
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, "AES");
    }
}

package com.openride.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for EncryptionService.
 * Tests AES-256-GCM encryption and decryption.
 */
@SpringBootTest(classes = {EncryptionService.class})
@TestPropertySource(properties = {
    "security.encryption.key=12345678901234567890123456789012",
    "security.encryption.algorithm=AES/GCM/NoPadding"
})
class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @Value("${security.encryption.key}")
    private String encryptionKey;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService(encryptionKey, "AES/GCM/NoPadding");
    }

    @Test
    void encrypt_ValidInput_ReturnsEncryptedString() {
        // Given
        String plaintext = "1234567890";

        // When
        String encrypted = encryptionService.encrypt(plaintext);

        // Then
        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEmpty();
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(encrypted).isBase64();
    }

    @Test
    void decrypt_ValidEncryptedString_ReturnsOriginalPlaintext() {
        // Given
        String plaintext = "1234567890";
        String encrypted = encryptionService.encrypt(plaintext);

        // When
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encryptDecrypt_RoundTrip_PreservesData() {
        // Given
        String[] testCases = {
            "1234567890",
            "ABC-123-XYZ",
            "Special!@#$%Characters",
            "12345678901234567890123456789012345",
            "Short",
            ""
        };

        // When & Then
        for (String original : testCases) {
            String encrypted = encryptionService.encrypt(original);
            String decrypted = encryptionService.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(original);
        }
    }

    @Test
    void encrypt_SameInputMultipleTimes_ReturnsDifferentCiphertexts() {
        // Given
        String plaintext = "1234567890";

        // When
        String encrypted1 = encryptionService.encrypt(plaintext);
        String encrypted2 = encryptionService.encrypt(plaintext);
        String encrypted3 = encryptionService.encrypt(plaintext);

        // Then - Different IVs produce different ciphertexts
        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(encrypted2).isNotEqualTo(encrypted3);
        assertThat(encrypted1).isNotEqualTo(encrypted3);

        // But all decrypt to the same plaintext
        assertThat(encryptionService.decrypt(encrypted1)).isEqualTo(plaintext);
        assertThat(encryptionService.decrypt(encrypted2)).isEqualTo(plaintext);
        assertThat(encryptionService.decrypt(encrypted3)).isEqualTo(plaintext);
    }

    @Test
    void encrypt_NullInput_ReturnsNull() {
        // When
        String encrypted = encryptionService.encrypt(null);

        // Then
        assertThat(encrypted).isNull();
    }

    @Test
    void decrypt_NullInput_ReturnsNull() {
        // When
        String decrypted = encryptionService.decrypt(null);

        // Then
        assertThat(decrypted).isNull();
    }

    @Test
    void decrypt_InvalidBase64_ThrowsException() {
        // Given
        String invalidBase64 = "not-valid-base64!@#$";

        // When & Then
        assertThatThrownBy(() -> encryptionService.decrypt(invalidBase64))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void decrypt_TamperedCiphertext_ThrowsException() {
        // Given
        String plaintext = "1234567890";
        String encrypted = encryptionService.encrypt(plaintext);
        
        // Tamper with the ciphertext (flip a bit in the middle)
        byte[] bytes = java.util.Base64.getDecoder().decode(encrypted);
        bytes[bytes.length / 2] ^= 1; // Flip one bit
        String tampered = java.util.Base64.getEncoder().encodeToString(bytes);

        // When & Then - GCM authentication should fail
        assertThatThrownBy(() -> encryptionService.decrypt(tampered))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void encrypt_LongString_HandlesCorrectly() {
        // Given
        String longPlaintext = "A".repeat(1000);

        // When
        String encrypted = encryptionService.encrypt(longPlaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(longPlaintext);
    }

    @Test
    void encrypt_SpecialCharacters_HandlesCorrectly() {
        // Given
        String specialChars = "Hello 世界 🌍 \n\t\r Special@#$%^&*()";

        // When
        String encrypted = encryptionService.encrypt(specialChars);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(specialChars);
    }

    @Test
    void encrypt_BVNExample_WorksCorrectly() {
        // Given - Real-world BVN example
        String bvn = "12345678901";

        // When
        String encrypted = encryptionService.encrypt(bvn);

        // Then
        assertThat(encrypted).isNotEqualTo(bvn);
        assertThat(encrypted).doesNotContain(bvn);
        assertThat(encryptionService.decrypt(encrypted)).isEqualTo(bvn);
    }

    @Test
    void encrypt_LicenseNumberExample_WorksCorrectly() {
        // Given - Real-world license number example
        String license = "LAG-123-ABC-2024";

        // When
        String encrypted = encryptionService.encrypt(license);

        // Then
        assertThat(encrypted).isNotEqualTo(license);
        assertThat(encrypted).doesNotContain(license);
        assertThat(encryptionService.decrypt(encrypted)).isEqualTo(license);
    }
}

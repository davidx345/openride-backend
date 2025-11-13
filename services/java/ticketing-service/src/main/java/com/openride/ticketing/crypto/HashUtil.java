package com.openride.ticketing.crypto;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;

/**
 * SHA-256 hashing utility for ticket integrity verification.
 * 
 * Uses BouncyCastle provider for cryptographic operations.
 * All hashes are returned as hexadecimal strings.
 */
@Slf4j
public class HashUtil {

    private static final String HASH_ALGORITHM = "SHA-256";

    static {
        // Add BouncyCastle as security provider
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Compute SHA-256 hash of input string.
     * 
     * @param input the input string to hash
     * @return hexadecimal representation of hash
     * @throws RuntimeException if hashing fails
     */
    public static String sha256(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Failed to compute SHA-256 hash", e);
        }
    }

    /**
     * Compute SHA-256 hash of byte array.
     * 
     * @param input the input bytes to hash
     * @return hexadecimal representation of hash
     * @throws RuntimeException if hashing fails
     */
    public static String sha256(byte[] input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(input);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Failed to compute SHA-256 hash", e);
        }
    }

    /**
     * Compute double SHA-256 hash (Bitcoin-style).
     * Used for additional security in Merkle tree construction.
     * 
     * @param input the input string to hash
     * @return hexadecimal representation of double hash
     */
    public static String doubleSha256(String input) {
        String firstHash = sha256(input);
        return sha256(firstHash);
    }

    /**
     * Compute double SHA-256 hash of byte array.
     * 
     * @param input the input bytes to hash
     * @return hexadecimal representation of double hash
     */
    public static String doubleSha256(byte[] input) {
        String firstHash = sha256(input);
        return sha256(firstHash);
    }

    /**
     * Verify that a hash matches the expected value.
     * 
     * @param input the input to hash
     * @param expectedHash the expected hash value
     * @return true if hash matches
     */
    public static boolean verifyHash(String input, String expectedHash) {
        String computedHash = sha256(input);
        return computedHash.equalsIgnoreCase(expectedHash);
    }

    /**
     * Convert byte array to hexadecimal string.
     * 
     * @param bytes the byte array
     * @return hexadecimal string (lowercase)
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Convert hexadecimal string to byte array.
     * 
     * @param hex the hexadecimal string
     * @return byte array
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hex string");
        }

        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Combine two hashes for Merkle tree construction.
     * Uses concatenation + SHA-256.
     * 
     * @param left the left hash
     * @param right the right hash
     * @return combined hash
     */
    public static String combineHashes(String left, String right) {
        String combined = left + right;
        return sha256(combined);
    }
}

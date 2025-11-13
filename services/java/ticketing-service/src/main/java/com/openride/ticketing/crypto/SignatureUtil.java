package com.openride.ticketing.crypto;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * ECDSA signature utility for ticket signing and verification.
 * 
 * Uses secp256k1 curve (same as Bitcoin/Ethereum) with BouncyCastle provider.
 * Supports key generation, signing, verification, and PEM import/export.
 */
@Slf4j
public class SignatureUtil {

    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final String KEY_ALGORITHM = "EC";
    private static final String CURVE_NAME = "secp256k1";
    private static final String PROVIDER = BouncyCastleProvider.PROVIDER_NAME;

    static {
        // Add BouncyCastle as security provider
        if (Security.getProvider(PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Generate a new ECDSA key pair using secp256k1 curve.
     * 
     * @return new key pair
     * @throws RuntimeException if key generation fails
     */
    public static KeyPair generateKeyPair() {
        try {
            ECGenParameterSpec ecSpec = new ECGenParameterSpec(CURVE_NAME);
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(KEY_ALGORITHM, PROVIDER);
            keyGen.initialize(ecSpec, new SecureRandom());
            KeyPair keyPair = keyGen.generateKeyPair();
            
            log.info("Generated new ECDSA key pair using curve: {}", CURVE_NAME);
            return keyPair;
        } catch (Exception e) {
            log.error("Failed to generate ECDSA key pair", e);
            throw new RuntimeException("Key pair generation failed", e);
        }
    }

    /**
     * Sign data with private key.
     * 
     * @param data the data to sign (typically a hash)
     * @param privateKey the private key
     * @return signature as hexadecimal string
     * @throws RuntimeException if signing fails
     */
    public static String sign(String data, PrivateKey privateKey) {
        if (data == null || privateKey == null) {
            throw new IllegalArgumentException("Data and private key cannot be null");
        }

        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM, PROVIDER);
            signature.initSign(privateKey);
            signature.update(data.getBytes());
            byte[] signatureBytes = signature.sign();
            return HashUtil.bytesToHex(signatureBytes);
        } catch (Exception e) {
            log.error("Failed to sign data", e);
            throw new RuntimeException("Signature generation failed", e);
        }
    }

    /**
     * Sign byte array with private key.
     * 
     * @param data the data to sign
     * @param privateKey the private key
     * @return signature as hexadecimal string
     * @throws RuntimeException if signing fails
     */
    public static String sign(byte[] data, PrivateKey privateKey) {
        if (data == null || privateKey == null) {
            throw new IllegalArgumentException("Data and private key cannot be null");
        }

        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM, PROVIDER);
            signature.initSign(privateKey);
            signature.update(data);
            byte[] signatureBytes = signature.sign();
            return HashUtil.bytesToHex(signatureBytes);
        } catch (Exception e) {
            log.error("Failed to sign data", e);
            throw new RuntimeException("Signature generation failed", e);
        }
    }

    /**
     * Verify signature with public key.
     * 
     * @param data the original data
     * @param signatureHex the signature as hexadecimal string
     * @param publicKey the public key
     * @return true if signature is valid
     */
    public static boolean verify(String data, String signatureHex, PublicKey publicKey) {
        if (data == null || signatureHex == null || publicKey == null) {
            log.warn("Null parameter provided for signature verification");
            return false;
        }

        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM, PROVIDER);
            signature.initVerify(publicKey);
            signature.update(data.getBytes());
            byte[] signatureBytes = HashUtil.hexToBytes(signatureHex);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            log.error("Signature verification failed", e);
            return false;
        }
    }

    /**
     * Verify signature with public key (byte array data).
     * 
     * @param data the original data
     * @param signatureHex the signature as hexadecimal string
     * @param publicKey the public key
     * @return true if signature is valid
     */
    public static boolean verify(byte[] data, String signatureHex, PublicKey publicKey) {
        if (data == null || signatureHex == null || publicKey == null) {
            log.warn("Null parameter provided for signature verification");
            return false;
        }

        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM, PROVIDER);
            signature.initVerify(publicKey);
            signature.update(data);
            byte[] signatureBytes = HashUtil.hexToBytes(signatureHex);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            log.error("Signature verification failed", e);
            return false;
        }
    }

    /**
     * Save private key to PEM file.
     * 
     * @param privateKey the private key
     * @param filePath the output file path
     * @throws IOException if file write fails
     */
    public static void savePrivateKeyToPem(PrivateKey privateKey, Path filePath) throws IOException {
        try (PemWriter pemWriter = new PemWriter(new FileWriter(filePath.toFile()))) {
            PemObject pemObject = new PemObject("EC PRIVATE KEY", privateKey.getEncoded());
            pemWriter.writeObject(pemObject);
            log.info("Private key saved to: {}", filePath);
        }
    }

    /**
     * Save public key to PEM file.
     * 
     * @param publicKey the public key
     * @param filePath the output file path
     * @throws IOException if file write fails
     */
    public static void savePublicKeyToPem(PublicKey publicKey, Path filePath) throws IOException {
        try (PemWriter pemWriter = new PemWriter(new FileWriter(filePath.toFile()))) {
            PemObject pemObject = new PemObject("PUBLIC KEY", publicKey.getEncoded());
            pemWriter.writeObject(pemObject);
            log.info("Public key saved to: {}", filePath);
        }
    }

    /**
     * Load private key from PEM file.
     * 
     * @param filePath the PEM file path
     * @return the private key
     * @throws IOException if file read fails
     */
    public static PrivateKey loadPrivateKeyFromPem(Path filePath) throws IOException {
        try (PemReader pemReader = new PemReader(new FileReader(filePath.toFile()))) {
            PemObject pemObject = pemReader.readPemObject();
            byte[] keyBytes = pemObject.getContent();
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, PROVIDER);
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
            log.info("Private key loaded from: {}", filePath);
            return privateKey;
        } catch (Exception e) {
            log.error("Failed to load private key from PEM file", e);
            throw new IOException("Private key loading failed", e);
        }
    }

    /**
     * Load public key from PEM file.
     * 
     * @param filePath the PEM file path
     * @return the public key
     * @throws IOException if file read fails
     */
    public static PublicKey loadPublicKeyFromPem(Path filePath) throws IOException {
        try (PemReader pemReader = new PemReader(new FileReader(filePath.toFile()))) {
            PemObject pemObject = pemReader.readPemObject();
            byte[] keyBytes = pemObject.getContent();
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, PROVIDER);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            log.info("Public key loaded from: {}", filePath);
            return publicKey;
        } catch (Exception e) {
            log.error("Failed to load public key from PEM file", e);
            throw new IOException("Public key loading failed", e);
        }
    }

    /**
     * Get public key as PEM-formatted string.
     * 
     * @param publicKey the public key
     * @return PEM-formatted string
     */
    public static String publicKeyToPemString(PublicKey publicKey) {
        StringWriter stringWriter = new StringWriter();
        try (PemWriter pemWriter = new PemWriter(stringWriter)) {
            PemObject pemObject = new PemObject("PUBLIC KEY", publicKey.getEncoded());
            pemWriter.writeObject(pemObject);
            return stringWriter.toString();
        } catch (IOException e) {
            log.error("Failed to convert public key to PEM string", e);
            throw new RuntimeException("Public key conversion failed", e);
        }
    }

    /**
     * Get public key as base64-encoded string.
     * 
     * @param publicKey the public key
     * @return base64-encoded string
     */
    public static String publicKeyToBase64(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Load public key from base64-encoded string.
     * 
     * @param base64Key the base64-encoded key
     * @return the public key
     * @throws RuntimeException if parsing fails
     */
    public static PublicKey publicKeyFromBase64(String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, PROVIDER);
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            log.error("Failed to parse public key from base64", e);
            throw new RuntimeException("Public key parsing failed", e);
        }
    }
}

package com.openride.ticketing.service;

import com.openride.ticketing.util.SignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.KeyPair;

/**
 * Service for managing cryptographic keys.
 * Handles key loading, rotation, and secure storage.
 */
@Slf4j
@Service
public class KeyManagementService {
    
    @Value("${ticketing.crypto.private-key-path}")
    private String privateKeyPath;
    
    @Value("${ticketing.crypto.public-key-path}")
    private String publicKeyPath;
    
    @Value("${ticketing.crypto.auto-generate-keys:true}")
    private boolean autoGenerateKeys;
    
    private PrivateKey privateKey;
    private PublicKey publicKey;
    
    /**
     * Initialize keys on service startup.
     */
    @PostConstruct
    public void init() {
        try {
            loadKeys();
        } catch (Exception e) {
            log.error("Failed to load keys", e);
            if (autoGenerateKeys) {
                log.info("Auto-generating new key pair");
                generateAndSaveKeys();
            } else {
                throw new RuntimeException("Failed to load keys and auto-generation is disabled", e);
            }
        }
    }
    
    /**
     * Load keys from configured paths.
     */
    private void loadKeys() throws IOException {
        Path privatePath = Paths.get(privateKeyPath);
        Path publicPath = Paths.get(publicKeyPath);
        
        if (!Files.exists(privatePath) || !Files.exists(publicPath)) {
            throw new IOException("Key files not found");
        }
        
        log.info("Loading private key from: {}", privateKeyPath);
        privateKey = SignatureUtil.loadPrivateKeyFromPem(privateKeyPath);
        
        log.info("Loading public key from: {}", publicKeyPath);
        publicKey = SignatureUtil.loadPublicKeyFromPem(publicKeyPath);
        
        log.info("Keys loaded successfully");
    }
    
    /**
     * Generate new key pair and save to configured paths.
     */
    private void generateAndSaveKeys() {
        try {
            log.info("Generating new ECDSA key pair");
            KeyPair keyPair = SignatureUtil.generateKeyPair();
            
            privateKey = keyPair.getPrivate();
            publicKey = keyPair.getPublic();
            
            // Create directories if they don't exist
            Path privatePath = Paths.get(privateKeyPath);
            Path publicPath = Paths.get(publicKeyPath);
            
            Files.createDirectories(privatePath.getParent());
            Files.createDirectories(publicPath.getParent());
            
            // Save keys
            log.info("Saving private key to: {}", privateKeyPath);
            SignatureUtil.savePrivateKeyToPem(privateKey, privateKeyPath);
            
            log.info("Saving public key to: {}", publicKeyPath);
            SignatureUtil.savePublicKeyToPem(publicKey, publicKeyPath);
            
            log.info("Key pair generated and saved successfully");
            
        } catch (Exception e) {
            log.error("Failed to generate and save keys", e);
            throw new RuntimeException("Failed to generate keys", e);
        }
    }
    
    /**
     * Get the private key.
     *
     * @return the private key
     */
    public PrivateKey getPrivateKey() {
        if (privateKey == null) {
            throw new IllegalStateException("Private key not loaded");
        }
        return privateKey;
    }
    
    /**
     * Get the public key.
     *
     * @return the public key
     */
    public PublicKey getPublicKey() {
        if (publicKey == null) {
            throw new IllegalStateException("Public key not loaded");
        }
        return publicKey;
    }
    
    /**
     * Get public key as PEM string for distribution.
     *
     * @return the public key in PEM format
     */
    public String getPublicKeyPem() {
        try {
            return SignatureUtil.publicKeyToPemString(getPublicKey());
        } catch (IOException e) {
            log.error("Error converting public key to PEM", e);
            throw new RuntimeException("Failed to get public key PEM", e);
        }
    }
    
    /**
     * Rotate keys (generate new key pair and save).
     * Old keys should be backed up before rotation.
     */
    public void rotateKeys() {
        log.warn("Rotating cryptographic keys - this will invalidate all existing signatures");
        
        // Backup old keys
        backupKeys();
        
        // Generate new keys
        generateAndSaveKeys();
        
        log.info("Key rotation completed");
    }
    
    /**
     * Backup current keys to timestamped files.
     */
    private void backupKeys() {
        try {
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            
            String backupPrivatePath = privateKeyPath + ".backup_" + timestamp;
            String backupPublicPath = publicKeyPath + ".backup_" + timestamp;
            
            Files.copy(Paths.get(privateKeyPath), Paths.get(backupPrivatePath));
            Files.copy(Paths.get(publicKeyPath), Paths.get(backupPublicPath));
            
            log.info("Keys backed up to: {}, {}", backupPrivatePath, backupPublicPath);
            
        } catch (IOException e) {
            log.error("Failed to backup keys", e);
        }
    }
}

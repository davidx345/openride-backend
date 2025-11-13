package com.openride.ticketing.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SignatureUtil.
 */
class SignatureUtilTest {
    
    private KeyPair keyPair;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    
    @BeforeEach
    void setUp() {
        keyPair = SignatureUtil.generateKeyPair();
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();
    }
    
    @Test
    void testGenerateKeyPair() {
        assertNotNull(keyPair);
        assertNotNull(privateKey);
        assertNotNull(publicKey);
        assertEquals("EC", privateKey.getAlgorithm());
        assertEquals("EC", publicKey.getAlgorithm());
    }
    
    @Test
    void testSignAndVerify() {
        String data = "test data to sign";
        
        // Sign the data
        String signature = SignatureUtil.sign(data, privateKey);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
        
        // Verify with correct public key
        assertTrue(SignatureUtil.verify(data, signature, publicKey));
    }
    
    @Test
    void testVerifyWithWrongData() {
        String data = "original data";
        String signature = SignatureUtil.sign(data, privateKey);
        
        // Should fail with different data
        assertFalse(SignatureUtil.verify("tampered data", signature, publicKey));
    }
    
    @Test
    void testVerifyWithWrongKey() {
        String data = "test data";
        String signature = SignatureUtil.sign(data, privateKey);
        
        // Generate different key pair
        KeyPair otherKeyPair = SignatureUtil.generateKeyPair();
        PublicKey otherPublicKey = otherKeyPair.getPublic();
        
        // Should fail with wrong public key
        assertFalse(SignatureUtil.verify(data, signature, otherPublicKey));
    }
    
    @Test
    void testPublicKeyToBase64AndBack() {
        String base64 = SignatureUtil.publicKeyToBase64(publicKey);
        assertNotNull(base64);
        assertFalse(base64.isEmpty());
        
        PublicKey recovered = SignatureUtil.publicKeyFromBase64(base64);
        assertNotNull(recovered);
        assertEquals(publicKey, recovered);
    }
    
    @Test
    void testPublicKeyToPemString() throws Exception {
        String pem = SignatureUtil.publicKeyToPemString(publicKey);
        
        assertNotNull(pem);
        assertTrue(pem.contains("BEGIN PUBLIC KEY"));
        assertTrue(pem.contains("END PUBLIC KEY"));
    }
    
    @Test
    void testSignatureDeterminism() {
        // Note: ECDSA signatures are NOT deterministic by default (they include random k value)
        // This test verifies that different signatures on same data can both be verified
        String data = "test data";
        
        String signature1 = SignatureUtil.sign(data, privateKey);
        String signature2 = SignatureUtil.sign(data, privateKey);
        
        // Signatures may be different (non-deterministic ECDSA)
        // But both should verify correctly
        assertTrue(SignatureUtil.verify(data, signature1, publicKey));
        assertTrue(SignatureUtil.verify(data, signature2, publicKey));
    }
    
    @Test
    void testSignEmptyString() {
        String signature = SignatureUtil.sign("", privateKey);
        assertNotNull(signature);
        assertTrue(SignatureUtil.verify("", signature, publicKey));
    }
    
    @Test
    void testSignLargeData() {
        // Test with larger data
        StringBuilder largeData = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeData.append("Lorem ipsum dolor sit amet. ");
        }
        
        String data = largeData.toString();
        String signature = SignatureUtil.sign(data, privateKey);
        
        assertNotNull(signature);
        assertTrue(SignatureUtil.verify(data, signature, publicKey));
    }
}

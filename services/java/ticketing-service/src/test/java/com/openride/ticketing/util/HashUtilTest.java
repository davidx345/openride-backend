package com.openride.ticketing.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HashUtil.
 */
class HashUtilTest {
    
    @Test
    void testSha256WithString() {
        String input = "Hello, World!";
        String hash = HashUtil.sha256(input);
        
        assertNotNull(hash);
        assertEquals(64, hash.length()); // SHA-256 produces 64 hex characters
        
        // Same input should produce same hash
        String hash2 = HashUtil.sha256(input);
        assertEquals(hash, hash2);
    }
    
    @Test
    void testSha256WithBytes() {
        byte[] input = "Hello, World!".getBytes();
        String hash = HashUtil.sha256(input);
        
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }
    
    @Test
    void testDoubleSha256() {
        String input = "test data";
        String doubleHash = HashUtil.doubleSha256(input);
        
        assertNotNull(doubleHash);
        assertEquals(64, doubleHash.length());
        
        // Double hash should be different from single hash
        String singleHash = HashUtil.sha256(input);
        assertNotEquals(singleHash, doubleHash);
    }
    
    @Test
    void testVerifyHash() {
        String input = "test data";
        String hash = HashUtil.sha256(input);
        
        assertTrue(HashUtil.verifyHash(input, hash));
        assertFalse(HashUtil.verifyHash(input, "invalid_hash"));
        assertFalse(HashUtil.verifyHash("different data", hash));
    }
    
    @Test
    void testCombineHashes() {
        String hash1 = HashUtil.sha256("data1");
        String hash2 = HashUtil.sha256("data2");
        
        String combined = HashUtil.combineHashes(hash1, hash2);
        
        assertNotNull(combined);
        assertEquals(64, combined.length());
        
        // Combining in different order should produce different result
        String combinedReversed = HashUtil.combineHashes(hash2, hash1);
        assertNotEquals(combined, combinedReversed);
    }
    
    @Test
    void testBytesToHexAndBack() {
        byte[] original = "test data".getBytes();
        String hex = HashUtil.bytesToHex(original);
        byte[] converted = HashUtil.hexToBytes(hex);
        
        assertArrayEquals(original, converted);
    }
    
    @Test
    void testEmptyString() {
        String hash = HashUtil.sha256("");
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }
    
    @Test
    void testConsistency() {
        // Test that same input always produces same hash across multiple calls
        String input = "consistency test";
        String hash1 = HashUtil.sha256(input);
        String hash2 = HashUtil.sha256(input);
        String hash3 = HashUtil.sha256(input);
        
        assertEquals(hash1, hash2);
        assertEquals(hash2, hash3);
    }
}

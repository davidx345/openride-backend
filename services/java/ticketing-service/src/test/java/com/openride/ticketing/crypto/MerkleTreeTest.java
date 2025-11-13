package com.openride.ticketing.crypto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MerkleTree.
 */
class MerkleTreeTest {
    
    @Test
    void testSingleLeafTree() {
        List<String> leaves = List.of("hash1");
        MerkleTree tree = new MerkleTree(leaves);
        
        assertEquals("hash1", tree.getRoot());
        assertEquals(1, tree.getLeafCount());
    }
    
    @Test
    void testTwoLeafTree() {
        List<String> leaves = Arrays.asList("hash1", "hash2");
        MerkleTree tree = new MerkleTree(leaves);
        
        assertNotNull(tree.getRoot());
        assertEquals(2, tree.getLeafCount());
        
        // Verify leaves
        assertTrue(tree.containsLeaf("hash1"));
        assertTrue(tree.containsLeaf("hash2"));
        assertFalse(tree.containsLeaf("hash3"));
    }
    
    @Test
    void testOddNumberOfLeaves() {
        List<String> leaves = Arrays.asList("hash1", "hash2", "hash3");
        MerkleTree tree = new MerkleTree(leaves);
        
        assertNotNull(tree.getRoot());
        assertEquals(3, tree.getLeafCount());
    }
    
    @Test
    void testProofGeneration() {
        List<String> leaves = Arrays.asList("hash1", "hash2", "hash3", "hash4");
        MerkleTree tree = new MerkleTree(leaves);
        
        // Generate proof for first leaf
        List<String> proof = tree.generateProof(0);
        assertNotNull(proof);
        assertFalse(proof.isEmpty());
        
        // Verify proof
        boolean valid = MerkleTree.verifyProof("hash1", proof, tree.getRoot());
        assertTrue(valid);
    }
    
    @Test
    void testProofVerification() {
        List<String> leaves = Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h");
        MerkleTree tree = new MerkleTree(leaves);
        
        // Test proofs for all leaves
        for (int i = 0; i < leaves.size(); i++) {
            List<String> proof = tree.generateProof(i);
            boolean valid = MerkleTree.verifyProof(leaves.get(i), proof, tree.getRoot());
            assertTrue(valid, "Proof verification failed for leaf " + i);
        }
    }
    
    @Test
    void testInvalidProof() {
        List<String> leaves = Arrays.asList("hash1", "hash2", "hash3", "hash4");
        MerkleTree tree = new MerkleTree(leaves);
        
        List<String> proof = tree.generateProof(0);
        
        // Should fail with wrong leaf
        boolean valid = MerkleTree.verifyProof("wrong_hash", proof, tree.getRoot());
        assertFalse(valid);
        
        // Should fail with wrong root
        boolean valid2 = MerkleTree.verifyProof("hash1", proof, "wrong_root");
        assertFalse(valid2);
    }
    
    @Test
    void testGetLeafIndex() {
        List<String> leaves = Arrays.asList("hash1", "hash2", "hash3");
        MerkleTree tree = new MerkleTree(leaves);
        
        assertEquals(0, tree.getLeafIndex("hash1"));
        assertEquals(1, tree.getLeafIndex("hash2"));
        assertEquals(2, tree.getLeafIndex("hash3"));
        assertEquals(-1, tree.getLeafIndex("not_found"));
    }
    
    @Test
    void testLargeTree() {
        // Create tree with 100 leaves
        List<String> leaves = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            leaves.add("hash_" + i);
        }
        
        MerkleTree tree = new MerkleTree(leaves);
        
        assertEquals(100, tree.getLeafCount());
        assertNotNull(tree.getRoot());
        
        // Verify random proofs
        for (int i = 0; i < 10; i++) {
            int randomIndex = (int) (Math.random() * 100);
            List<String> proof = tree.generateProof(randomIndex);
            boolean valid = MerkleTree.verifyProof(leaves.get(randomIndex), proof, tree.getRoot());
            assertTrue(valid, "Proof failed for leaf " + randomIndex);
        }
    }
    
    @Test
    void testTreeStructureValidation() {
        List<String> leaves = Arrays.asList("hash1", "hash2", "hash3", "hash4");
        MerkleTree tree = new MerkleTree(leaves);
        
        assertTrue(tree.verifyTreeStructure());
    }
    
    @Test
    void testDeterministicRoot() {
        List<String> leaves = Arrays.asList("a", "b", "c", "d");
        
        MerkleTree tree1 = new MerkleTree(leaves);
        MerkleTree tree2 = new MerkleTree(leaves);
        
        // Same leaves should produce same root
        assertEquals(tree1.getRoot(), tree2.getRoot());
    }
    
    @Test
    void testDifferentOrderProducesDifferentRoot() {
        List<String> leaves1 = Arrays.asList("a", "b", "c", "d");
        List<String> leaves2 = Arrays.asList("d", "c", "b", "a");
        
        MerkleTree tree1 = new MerkleTree(leaves1);
        MerkleTree tree2 = new MerkleTree(leaves2);
        
        // Different order should produce different root
        assertNotEquals(tree1.getRoot(), tree2.getRoot());
    }
    
    @Test
    void testEmptyTreeThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            new MerkleTree(List.of());
        });
    }
}

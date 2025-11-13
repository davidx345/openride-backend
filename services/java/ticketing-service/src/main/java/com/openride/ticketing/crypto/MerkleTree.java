package com.openride.ticketing.crypto;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Merkle tree builder for efficient batch verification of tickets.
 * 
 * A Merkle tree allows proving that a ticket is part of a batch
 * without revealing other tickets in the batch.
 * 
 * The tree is built bottom-up from leaf hashes:
 * - Leaf nodes are ticket hashes
 * - Parent nodes are hash(left_child + right_child)
 * - Root node is the Merkle root (anchored on blockchain)
 * 
 * Features:
 * - Generates proofs for each leaf
 * - Handles odd number of leaves (duplicates last)
 * - Uses SHA-256 for hashing
 */
@Slf4j
@Getter
public class MerkleTree {

    private final List<String> leaves;
    private final List<List<String>> levels;
    private final String root;

    /**
     * Build Merkle tree from list of hashes.
     * 
     * @param leafHashes list of leaf hashes (ticket hashes)
     * @throws IllegalArgumentException if leaf list is empty
     */
    public MerkleTree(List<String> leafHashes) {
        if (leafHashes == null || leafHashes.isEmpty()) {
            throw new IllegalArgumentException("Leaf hashes cannot be null or empty");
        }

        this.leaves = new ArrayList<>(leafHashes);
        this.levels = new ArrayList<>();
        this.root = buildTree();

        log.info("Built Merkle tree with {} leaves, root: {}", leaves.size(), root);
    }

    /**
     * Build the Merkle tree bottom-up.
     * 
     * @return the Merkle root hash
     */
    private String buildTree() {
        // Start with leaf level
        List<String> currentLevel = new ArrayList<>(leaves);
        levels.add(new ArrayList<>(currentLevel));

        // Build tree level by level
        while (currentLevel.size() > 1) {
            List<String> nextLevel = new ArrayList<>();

            // Process pairs
            for (int i = 0; i < currentLevel.size(); i += 2) {
                String left = currentLevel.get(i);
                String right;

                // If odd number of nodes, duplicate the last one
                if (i + 1 < currentLevel.size()) {
                    right = currentLevel.get(i + 1);
                } else {
                    right = left;
                    log.debug("Duplicating last node at level {}", levels.size());
                }

                // Combine hashes
                String parentHash = HashUtil.combineHashes(left, right);
                nextLevel.add(parentHash);
            }

            levels.add(nextLevel);
            currentLevel = nextLevel;
        }

        // Root is the single node at the top level
        return currentLevel.get(0);
    }

    /**
     * Generate Merkle proof for a leaf at given index.
     * 
     * The proof is an array of sibling hashes needed to
     * reconstruct the path from leaf to root.
     * 
     * @param leafIndex the index of the leaf (0-based)
     * @return list of sibling hashes forming the proof path
     * @throws IllegalArgumentException if index is out of bounds
     */
    public List<String> generateProof(int leafIndex) {
        if (leafIndex < 0 || leafIndex >= leaves.size()) {
            throw new IllegalArgumentException("Invalid leaf index: " + leafIndex);
        }

        List<String> proof = new ArrayList<>();
        int currentIndex = leafIndex;

        // Traverse from leaf to root, collecting sibling hashes
        for (int level = 0; level < levels.size() - 1; level++) {
            List<String> currentLevel = levels.get(level);
            int siblingIndex;

            // Determine sibling index (if even, sibling is right; if odd, sibling is left)
            if (currentIndex % 2 == 0) {
                siblingIndex = currentIndex + 1;
            } else {
                siblingIndex = currentIndex - 1;
            }

            // Add sibling hash to proof (or duplicate if at end)
            if (siblingIndex < currentLevel.size()) {
                proof.add(currentLevel.get(siblingIndex));
            } else {
                proof.add(currentLevel.get(currentIndex)); // Duplicate last node
            }

            // Move to parent index
            currentIndex = currentIndex / 2;
        }

        log.debug("Generated proof for leaf {}: {} hashes", leafIndex, proof.size());
        return proof;
    }

    /**
     * Verify a Merkle proof for a given leaf hash.
     * 
     * @param leafHash the leaf hash to verify
     * @param leafIndex the index of the leaf
     * @param proof the Merkle proof (list of sibling hashes)
     * @param expectedRoot the expected Merkle root
     * @return true if proof is valid
     */
    public static boolean verifyProof(String leafHash, int leafIndex, List<String> proof, String expectedRoot) {
        if (leafHash == null || proof == null || expectedRoot == null) {
            log.warn("Null parameters provided for proof verification");
            return false;
        }

        try {
            String computedHash = leafHash;
            int currentIndex = leafIndex;

            // Reconstruct path to root
            for (String siblingHash : proof) {
                if (currentIndex % 2 == 0) {
                    // Current node is left child
                    computedHash = HashUtil.combineHashes(computedHash, siblingHash);
                } else {
                    // Current node is right child
                    computedHash = HashUtil.combineHashes(siblingHash, computedHash);
                }

                currentIndex = currentIndex / 2;
            }

            // Compare computed root with expected root
            boolean isValid = computedHash.equalsIgnoreCase(expectedRoot);
            
            if (isValid) {
                log.debug("Merkle proof verified successfully");
            } else {
                log.warn("Merkle proof verification failed. Computed: {}, Expected: {}", 
                        computedHash, expectedRoot);
            }

            return isValid;
        } catch (Exception e) {
            log.error("Error during Merkle proof verification", e);
            return false;
        }
    }

    /**
     * Get the number of leaves in the tree.
     * 
     * @return number of leaves
     */
    public int getLeafCount() {
        return leaves.size();
    }

    /**
     * Get the height of the tree.
     * 
     * @return tree height (number of levels)
     */
    public int getHeight() {
        return levels.size();
    }

    /**
     * Get all leaves in the tree.
     * 
     * @return copy of leaf list
     */
    public List<String> getLeaves() {
        return new ArrayList<>(leaves);
    }

    /**
     * Get a specific level of the tree.
     * 
     * @param levelIndex the level index (0 = leaves, max = root)
     * @return list of hashes at that level
     * @throws IllegalArgumentException if level index is invalid
     */
    public List<String> getLevel(int levelIndex) {
        if (levelIndex < 0 || levelIndex >= levels.size()) {
            throw new IllegalArgumentException("Invalid level index: " + levelIndex);
        }
        return new ArrayList<>(levels.get(levelIndex));
    }

    /**
     * Verify that this tree contains a specific leaf.
     * 
     * @param leafHash the leaf hash to check
     * @return true if leaf exists in tree
     */
    public boolean containsLeaf(String leafHash) {
        return leaves.contains(leafHash);
    }

    /**
     * Get the index of a leaf hash.
     * 
     * @param leafHash the leaf hash to find
     * @return the index, or -1 if not found
     */
    public int getLeafIndex(String leafHash) {
        return leaves.indexOf(leafHash);
    }

    /**
     * Verify the entire tree structure.
     * Useful for testing and debugging.
     * 
     * @return true if tree structure is valid
     */
    public boolean verifyTreeStructure() {
        try {
            // Rebuild tree from leaves and compare roots
            MerkleTree rebuilt = new MerkleTree(leaves);
            boolean isValid = rebuilt.getRoot().equals(this.root);
            
            if (!isValid) {
                log.warn("Tree structure verification failed");
            }
            
            return isValid;
        } catch (Exception e) {
            log.error("Error during tree structure verification", e);
            return false;
        }
    }
}

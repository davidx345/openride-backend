package com.openride.ticketing.blockchain;

import com.openride.ticketing.model.BlockchainAnchor;
import com.openride.ticketing.model.MerkleBatch;

import java.util.concurrent.CompletableFuture;

/**
 * Blockchain client interface for anchoring Merkle roots.
 * Supports multiple blockchain implementations (Polygon, Hyperledger Fabric, Ethereum).
 */
public interface BlockchainClient {
    
    /**
     * Submit Merkle root to blockchain.
     *
     * @param batch the Merkle batch to anchor
     * @return CompletableFuture containing the blockchain anchor record
     */
    CompletableFuture<BlockchainAnchor> anchorMerkleRoot(MerkleBatch batch);
    
    /**
     * Check confirmation status of a blockchain transaction.
     *
     * @param transactionHash the transaction hash
     * @return CompletableFuture containing the confirmation count
     */
    CompletableFuture<Integer> getConfirmationCount(String transactionHash);
    
    /**
     * Verify if a Merkle root exists on the blockchain.
     *
     * @param merkleRoot the Merkle root hash
     * @return CompletableFuture containing true if verified, false otherwise
     */
    CompletableFuture<Boolean> verifyMerkleRoot(String merkleRoot);
    
    /**
     * Get current gas price estimate.
     *
     * @return CompletableFuture containing gas price in wei
     */
    CompletableFuture<Long> estimateGasPrice();
    
    /**
     * Check if blockchain client is connected and healthy.
     *
     * @return true if healthy, false otherwise
     */
    boolean isHealthy();
    
    /**
     * Get blockchain type identifier.
     *
     * @return blockchain type (e.g., "POLYGON", "HYPERLEDGER", "ETHEREUM")
     */
    String getBlockchainType();
}

package com.openride.commons.dto.ticketing;

import java.util.List;

/**
 * Response DTO containing Merkle proof for blockchain verification.
 * Used to verify ticket inclusion in blockchain-anchored batch.
 */
public class MerkleProofResponse {
    
    private String ticketId;
    private String ticketHash;
    private String merkleRoot;
    private List<String> proof;
    private List<Boolean> positions; // true = right, false = left
    private Integer leafIndex;
    private Long batchId;
    private String blockchainTxHash;
    private String blockchainStatus;
    
    public MerkleProofResponse() {
    }
    
    public MerkleProofResponse(String ticketId, String merkleRoot, List<String> proof) {
        this.ticketId = ticketId;
        this.merkleRoot = merkleRoot;
        this.proof = proof;
    }
    
    // Getters and setters
    
    public String getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }
    
    public String getTicketHash() {
        return ticketHash;
    }
    
    public void setTicketHash(String ticketHash) {
        this.ticketHash = ticketHash;
    }
    
    public String getMerkleRoot() {
        return merkleRoot;
    }
    
    public void setMerkleRoot(String merkleRoot) {
        this.merkleRoot = merkleRoot;
    }
    
    public List<String> getProof() {
        return proof;
    }
    
    public void setProof(List<String> proof) {
        this.proof = proof;
    }
    
    public List<Boolean> getPositions() {
        return positions;
    }
    
    public void setPositions(List<Boolean> positions) {
        this.positions = positions;
    }
    
    public Integer getLeafIndex() {
        return leafIndex;
    }
    
    public void setLeafIndex(Integer leafIndex) {
        this.leafIndex = leafIndex;
    }
    
    public Long getBatchId() {
        return batchId;
    }
    
    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }
    
    public String getBlockchainTxHash() {
        return blockchainTxHash;
    }
    
    public void setBlockchainTxHash(String blockchainTxHash) {
        this.blockchainTxHash = blockchainTxHash;
    }
    
    public String getBlockchainStatus() {
        return blockchainStatus;
    }
    
    public void setBlockchainStatus(String blockchainStatus) {
        this.blockchainStatus = blockchainStatus;
    }
    
    @Override
    public String toString() {
        return "MerkleProofResponse{" +
                "ticketId='" + ticketId + '\'' +
                ", merkleRoot='" + merkleRoot + '\'' +
                ", proofSize=" + (proof != null ? proof.size() : 0) +
                ", batchId=" + batchId +
                ", blockchainStatus='" + blockchainStatus + '\'' +
                '}';
    }
}

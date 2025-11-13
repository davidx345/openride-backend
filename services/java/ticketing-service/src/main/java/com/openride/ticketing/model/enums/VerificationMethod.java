package com.openride.ticketing.model.enums;

/**
 * Method used to verify a ticket.
 */
public enum VerificationMethod {
    /**
     * Verification using ECDSA signature.
     */
    SIGNATURE,
    
    /**
     * Verification using Merkle proof.
     */
    MERKLE_PROOF,
    
    /**
     * Verification via blockchain query.
     */
    BLOCKCHAIN,
    
    /**
     * Simple database lookup verification.
     */
    DATABASE
}

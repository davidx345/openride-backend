package com.openride.ticketing.service;

import com.openride.ticketing.model.*;
import com.openride.ticketing.repository.TicketRepository;
import com.openride.ticketing.repository.TicketVerificationLogRepository;
import com.openride.ticketing.util.HashUtil;
import com.openride.ticketing.util.SignatureUtil;
import com.openride.ticketing.crypto.MerkleTree;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for ticket verification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketVerificationService {
    
    private final TicketRepository ticketRepository;
    private final TicketVerificationLogRepository verificationLogRepository;
    private final KeyManagementService keyManagementService;
    
    /**
     * Verify a ticket by its hash and signature.
     *
     * @param ticketId the ticket ID
     * @param verifierId the ID of the entity verifying (driver ID)
     * @param ipAddress the IP address of the verifier
     * @param userAgent the user agent of the verifier
     * @return the verification result
     */
    @Transactional
    public VerificationResult verifyTicket(UUID ticketId, UUID verifierId, 
                                          String ipAddress, String userAgent) {
        log.info("Verifying ticket: {}", ticketId);
        
        // Find ticket
        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        
        if (ticket == null) {
            logVerification(ticketId, verifierId, VerificationMethod.DATABASE, 
                          VerificationResult.NOT_FOUND, ipAddress, userAgent, "Ticket not found");
            return VerificationResult.NOT_FOUND;
        }
        
        // Check if ticket is revoked
        if (ticket.getStatus() == TicketStatus.REVOKED) {
            logVerification(ticket, verifierId, VerificationMethod.DATABASE, 
                          VerificationResult.REVOKED, ipAddress, userAgent, "Ticket is revoked");
            return VerificationResult.REVOKED;
        }
        
        // Check if ticket is expired
        if (ticket.isExpired()) {
            logVerification(ticket, verifierId, VerificationMethod.DATABASE, 
                          VerificationResult.EXPIRED, ipAddress, userAgent, "Ticket is expired");
            return VerificationResult.EXPIRED;
        }
        
        // Verify signature
        boolean signatureValid = verifySignature(ticket);
        
        if (!signatureValid) {
            logVerification(ticket, verifierId, VerificationMethod.SIGNATURE, 
                          VerificationResult.INVALID, ipAddress, userAgent, "Invalid signature");
            return VerificationResult.INVALID;
        }
        
        // Verify hash
        boolean hashValid = verifyHash(ticket);
        
        if (!hashValid) {
            logVerification(ticket, verifierId, VerificationMethod.DATABASE, 
                          VerificationResult.INVALID, ipAddress, userAgent, "Invalid hash");
            return VerificationResult.INVALID;
        }
        
        // All checks passed
        logVerification(ticket, verifierId, VerificationMethod.SIGNATURE, 
                      VerificationResult.VALID, ipAddress, userAgent, "Ticket verified successfully");
        
        return VerificationResult.VALID;
    }
    
    /**
     * Verify ticket using Merkle proof.
     *
     * @param ticketId the ticket ID
     * @param verifierId the verifier ID
     * @param ipAddress the IP address
     * @param userAgent the user agent
     * @return the verification result
     */
    @Transactional
    public VerificationResult verifyTicketWithMerkleProof(UUID ticketId, UUID verifierId,
                                                         String ipAddress, String userAgent) {
        log.info("Verifying ticket with Merkle proof: {}", ticketId);
        
        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        
        if (ticket == null) {
            logVerification(ticketId, verifierId, VerificationMethod.MERKLE_PROOF,
                          VerificationResult.NOT_FOUND, ipAddress, userAgent, "Ticket not found");
            return VerificationResult.NOT_FOUND;
        }
        
        // Check if ticket has Merkle proof
        if (ticket.getMerkleProof() == null) {
            logVerification(ticket, verifierId, VerificationMethod.MERKLE_PROOF,
                          VerificationResult.INVALID, ipAddress, userAgent, "No Merkle proof available");
            return VerificationResult.INVALID;
        }
        
        MerkleProof proof = ticket.getMerkleProof();
        MerkleBatch batch = proof.getMerkleBatch();
        
        // Verify Merkle proof
        boolean proofValid = MerkleTree.verifyProof(
                ticket.getHash(),
                proof.getProofPathArray(),
                batch.getMerkleRoot()
        );
        
        if (!proofValid) {
            logVerification(ticket, verifierId, VerificationMethod.MERKLE_PROOF,
                          VerificationResult.INVALID, ipAddress, userAgent, "Invalid Merkle proof");
            return VerificationResult.INVALID;
        }
        
        // Check ticket status
        if (ticket.isRevoked()) {
            logVerification(ticket, verifierId, VerificationMethod.MERKLE_PROOF,
                          VerificationResult.REVOKED, ipAddress, userAgent, "Ticket is revoked");
            return VerificationResult.REVOKED;
        }
        
        if (ticket.isExpired()) {
            logVerification(ticket, verifierId, VerificationMethod.MERKLE_PROOF,
                          VerificationResult.EXPIRED, ipAddress, userAgent, "Ticket is expired");
            return VerificationResult.EXPIRED;
        }
        
        logVerification(ticket, verifierId, VerificationMethod.MERKLE_PROOF,
                      VerificationResult.VALID, ipAddress, userAgent, "Ticket verified with Merkle proof");
        
        return VerificationResult.VALID;
    }
    
    /**
     * Verify ticket signature.
     */
    private boolean verifySignature(Ticket ticket) {
        try {
            PublicKey publicKey = keyManagementService.getPublicKey();
            return SignatureUtil.verify(ticket.getHash(), ticket.getSignature(), publicKey);
        } catch (Exception e) {
            log.error("Error verifying ticket signature", e);
            return false;
        }
    }
    
    /**
     * Verify ticket hash matches canonical JSON.
     */
    private boolean verifyHash(Ticket ticket) {
        try {
            String computedHash = HashUtil.sha256(ticket.getCanonicalJson());
            return HashUtil.verifyHash(ticket.getCanonicalJson(), ticket.getHash());
        } catch (Exception e) {
            log.error("Error verifying ticket hash", e);
            return false;
        }
    }
    
    /**
     * Log verification attempt.
     */
    private void logVerification(Ticket ticket, UUID verifierId, VerificationMethod method,
                                VerificationResult result, String ipAddress, String userAgent,
                                String notes) {
        TicketVerificationLog log = new TicketVerificationLog();
        log.setTicket(ticket);
        log.setVerifierId(verifierId);
        log.setVerificationMethod(method);
        log.setResult(result);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setNotes(notes);
        
        verificationLogRepository.save(log);
    }
    
    /**
     * Log verification attempt when ticket is not found.
     */
    private void logVerification(UUID ticketId, UUID verifierId, VerificationMethod method,
                                VerificationResult result, String ipAddress, String userAgent,
                                String notes) {
        TicketVerificationLog log = new TicketVerificationLog();
        log.setVerifierId(verifierId);
        log.setVerificationMethod(method);
        log.setResult(result);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setNotes(notes + " - Ticket ID: " + ticketId);
        
        verificationLogRepository.save(log);
    }
}

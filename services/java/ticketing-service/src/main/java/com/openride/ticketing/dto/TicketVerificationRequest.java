package com.openride.ticketing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Ticket verification request DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketVerificationRequest {
    
    @NotNull(message = "Ticket ID is required")
    private UUID ticketId;
    
    @NotNull(message = "Verifier ID is required")
    private UUID verifierId;
    
    /**
     * Whether to use Merkle proof verification (faster for anchored tickets).
     */
    private boolean useMerkleProof = false;
}

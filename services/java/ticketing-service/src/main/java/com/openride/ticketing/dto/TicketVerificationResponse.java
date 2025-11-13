package com.openride.ticketing.dto;

import com.openride.ticketing.model.VerificationResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Ticket verification response DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketVerificationResponse {
    
    private UUID ticketId;
    private VerificationResult result;
    private boolean valid;
    private String message;
}

package com.openride.ticketing.repository;

import com.openride.ticketing.model.entity.TicketVerificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketVerificationLogRepository extends JpaRepository<TicketVerificationLog, UUID> {
    
    List<TicketVerificationLog> findByTicketIdOrderByVerificationTimeDesc(UUID ticketId);
    
    List<TicketVerificationLog> findByVerifierIdOrderByVerificationTimeDesc(UUID verifierId);
}

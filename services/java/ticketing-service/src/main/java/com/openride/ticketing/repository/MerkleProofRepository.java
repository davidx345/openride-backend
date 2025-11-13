package com.openride.ticketing.repository;

import com.openride.ticketing.model.entity.MerkleProof;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerkleProofRepository extends JpaRepository<MerkleProof, UUID> {
    
    Optional<MerkleProof> findByTicketId(UUID ticketId);
    
    List<MerkleProof> findByMerkleBatchId(UUID merkleBatchId);
}

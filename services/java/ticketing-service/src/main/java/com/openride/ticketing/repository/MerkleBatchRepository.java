package com.openride.ticketing.repository;

import com.openride.ticketing.model.entity.MerkleBatch;
import com.openride.ticketing.model.enums.MerkleBatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerkleBatchRepository extends JpaRepository<MerkleBatch, UUID> {
    
    List<MerkleBatch> findByStatusOrderByCreatedAtDesc(MerkleBatchStatus status);
    
    Optional<MerkleBatch> findByMerkleRoot(String merkleRoot);
    
    long countByStatus(MerkleBatchStatus status);
}

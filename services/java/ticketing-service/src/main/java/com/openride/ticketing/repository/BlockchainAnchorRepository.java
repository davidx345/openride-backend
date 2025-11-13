package com.openride.ticketing.repository;

import com.openride.ticketing.model.entity.BlockchainAnchor;
import com.openride.ticketing.model.enums.BlockchainAnchorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlockchainAnchorRepository extends JpaRepository<BlockchainAnchor, UUID> {
    
    List<BlockchainAnchor> findByStatusOrderByCreatedAtDesc(BlockchainAnchorStatus status);
    
    Optional<BlockchainAnchor> findByTransactionHash(String transactionHash);
    
    Optional<BlockchainAnchor> findByMerkleBatchId(UUID merkleBatchId);
}

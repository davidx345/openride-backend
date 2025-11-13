package com.openride.ticketing.model.entity;

import com.openride.ticketing.model.enums.MerkleBatchStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Merkle batch entity representing a group of tickets batched together
 * for efficient blockchain anchoring.
 * 
 * A Merkle batch:
 * - Contains multiple tickets
 * - Generates a Merkle tree from ticket hashes
 * - Stores the Merkle root for blockchain anchoring
 * - Maintains proofs for each ticket in the batch
 */
@Entity
@Table(name = "merkle_batches", indexes = {
    @Index(name = "idx_merkle_batches_status", columnList = "status"),
    @Index(name = "idx_merkle_batches_created_at", columnList = "created_at"),
    @Index(name = "idx_merkle_batches_merkle_root", columnList = "merkle_root")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerkleBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merkle_root", nullable = false, length = 64)
    private String merkleRoot;

    @Column(name = "ticket_count", nullable = false)
    private Integer ticketCount;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blockchain_anchor_id", foreignKey = @ForeignKey(name = "fk_batch_anchor"))
    private BlockchainAnchor blockchainAnchor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MerkleBatchStatus status = MerkleBatchStatus.PENDING;

    @OneToMany(mappedBy = "merkleBatch", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Ticket> tickets = new ArrayList<>();

    @OneToMany(mappedBy = "merkleBatch", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MerkleProof> proofs = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "anchored_at")
    private LocalDateTime anchoredAt;

    /**
     * Check if batch is ready for blockchain anchoring.
     * 
     * @return true if status is READY
     */
    public boolean isReady() {
        return status == MerkleBatchStatus.READY;
    }

    /**
     * Check if batch has been anchored to blockchain.
     * 
     * @return true if status is ANCHORED
     */
    public boolean isAnchored() {
        return status == MerkleBatchStatus.ANCHORED;
    }

    /**
     * Mark batch as ready for anchoring.
     */
    public void markAsReady() {
        this.status = MerkleBatchStatus.READY;
    }

    /**
     * Mark batch as anchored.
     */
    public void markAsAnchored() {
        this.status = MerkleBatchStatus.ANCHORED;
        this.anchoredAt = LocalDateTime.now();
    }

    /**
     * Mark batch as failed.
     */
    public void markAsFailed() {
        this.status = MerkleBatchStatus.FAILED;
    }

    /**
     * Add ticket to batch.
     * 
     * @param ticket the ticket to add
     */
    public void addTicket(Ticket ticket) {
        tickets.add(ticket);
        ticket.setMerkleBatch(this);
        this.ticketCount = tickets.size();
    }

    /**
     * Add Merkle proof to batch.
     * 
     * @param proof the proof to add
     */
    public void addProof(MerkleProof proof) {
        proofs.add(proof);
        proof.setMerkleBatch(this);
    }
}

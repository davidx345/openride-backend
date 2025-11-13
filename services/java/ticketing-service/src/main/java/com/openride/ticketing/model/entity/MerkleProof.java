package com.openride.ticketing.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Merkle proof entity storing the cryptographic proof path
 * for a ticket within a Merkle tree batch.
 * 
 * This allows:
 * - Independent verification of ticket inclusion
 * - Offline proof validation
 * - Efficient blockchain verification
 */
@Entity
@Table(name = "merkle_proofs", indexes = {
    @Index(name = "idx_merkle_proofs_ticket_id", columnList = "ticket_id"),
    @Index(name = "idx_merkle_proofs_batch_id", columnList = "merkle_batch_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerkleProof {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false, unique = true,
                foreignKey = @ForeignKey(name = "fk_proof_ticket"))
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merkle_batch_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_proof_batch"))
    private MerkleBatch merkleBatch;

    @Column(name = "leaf_index", nullable = false)
    private Integer leafIndex;

    /**
     * JSON array of sibling hashes forming the proof path.
     * Example: ["hash1", "hash2", "hash3"]
     */
    @Column(name = "proof_path", nullable = false, columnDefinition = "TEXT")
    private String proofPath;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Get proof path as array of strings.
     * Parses the JSON stored in proofPath.
     * 
     * @return array of sibling hashes
     */
    public String[] getProofPathArray() {
        if (proofPath == null || proofPath.isEmpty()) {
            return new String[0];
        }
        // Remove brackets and split by comma
        String cleaned = proofPath.replaceAll("[\\[\\]\"]", "");
        return cleaned.split(",");
    }
}

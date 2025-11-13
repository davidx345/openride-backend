package com.openride.payments.dto;

import com.openride.payments.model.ReconciliationRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for reconciliation record.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationResponse {

    private UUID id;
    private LocalDate reconciliationDate;
    private Integer totalLocalPayments;
    private Integer totalKorapayPayments;
    private BigDecimal totalLocalAmount;
    private BigDecimal totalKorapayAmount;
    private Integer discrepancyCount;
    private String status;
    private String notes;
    private String discrepancies; // JSON string
    private LocalDateTime createdAt;

    /**
     * Converts entity to response DTO.
     *
     * @param entity reconciliation record entity
     * @return response DTO
     */
    public static ReconciliationResponse fromEntity(ReconciliationRecord entity) {
        return ReconciliationResponse.builder()
            .id(entity.getId())
            .reconciliationDate(entity.getReconciliationDate())
            .totalLocalPayments(entity.getTotalLocalPayments())
            .totalKorapayPayments(entity.getTotalKorapayPayments())
            .totalLocalAmount(entity.getTotalLocalAmount())
            .totalKorapayAmount(entity.getTotalKorapayAmount())
            .discrepancyCount(entity.getDiscrepancyCount())
            .status(entity.getStatus().name())
            .notes(entity.getNotes())
            .discrepancies(entity.getDiscrepancies())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}

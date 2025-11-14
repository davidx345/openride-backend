package com.openride.admin.dto;

import com.openride.admin.model.UserSuspension;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for UserSuspension response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSuspensionResponse {

    private UUID id;
    private UUID userId;
    private String suspensionType;
    private String reason;
    private String notes;
    private Instant startDate;
    private Instant endDate;
    private UUID suspendedBy;
    private Boolean isActive;
    private Boolean currentlyActive;
    private Instant createdAt;
    private Instant updatedAt;

    public static UserSuspensionResponse fromEntity(UserSuspension suspension) {
        return UserSuspensionResponse.builder()
                .id(suspension.getId())
                .userId(suspension.getUserId())
                .suspensionType(suspension.getSuspensionType().name())
                .reason(suspension.getReason())
                .notes(suspension.getNotes())
                .startDate(suspension.getStartDate())
                .endDate(suspension.getEndDate())
                .suspendedBy(suspension.getSuspendedBy())
                .isActive(suspension.getIsActive())
                .currentlyActive(suspension.isCurrentlyActive())
                .createdAt(suspension.getCreatedAt())
                .updatedAt(suspension.getUpdatedAt())
                .build();
    }
}

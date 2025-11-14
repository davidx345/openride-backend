package com.openride.admin.service;

import com.openride.admin.dto.CreateSuspensionRequest;
import com.openride.admin.entity.UserSuspension;
import com.openride.admin.entity.UserSuspension.SuspensionType;
import com.openride.admin.repository.UserSuspensionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuspensionServiceTest {

    @Mock
    private UserSuspensionRepository suspensionRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private SuspensionService suspensionService;

    private UUID userId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        adminId = UUID.randomUUID();
    }

    @Test
    void suspendUser_TemporarySuspension_Success() {
        // Given
        CreateSuspensionRequest request = CreateSuspensionRequest.builder()
                .userId(userId)
                .suspensionType(SuspensionType.TEMPORARY)
                .reason("Inappropriate behavior")
                .notes("First offense - 7 day suspension")
                .endDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        when(suspensionRepository.findActiveSuspensionForUser(userId))
                .thenReturn(Optional.empty());
        
        UserSuspension savedSuspension = new UserSuspension();
        savedSuspension.setId(UUID.randomUUID());
        savedSuspension.setUserId(userId);
        savedSuspension.setActive(true);
        
        when(suspensionRepository.save(any(UserSuspension.class)))
                .thenReturn(savedSuspension);

        // When
        UserSuspension result = suspensionService.suspendUser(request, adminId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isActive()).isTrue();
        verify(suspensionRepository).save(any(UserSuspension.class));
        verify(auditService).logAction(eq("USER_SUSPENSION"), eq(savedSuspension.getId()),
                eq("CREATE"), eq(adminId), any());
    }

    @Test
    void suspendUser_PermanentSuspension_Success() {
        // Given
        CreateSuspensionRequest request = CreateSuspensionRequest.builder()
                .userId(userId)
                .suspensionType(SuspensionType.PERMANENT)
                .reason("Fraudulent activity")
                .notes("Permanent ban - fraud detected")
                .build();

        when(suspensionRepository.findActiveSuspensionForUser(userId))
                .thenReturn(Optional.empty());
        
        UserSuspension savedSuspension = new UserSuspension();
        savedSuspension.setId(UUID.randomUUID());
        savedSuspension.setUserId(userId);
        savedSuspension.setActive(true);
        
        when(suspensionRepository.save(any(UserSuspension.class)))
                .thenReturn(savedSuspension);

        // When
        UserSuspension result = suspensionService.suspendUser(request, adminId);

        // Then
        assertThat(result).isNotNull();
        verify(suspensionRepository).save(any(UserSuspension.class));
    }

    @Test
    void suspendUser_TemporaryWithoutEndDate_ThrowsException() {
        // Given
        CreateSuspensionRequest request = CreateSuspensionRequest.builder()
                .userId(userId)
                .suspensionType(SuspensionType.TEMPORARY)
                .reason("Test")
                .build();

        // When & Then
        assertThatThrownBy(() -> suspensionService.suspendUser(request, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Temporary suspension must have an end date");
    }

    @Test
    void suspendUser_PermanentWithEndDate_ThrowsException() {
        // Given
        CreateSuspensionRequest request = CreateSuspensionRequest.builder()
                .userId(userId)
                .suspensionType(SuspensionType.PERMANENT)
                .reason("Test")
                .endDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        // When & Then
        assertThatThrownBy(() -> suspensionService.suspendUser(request, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Permanent suspension cannot have an end date");
    }

    @Test
    void suspendUser_AlreadySuspended_ThrowsException() {
        // Given
        CreateSuspensionRequest request = CreateSuspensionRequest.builder()
                .userId(userId)
                .suspensionType(SuspensionType.TEMPORARY)
                .reason("Test")
                .endDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        UserSuspension existingSuspension = new UserSuspension();
        existingSuspension.setActive(true);
        
        when(suspensionRepository.findActiveSuspensionForUser(userId))
                .thenReturn(Optional.of(existingSuspension));

        // When & Then
        assertThatThrownBy(() -> suspensionService.suspendUser(request, adminId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User already has an active suspension");
    }

    @Test
    void liftSuspension_Success() {
        // Given
        UUID suspensionId = UUID.randomUUID();
        UserSuspension suspension = new UserSuspension();
        suspension.setId(suspensionId);
        suspension.setUserId(userId);
        suspension.setActive(true);

        when(suspensionRepository.findById(suspensionId))
                .thenReturn(Optional.of(suspension));
        when(suspensionRepository.save(any(UserSuspension.class)))
                .thenReturn(suspension);

        // When
        suspensionService.liftSuspension(suspensionId, adminId);

        // Then
        assertThat(suspension.isActive()).isFalse();
        verify(suspensionRepository).save(suspension);
        verify(auditService).logAction(eq("USER_SUSPENSION"), eq(suspensionId),
                eq("LIFT"), eq(adminId), any());
    }

    @Test
    void isUserSuspended_ReturnsTrueWhenSuspended() {
        // Given
        UserSuspension suspension = new UserSuspension();
        suspension.setActive(true);
        
        when(suspensionRepository.findActiveSuspensionForUser(userId))
                .thenReturn(Optional.of(suspension));

        // When
        boolean result = suspensionService.isUserSuspended(userId);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isUserSuspended_ReturnsFalseWhenNotSuspended() {
        // Given
        when(suspensionRepository.findActiveSuspensionForUser(userId))
                .thenReturn(Optional.empty());

        // When
        boolean result = suspensionService.isUserSuspended(userId);

        // Then
        assertThat(result).isFalse();
    }
}

package com.openride.payouts.model.entity;

import com.openride.payouts.exception.PayoutsException;
import com.openride.payouts.model.enums.PayoutStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PayoutRequest entity state machine.
 */
class PayoutRequestTest {

    private PayoutRequest payoutRequest;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();

        payoutRequest = new PayoutRequest();
        payoutRequest.setId(UUID.randomUUID());
        payoutRequest.setDriverId(UUID.randomUUID());
        payoutRequest.setWalletId(UUID.randomUUID());
        payoutRequest.setBankAccountId(UUID.randomUUID());
        payoutRequest.setAmount(BigDecimal.valueOf(20000.00));
        payoutRequest.setStatus(PayoutStatus.PENDING);
        payoutRequest.setRequestedAt(LocalDateTime.now());
    }

    @Test
    void approve_FromPendingStatus_ShouldTransitionToApproved() {
        // Arrange
        String notes = "Approved by admin";

        // Act
        payoutRequest.approve(adminId, notes);

        // Assert
        assertThat(payoutRequest.getStatus()).isEqualTo(PayoutStatus.APPROVED);
        assertThat(payoutRequest.getReviewedBy()).isEqualTo(adminId);
        assertThat(payoutRequest.getReviewNotes()).isEqualTo(notes);
        assertThat(payoutRequest.getReviewedAt()).isNotNull();
    }

    @Test
    void approve_FromNonPendingStatus_ShouldThrowException() {
        // Arrange
        payoutRequest.setStatus(PayoutStatus.APPROVED);

        // Act & Assert
        assertThatThrownBy(() -> payoutRequest.approve(adminId, "notes"))
                .isInstanceOf(PayoutsException.class)
                .hasMessageContaining("Can only approve pending payouts");
    }

    @Test
    void reject_FromPendingStatus_ShouldTransitionToRejected() {
        // Arrange
        String notes = "Suspicious activity detected";

        // Act
        payoutRequest.reject(adminId, notes);

        // Assert
        assertThat(payoutRequest.getStatus()).isEqualTo(PayoutStatus.REJECTED);
        assertThat(payoutRequest.getReviewedBy()).isEqualTo(adminId);
        assertThat(payoutRequest.getReviewNotes()).isEqualTo(notes);
        assertThat(payoutRequest.getReviewedAt()).isNotNull();
    }

    @Test
    void reject_FromNonPendingStatus_ShouldThrowException() {
        // Arrange
        payoutRequest.setStatus(PayoutStatus.COMPLETED);

        // Act & Assert
        assertThatThrownBy(() -> payoutRequest.reject(adminId, "notes"))
                .isInstanceOf(PayoutsException.class)
                .hasMessageContaining("Can only reject pending payouts");
    }

    @Test
    void markAsProcessing_FromApprovedStatus_ShouldTransitionToProcessing() {
        // Arrange
        payoutRequest.setStatus(PayoutStatus.APPROVED);

        // Act
        payoutRequest.markAsProcessing();

        // Assert
        assertThat(payoutRequest.getStatus()).isEqualTo(PayoutStatus.PROCESSING);
        assertThat(payoutRequest.getProcessedAt()).isNotNull();
    }

    @Test
    void markAsProcessing_FromNonApprovedStatus_ShouldThrowException() {
        // Arrange - status is PENDING

        // Act & Assert
        assertThatThrownBy(() -> payoutRequest.markAsProcessing())
                .isInstanceOf(PayoutsException.class)
                .hasMessageContaining("Can only process approved payouts");
    }

    @Test
    void markAsCompleted_FromProcessingStatus_ShouldTransitionToCompleted() {
        // Arrange
        payoutRequest.setStatus(PayoutStatus.PROCESSING);
        String providerReference = "PAY_123456789";

        // Act
        payoutRequest.markAsCompleted(providerReference);

        // Assert
        assertThat(payoutRequest.getStatus()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(payoutRequest.getProviderReference()).isEqualTo(providerReference);
        assertThat(payoutRequest.getCompletedAt()).isNotNull();
    }

    @Test
    void markAsCompleted_FromNonProcessingStatus_ShouldThrowException() {
        // Arrange - status is PENDING

        // Act & Assert
        assertThatThrownBy(() -> payoutRequest.markAsCompleted("PAY_123"))
                .isInstanceOf(PayoutsException.class)
                .hasMessageContaining("Can only complete processing payouts");
    }

    @Test
    void markAsFailed_FromProcessingStatus_ShouldTransitionToFailed() {
        // Arrange
        payoutRequest.setStatus(PayoutStatus.PROCESSING);
        String failureReason = "Insufficient funds in provider account";

        // Act
        payoutRequest.markAsFailed(failureReason);

        // Assert
        assertThat(payoutRequest.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(payoutRequest.getFailureReason()).isEqualTo(failureReason);
    }

    @Test
    void markAsFailed_FromNonProcessingStatus_ShouldThrowException() {
        // Arrange - status is PENDING

        // Act & Assert
        assertThatThrownBy(() -> payoutRequest.markAsFailed("error"))
                .isInstanceOf(PayoutsException.class)
                .hasMessageContaining("Can only fail processing payouts");
    }

    @Test
    void stateMachine_FullHappyPath_ShouldTransitionCorrectly() {
        // PENDING -> APPROVED -> PROCESSING -> COMPLETED

        // Start: PENDING
        assertThat(payoutRequest.getStatus()).isEqualTo(PayoutStatus.PENDING);

        // Transition to APPROVED
        payoutRequest.approve(adminId, "Looks good");
        assertThat(payoutRequest.getStatus()).isEqualTo(PayoutStatus.APPROVED);

        // Transition to PROCESSING
        payoutRequest.markAsProcessing();
        assertThat(payoutRequest.getStatus()).isEqualTo(PayoutStatus.PROCESSING);

        // Transition to COMPLETED
        payoutRequest.markAsCompleted("PAY_123");
        assertThat(payoutRequest.getStatus()).isEqualTo(PayoutStatus.COMPLETED);
    }

    @Test
    void stateMachine_RejectionPath_ShouldEndAtRejected() {
        // PENDING -> REJECTED

        // Start: PENDING
        assertThat(payoutRequest.getStatus()).isEqualTo(PayoutStatus.PENDING);

        // Transition to REJECTED
        payoutRequest.reject(adminId, "Failed verification");
        assertThat(payoutRequest.getStatus()).isEqualTo(PayoutStatus.REJECTED);

        // Cannot transition further
        assertThatThrownBy(() -> payoutRequest.markAsProcessing())
                .isInstanceOf(PayoutsException.class);
    }

    @Test
    void stateMachine_FailurePath_ShouldEndAtFailed() {
        // PENDING -> APPROVED -> PROCESSING -> FAILED

        payoutRequest.approve(adminId, "Approved");
        payoutRequest.markAsProcessing();
        
        // Transition to FAILED
        payoutRequest.markAsFailed("Bank transfer failed");
        assertThat(payoutRequest.getStatus()).isEqualTo(PayoutStatus.FAILED);

        // Cannot transition further
        assertThatThrownBy(() -> payoutRequest.markAsCompleted("PAY_123"))
                .isInstanceOf(PayoutsException.class);
    }

    @Test
    void approve_WithNullAdminId_ShouldStillSucceed() {
        // Act
        payoutRequest.approve(null, "Auto-approved");

        // Assert
        assertThat(payoutRequest.getStatus()).isEqualTo(PayoutStatus.APPROVED);
        assertThat(payoutRequest.getReviewedBy()).isNull();
        assertThat(payoutRequest.getReviewedAt()).isNotNull();
    }

    @Test
    void approve_WithNullNotes_ShouldStillSucceed() {
        // Act
        payoutRequest.approve(adminId, null);

        // Assert
        assertThat(payoutRequest.getStatus()).isEqualTo(PayoutStatus.APPROVED);
        assertThat(payoutRequest.getReviewNotes()).isNull();
    }

    @Test
    void markAsCompleted_ShouldSetCompletedAtTimestamp() {
        // Arrange
        payoutRequest.setStatus(PayoutStatus.PROCESSING);
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        // Act
        payoutRequest.markAsCompleted("PAY_123");

        // Assert
        assertThat(payoutRequest.getCompletedAt()).isAfter(before);
        assertThat(payoutRequest.getCompletedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void approve_ShouldSetReviewedAtTimestamp() {
        // Arrange
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        // Act
        payoutRequest.approve(adminId, "Approved");

        // Assert
        assertThat(payoutRequest.getReviewedAt()).isAfter(before);
        assertThat(payoutRequest.getReviewedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void markAsProcessing_ShouldSetProcessedAtTimestamp() {
        // Arrange
        payoutRequest.setStatus(PayoutStatus.APPROVED);
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        // Act
        payoutRequest.markAsProcessing();

        // Assert
        assertThat(payoutRequest.getProcessedAt()).isAfter(before);
        assertThat(payoutRequest.getProcessedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void invalidStateTransition_FromCompletedToFailed_ShouldThrowException() {
        // Arrange
        payoutRequest.setStatus(PayoutStatus.COMPLETED);

        // Act & Assert
        assertThatThrownBy(() -> payoutRequest.markAsFailed("error"))
                .isInstanceOf(PayoutsException.class);
    }

    @Test
    void invalidStateTransition_FromRejectedToApproved_ShouldThrowException() {
        // Arrange
        payoutRequest.setStatus(PayoutStatus.REJECTED);

        // Act & Assert
        assertThatThrownBy(() -> payoutRequest.approve(adminId, "notes"))
                .isInstanceOf(PayoutsException.class);
    }
}

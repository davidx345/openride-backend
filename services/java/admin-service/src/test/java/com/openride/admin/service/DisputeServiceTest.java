package com.openride.admin.service;

import com.openride.admin.dto.CreateDisputeRequest;
import com.openride.admin.dto.ResolveDisputeRequest;
import com.openride.admin.entity.Dispute;
import com.openride.admin.entity.Dispute.DisputeStatus;
import com.openride.admin.entity.Dispute.DisputeType;
import com.openride.admin.repository.DisputeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private DisputeService disputeService;

    private UUID bookingId;
    private UUID reporterId;
    private UUID reportedId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        reporterId = UUID.randomUUID();
        reportedId = UUID.randomUUID();
        adminId = UUID.randomUUID();
    }

    @Test
    void createDispute_Success() {
        // Given
        CreateDisputeRequest request = CreateDisputeRequest.builder()
                .bookingId(bookingId)
                .reporterId(reporterId)
                .reportedId(reportedId)
                .disputeType(DisputeType.PAYMENT)
                .subject("Payment not received")
                .description("Driver did not receive payment")
                .evidenceUrls(new String[]{"https://example.com/proof.jpg"})
                .build();

        Dispute savedDispute = new Dispute();
        savedDispute.setId(UUID.randomUUID());
        savedDispute.setBookingId(bookingId);
        savedDispute.setStatus(DisputeStatus.OPEN);

        when(disputeRepository.save(any(Dispute.class))).thenReturn(savedDispute);

        // When
        Dispute result = disputeService.createDispute(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(DisputeStatus.OPEN);
        verify(disputeRepository).save(any(Dispute.class));
        verify(auditService).logAction(eq("DISPUTE"), eq(savedDispute.getId()), 
                eq("CREATE"), eq(reporterId), any());
    }

    @Test
    void resolveDispute_Success() {
        // Given
        UUID disputeId = UUID.randomUUID();
        Dispute dispute = new Dispute();
        dispute.setId(disputeId);
        dispute.setStatus(DisputeStatus.OPEN);

        ResolveDisputeRequest request = ResolveDisputeRequest.builder()
                .status(DisputeStatus.RESOLVED)
                .resolutionNotes("Issue resolved - payment confirmed")
                .build();

        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenReturn(dispute);

        // When
        Dispute result = disputeService.resolveDispute(disputeId, request, adminId);

        // Then
        assertThat(result.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(result.getResolvedBy()).isEqualTo(adminId);
        assertThat(result.getResolutionNotes()).isEqualTo("Issue resolved - payment confirmed");
        verify(auditService).logAction(eq("DISPUTE"), eq(disputeId), 
                eq("RESOLVE"), eq(adminId), any());
    }

    @Test
    void resolveDispute_InvalidStatusTransition() {
        // Given
        UUID disputeId = UUID.randomUUID();
        Dispute dispute = new Dispute();
        dispute.setId(disputeId);
        dispute.setStatus(DisputeStatus.RESOLVED);

        ResolveDisputeRequest request = ResolveDisputeRequest.builder()
                .status(DisputeStatus.REJECTED)
                .resolutionNotes("Cannot change resolved dispute")
                .build();

        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

        // When & Then
        assertThatThrownBy(() -> disputeService.resolveDispute(disputeId, request, adminId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot resolve dispute with status");
    }

    @Test
    void assignDispute_Success() {
        // Given
        UUID disputeId = UUID.randomUUID();
        Dispute dispute = new Dispute();
        dispute.setId(disputeId);
        dispute.setStatus(DisputeStatus.OPEN);

        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenReturn(dispute);

        // When
        Dispute result = disputeService.assignDispute(disputeId, adminId);

        // Then
        assertThat(result.getStatus()).isEqualTo(DisputeStatus.IN_PROGRESS);
        assertThat(result.getResolvedBy()).isEqualTo(adminId);
        verify(auditService).logAction(eq("DISPUTE"), eq(disputeId), 
                eq("ASSIGN"), eq(adminId), any());
    }

    @Test
    void getOpenDisputes_Success() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Dispute dispute1 = new Dispute();
        dispute1.setStatus(DisputeStatus.OPEN);
        Dispute dispute2 = new Dispute();
        dispute2.setStatus(DisputeStatus.IN_PROGRESS);

        Page<Dispute> page = new PageImpl<>(List.of(dispute1, dispute2));
        when(disputeRepository.findOpenDisputes(pageable)).thenReturn(page);

        // When
        Page<Dispute> result = disputeService.getOpenDisputes(pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(DisputeStatus.OPEN);
    }

    @Test
    void getDisputeStatistics_Success() {
        // Given
        when(disputeRepository.countByStatus(DisputeStatus.OPEN)).thenReturn(5L);
        when(disputeRepository.countByStatus(DisputeStatus.IN_PROGRESS)).thenReturn(3L);
        when(disputeRepository.countByStatus(DisputeStatus.RESOLVED)).thenReturn(10L);
        when(disputeRepository.countByStatus(DisputeStatus.REJECTED)).thenReturn(2L);

        // When
        Map<String, Long> stats = disputeService.getDisputeStatistics();

        // Then
        assertThat(stats).hasSize(4);
        assertThat(stats.get("OPEN")).isEqualTo(5L);
        assertThat(stats.get("IN_PROGRESS")).isEqualTo(3L);
        assertThat(stats.get("RESOLVED")).isEqualTo(10L);
        assertThat(stats.get("REJECTED")).isEqualTo(2L);
    }
}

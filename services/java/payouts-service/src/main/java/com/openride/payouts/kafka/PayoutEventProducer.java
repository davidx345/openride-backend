package com.openride.payouts.kafka;

import com.openride.payouts.dto.PayoutEvent;
import com.openride.payouts.model.PayoutRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for payout events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.payout-requested}")
    private String payoutRequestedTopic;

    @Value("${kafka.topics.payout-approved}")
    private String payoutApprovedTopic;

    @Value("${kafka.topics.payout-rejected}")
    private String payoutRejectedTopic;

    @Value("${kafka.topics.payout-completed}")
    private String payoutCompletedTopic;

    @Value("${kafka.topics.payout-failed}")
    private String payoutFailedTopic;

    public void publishPayoutRequested(PayoutRequest payout) {
        PayoutEvent event = buildPayoutEvent(payout, "REQUESTED");
        sendEvent(payoutRequestedTopic, payout.getId().toString(), event);
    }

    public void publishPayoutApproved(PayoutRequest payout) {
        PayoutEvent event = buildPayoutEvent(payout, "APPROVED");
        sendEvent(payoutApprovedTopic, payout.getId().toString(), event);
    }

    public void publishPayoutRejected(PayoutRequest payout) {
        PayoutEvent event = buildPayoutEvent(payout, "REJECTED");
        sendEvent(payoutRejectedTopic, payout.getId().toString(), event);
    }

    public void publishPayoutCompleted(PayoutRequest payout) {
        PayoutEvent event = buildPayoutEvent(payout, "COMPLETED");
        sendEvent(payoutCompletedTopic, payout.getId().toString(), event);
    }

    public void publishPayoutFailed(PayoutRequest payout, String failureReason) {
        PayoutEvent event = buildPayoutEvent(payout, "FAILED");
        event.setFailureReason(failureReason);
        sendEvent(payoutFailedTopic, payout.getId().toString(), event);
    }

    private PayoutEvent buildPayoutEvent(PayoutRequest payout, String eventType) {
        return PayoutEvent.builder()
                .payoutId(payout.getId())
                .driverId(payout.getDriverId())
                .walletId(payout.getWalletId())
                .amount(payout.getAmount())
                .status(payout.getStatus())
                .eventType(eventType)
                .eventTimestamp(LocalDateTime.now())
                .build();
    }

    private void sendEvent(String topic, String key, PayoutEvent event) {
        log.debug("Publishing payout event to topic: {}, event: {}", topic, event.getEventType());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Payout event published successfully: topic={}, event={}, partition={}, offset={}",
                        topic, event.getEventType(), 
                        result.getRecordMetadata().partition(), 
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish payout event: topic={}, event={}, error={}",
                        topic, event.getEventType(), ex.getMessage(), ex);
            }
        });
    }
}

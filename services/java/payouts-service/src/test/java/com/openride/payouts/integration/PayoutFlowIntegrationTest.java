package com.openride.payouts.integration;

import com.openride.payouts.dto.PayoutRequestDto;
import com.openride.payouts.kafka.TripCompletedConsumer;
import com.openride.payouts.model.dto.TripCompletedEvent;
import com.openride.payouts.model.entity.BankAccount;
import com.openride.payouts.model.entity.DriverWallet;
import com.openride.payouts.model.entity.PayoutRequest;
import com.openride.payouts.model.enums.PayoutStatus;
import com.openride.payouts.repository.BankAccountRepository;
import com.openride.payouts.repository.DriverWalletRepository;
import com.openride.payouts.repository.PayoutRequestRepository;
import com.openride.payouts.service.PayoutService;
import com.openride.payouts.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Integration test for full payout flow.
 * Tests the end-to-end workflow from trip completion to payout completion.
 */
@SpringBootTest
@Testcontainers
class PayoutFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:14-alpine"))
            .withDatabaseName("payouts_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private TripCompletedConsumer tripCompletedConsumer;

    @Autowired
    private PayoutService payoutService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private DriverWalletRepository walletRepository;

    @Autowired
    private PayoutRequestRepository payoutRequestRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    private UUID driverId;
    private BankAccount bankAccount;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();

        // Clean up data
        payoutRequestRepository.deleteAll();
        bankAccountRepository.deleteAll();
        walletRepository.deleteAll();

        // Create verified bank account
        bankAccount = new BankAccount();
        bankAccount.setDriverId(driverId);
        bankAccount.setAccountNumber("0123456789");
        bankAccount.setBankCode("058");
        bankAccount.setBankName("GTBank");
        bankAccount.setAccountName("John Doe");
        bankAccount.setIsVerified(true);
        bankAccount.setIsPrimary(true);
        bankAccount.setCreatedAt(LocalDateTime.now());
        bankAccount.setUpdatedAt(LocalDateTime.now());
        bankAccount = bankAccountRepository.save(bankAccount);
    }

    @Test
    void fullPayoutFlow_FromTripCompletionToPayoutCompletion() {
        // Step 1: Simulate trip completion event
        TripCompletedEvent tripEvent = new TripCompletedEvent();
        tripEvent.setTripId(UUID.randomUUID());
        tripEvent.setDriverId(driverId);
        tripEvent.setTotalPrice(BigDecimal.valueOf(10000.00));
        tripEvent.setCompletedAt(LocalDateTime.now());

        tripCompletedConsumer.consumeTripCompleted(tripEvent, null);

        // Step 2: Verify wallet was credited
        await().atMost(5, SECONDS).untilAsserted(() -> {
            DriverWallet wallet = walletRepository.findByDriverId(driverId).orElseThrow();
            assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(8500.00)); // 85% of 10000
            assertThat(wallet.getTotalEarnings()).isEqualByComparingTo(BigDecimal.valueOf(8500.00));
        });

        // Step 3: Driver requests payout
        PayoutRequestDto payoutRequest = new PayoutRequestDto();
        payoutRequest.setAmount(BigDecimal.valueOf(5000.00));
        payoutRequest.setBankAccountId(bankAccount.getId());

        payoutService.requestPayout(driverId, payoutRequest);

        // Step 4: Verify payout was created and wallet was updated
        await().atMost(5, SECONDS).untilAsserted(() -> {
            DriverWallet wallet = walletRepository.findByDriverId(driverId).orElseThrow();
            assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(3500.00));
            assertThat(wallet.getPendingPayout()).isEqualByComparingTo(BigDecimal.valueOf(5000.00));

            PayoutRequest payout = payoutRequestRepository.findByDriverIdAndStatus(
                    driverId, PayoutStatus.PENDING, null).getContent().get(0);
            assertThat(payout.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000.00));
            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PENDING);
        });

        // Step 5: Admin approves payout
        PayoutRequest pendingPayout = payoutRequestRepository.findByDriverIdAndStatus(
                driverId, PayoutStatus.PENDING, null).getContent().get(0);
        
        com.openride.payouts.dto.PayoutReviewRequest reviewRequest = 
                new com.openride.payouts.dto.PayoutReviewRequest();
        reviewRequest.setNotes("Approved");

        payoutService.approvePayoutRequest(pendingPayout.getId(), UUID.randomUUID(), reviewRequest);

        // Step 6: Verify payout status changed to APPROVED
        await().atMost(5, SECONDS).untilAsserted(() -> {
            PayoutRequest payout = payoutRequestRepository.findById(pendingPayout.getId()).orElseThrow();
            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.APPROVED);
        });

        // Step 7: Settlement service processes the payout
        PayoutRequest approvedPayout = payoutRequestRepository.findById(pendingPayout.getId()).orElseThrow();
        approvedPayout.markAsProcessing();
        payoutRequestRepository.save(approvedPayout);

        // Step 8: Mark as completed (simulating successful bank transfer)
        payoutService.markAsCompleted(approvedPayout.getId(), "PAY_TEST_123");

        // Step 9: Verify final state
        await().atMost(5, SECONDS).untilAsserted(() -> {
            PayoutRequest completedPayout = payoutRequestRepository.findById(pendingPayout.getId()).orElseThrow();
            assertThat(completedPayout.getStatus()).isEqualTo(PayoutStatus.COMPLETED);
            assertThat(completedPayout.getProviderReference()).isEqualTo("PAY_TEST_123");

            DriverWallet finalWallet = walletRepository.findByDriverId(driverId).orElseThrow();
            assertThat(finalWallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(3500.00));
            assertThat(finalWallet.getPendingPayout()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(finalWallet.getTotalPaidOut()).isEqualByComparingTo(BigDecimal.valueOf(5000.00));
        });
    }

    @Test
    void payoutFlow_WithRejection_ShouldReleaseFunds() {
        // Step 1: Credit wallet
        TripCompletedEvent tripEvent = new TripCompletedEvent();
        tripEvent.setTripId(UUID.randomUUID());
        tripEvent.setDriverId(driverId);
        tripEvent.setTotalPrice(BigDecimal.valueOf(20000.00));
        tripEvent.setCompletedAt(LocalDateTime.now());

        tripCompletedConsumer.consumeTripCompleted(tripEvent, null);

        await().atMost(5, SECONDS).untilAsserted(() -> {
            DriverWallet wallet = walletRepository.findByDriverId(driverId).orElseThrow();
            assertThat(wallet.getAvailableBalance()).isGreaterThan(BigDecimal.ZERO);
        });

        // Step 2: Request payout
        PayoutRequestDto payoutRequest = new PayoutRequestDto();
        payoutRequest.setAmount(BigDecimal.valueOf(10000.00));
        payoutRequest.setBankAccountId(bankAccount.getId());

        payoutService.requestPayout(driverId, payoutRequest);

        // Step 3: Reject payout
        PayoutRequest pendingPayout = payoutRequestRepository.findByDriverIdAndStatus(
                driverId, PayoutStatus.PENDING, null).getContent().get(0);
        
        com.openride.payouts.dto.PayoutReviewRequest reviewRequest = 
                new com.openride.payouts.dto.PayoutReviewRequest();
        reviewRequest.setNotes("Suspicious activity");

        payoutService.rejectPayoutRequest(pendingPayout.getId(), UUID.randomUUID(), reviewRequest);

        // Step 4: Verify funds were released
        await().atMost(5, SECONDS).untilAsserted(() -> {
            DriverWallet wallet = walletRepository.findByDriverId(driverId).orElseThrow();
            assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(17000.00));
            assertThat(wallet.getPendingPayout()).isEqualByComparingTo(BigDecimal.ZERO);

            PayoutRequest rejected = payoutRequestRepository.findById(pendingPayout.getId()).orElseThrow();
            assertThat(rejected.getStatus()).isEqualTo(PayoutStatus.REJECTED);
        });
    }

    @Test
    void payoutFlow_WithFailure_ShouldReleaseFunds() {
        // Step 1: Setup wallet with balance
        TripCompletedEvent tripEvent = new TripCompletedEvent();
        tripEvent.setTripId(UUID.randomUUID());
        tripEvent.setDriverId(driverId);
        tripEvent.setTotalPrice(BigDecimal.valueOf(15000.00));
        tripEvent.setCompletedAt(LocalDateTime.now());

        tripCompletedConsumer.consumeTripCompleted(tripEvent, null);

        await().atMost(5, SECONDS).untilAsserted(() -> {
            assertThat(walletRepository.findByDriverId(driverId)).isPresent();
        });

        // Step 2: Request and approve payout
        PayoutRequestDto payoutRequest = new PayoutRequestDto();
        payoutRequest.setAmount(BigDecimal.valueOf(8000.00));
        payoutRequest.setBankAccountId(bankAccount.getId());

        payoutService.requestPayout(driverId, payoutRequest);

        PayoutRequest pending = payoutRequestRepository.findByDriverIdAndStatus(
                driverId, PayoutStatus.PENDING, null).getContent().get(0);
        
        com.openride.payouts.dto.PayoutReviewRequest reviewRequest = 
                new com.openride.payouts.dto.PayoutReviewRequest();
        reviewRequest.setNotes("Approved");

        payoutService.approvePayoutRequest(pending.getId(), UUID.randomUUID(), reviewRequest);

        // Step 3: Mark as processing
        PayoutRequest approved = payoutRequestRepository.findById(pending.getId()).orElseThrow();
        approved.markAsProcessing();
        payoutRequestRepository.save(approved);

        // Step 4: Mark as failed
        payoutService.markAsFailed(approved.getId(), "Bank transfer failed");

        // Step 5: Verify funds were released
        await().atMost(5, SECONDS).untilAsserted(() -> {
            DriverWallet wallet = walletRepository.findByDriverId(driverId).orElseThrow();
            assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(12750.00));
            assertThat(wallet.getPendingPayout()).isEqualByComparingTo(BigDecimal.ZERO);

            PayoutRequest failed = payoutRequestRepository.findById(pending.getId()).orElseThrow();
            assertThat(failed.getStatus()).isEqualTo(PayoutStatus.FAILED);
            assertThat(failed.getFailureReason()).isEqualTo("Bank transfer failed");
        });
    }

    @Test
    void multipleTrips_ShouldAccumulateEarnings() {
        // Complete 3 trips
        for (int i = 0; i < 3; i++) {
            TripCompletedEvent tripEvent = new TripCompletedEvent();
            tripEvent.setTripId(UUID.randomUUID());
            tripEvent.setDriverId(driverId);
            tripEvent.setTotalPrice(BigDecimal.valueOf(5000.00));
            tripEvent.setCompletedAt(LocalDateTime.now());

            tripCompletedConsumer.consumeTripCompleted(tripEvent, null);
        }

        // Verify accumulated earnings
        await().atMost(10, SECONDS).untilAsserted(() -> {
            DriverWallet wallet = walletRepository.findByDriverId(driverId).orElseThrow();
            // 3 trips * 5000 * 0.85 = 12,750
            assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(12750.00));
            assertThat(wallet.getTotalEarnings()).isEqualByComparingTo(BigDecimal.valueOf(12750.00));
        });
    }
}

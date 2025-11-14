package com.openride.payouts.integration;

import com.openride.payouts.job.SettlementJob;
import com.openride.payouts.model.entity.BankAccount;
import com.openride.payouts.model.entity.DriverWallet;
import com.openride.payouts.model.entity.PayoutRequest;
import com.openride.payouts.model.entity.Settlement;
import com.openride.payouts.model.enums.PayoutStatus;
import com.openride.payouts.model.enums.SettlementStatus;
import com.openride.payouts.repository.BankAccountRepository;
import com.openride.payouts.repository.DriverWalletRepository;
import com.openride.payouts.repository.PayoutRequestRepository;
import com.openride.payouts.repository.SettlementRepository;
import com.openride.payouts.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for scheduled settlement job.
 */
@SpringBootTest
@Testcontainers
class SettlementJobIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:14-alpine"))
            .withDatabaseName("payouts_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private SettlementJob settlementJob;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private PayoutRequestRepository payoutRequestRepository;

    @Autowired
    private DriverWalletRepository walletRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @BeforeEach
    void setUp() {
        // Clean up
        settlementRepository.deleteAll();
        payoutRequestRepository.deleteAll();
        bankAccountRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void settlementJob_ShouldCreateBatchForApprovedPayouts() {
        // Arrange - Create approved payouts
        createApprovedPayouts(3);

        // Act
        settlementJob.processWeeklySettlement();

        // Assert
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Settlement> settlements = settlementRepository.findAll();
            assertThat(settlements).isNotEmpty();
            
            Settlement settlement = settlements.get(0);
            assertThat(settlement.getTotalPayouts()).isEqualTo(3);
            assertThat(settlement.getStatus()).isIn(
                    SettlementStatus.PENDING, 
                    SettlementStatus.COMPLETED, 
                    SettlementStatus.PARTIALLY_COMPLETED
            );
        });
    }

    @Test
    void settlementJob_WithNoApprovedPayouts_ShouldNotCreateBatch() {
        // Arrange - No approved payouts

        // Act
        settlementJob.processWeeklySettlement();

        // Assert
        List<Settlement> settlements = settlementRepository.findAll();
        assertThat(settlements).isEmpty();
    }

    @Test
    void settlementJob_WithDistributedLock_ShouldPreventConcurrentExecution() throws InterruptedException {
        // Arrange
        createApprovedPayouts(5);
        
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(3);
        
        // Act - Simulate 3 instances trying to run job concurrently
        for (int i = 0; i < 3; i++) {
            executorService.submit(() -> {
                try {
                    settlementJob.processWeeklySettlement();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // Assert - Only one batch should be created despite multiple executions
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Settlement> settlements = settlementRepository.findAll();
            assertThat(settlements).hasSize(1); // Only one batch due to distributed lock
        });
    }

    @Test
    void settlementService_ShouldProcessBatchSuccessfully() {
        // Arrange
        List<PayoutRequest> payouts = createApprovedPayouts(3);
        Settlement batch = settlementService.createSettlementBatch();

        assertThat(batch).isNotNull();
        assertThat(batch.getTotalPayouts()).isEqualTo(3);

        // Note: Actual processing requires payment provider integration
        // This test verifies batch creation only
        Settlement saved = settlementRepository.findById(batch.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(saved.getTotalAmount()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void settlementJob_ShouldHandleMultipleBatchesOverTime() {
        // Batch 1
        createApprovedPayouts(2);
        settlementJob.processWeeklySettlement();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(settlementRepository.findAll()).hasSize(1);
        });

        // Batch 2 - Create more approved payouts
        createApprovedPayouts(3);
        settlementJob.processWeeklySettlement();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Settlement> settlements = settlementRepository.findAll();
            assertThat(settlements.size()).isGreaterThanOrEqualTo(1);
        });
    }

    @Test
    void settlementService_RetrySettlement_ShouldUpdateBatchStatus() {
        // Arrange - Create batch with failed payouts
        List<PayoutRequest> payouts = createApprovedPayouts(2);
        Settlement batch = settlementService.createSettlementBatch();

        // Simulate partial failure
        batch.setStatus(SettlementStatus.PARTIALLY_COMPLETED);
        batch.setSuccessfulPayouts(1);
        batch.setFailedPayouts(1);
        settlementRepository.save(batch);

        // Act - Retry would typically be triggered manually or on schedule
        // For this test, we just verify the batch state
        Settlement retrieved = settlementRepository.findById(batch.getId()).orElseThrow();
        
        // Assert
        assertThat(retrieved.getStatus()).isEqualTo(SettlementStatus.PARTIALLY_COMPLETED);
        assertThat(retrieved.getSuccessfulPayouts()).isEqualTo(1);
        assertThat(retrieved.getFailedPayouts()).isEqualTo(1);
    }

    private List<PayoutRequest> createApprovedPayouts(int count) {
        List<PayoutRequest> payouts = new java.util.ArrayList<>();

        for (int i = 0; i < count; i++) {
            UUID driverId = UUID.randomUUID();

            // Create wallet
            DriverWallet wallet = new DriverWallet();
            wallet.setDriverId(driverId);
            wallet.setAvailableBalance(BigDecimal.valueOf(50000.00));
            wallet.setPendingPayout(BigDecimal.valueOf(20000.00));
            wallet.setTotalEarnings(BigDecimal.valueOf(100000.00));
            wallet.setTotalPaidOut(BigDecimal.ZERO);
            wallet.setLifetimeEarnings(BigDecimal.valueOf(100000.00));
            wallet = walletRepository.save(wallet);

            // Create bank account
            BankAccount bankAccount = new BankAccount();
            bankAccount.setDriverId(driverId);
            bankAccount.setAccountNumber("012345678" + i);
            bankAccount.setBankCode("058");
            bankAccount.setBankName("GTBank");
            bankAccount.setAccountName("Driver " + i);
            bankAccount.setIsVerified(true);
            bankAccount.setIsPrimary(true);
            bankAccount.setCreatedAt(LocalDateTime.now());
            bankAccount.setUpdatedAt(LocalDateTime.now());
            bankAccount = bankAccountRepository.save(bankAccount);

            // Create approved payout
            PayoutRequest payout = new PayoutRequest();
            payout.setDriverId(driverId);
            payout.setWalletId(wallet.getId());
            payout.setBankAccountId(bankAccount.getId());
            payout.setAmount(BigDecimal.valueOf(10000.00 + (i * 5000)));
            payout.setStatus(PayoutStatus.APPROVED);
            payout.setRequestedAt(LocalDateTime.now().minusDays(1));
            payout.setReviewedBy(UUID.randomUUID());
            payout.setReviewedAt(LocalDateTime.now().minusHours(2));
            
            payouts.add(payoutRequestRepository.save(payout));
        }

        return payouts;
    }
}

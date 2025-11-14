# Phase 9 Testing Documentation

## Overview
This document provides comprehensive documentation for the test suite of the Payouts & Financial Management Service (Phase 9).

**Total Test Files**: 14 Java test files + 1 test configuration  
**Test Coverage Target**: 80%+ service layer, 90%+ entities, 70%+ controllers  
**Testing Frameworks**: JUnit 5, Mockito, Spring Boot Test, Testcontainers, Awaitility

---

## Test Structure

### Unit Tests (8 files)

#### 1. **EarningsServiceTest.java**
**Purpose**: Test earnings processing and commission calculation logic

**Key Test Cases**:
- ✅ `processEarnings_WhenWalletExists_ShouldCreditEarnings` - Verify earnings credited to existing wallet
- ✅ `processEarnings_WhenWalletDoesNotExist_ShouldCreateWalletAndCreditEarnings` - Wallet auto-creation
- ✅ `processEarnings_ShouldCalculateCorrectCommission` - Commission accuracy (15% platform, 85% driver)
- ✅ `processEarnings_WithZeroAmount_ShouldNotCreditWallet` - Edge case handling
- ✅ `processEarnings_WithDifferentCommissionRate_ShouldCalculateCorrectly` - Configurable commission
- ✅ Ledger entry creation and metadata validation

**Mocks**: `DriverWalletRepository`, `EarningsLedgerRepository`, `FinancialConfig`, `PayoutEventProducer`

---

#### 2. **WalletServiceTest.java**
**Purpose**: Test wallet operations and queries

**Key Test Cases**:
- ✅ `getWallet_WhenWalletExists_ShouldReturnWalletResponse` - Wallet retrieval
- ✅ `getWallet_WhenWalletDoesNotExist_ShouldThrowException` - Error handling
- ✅ `getOrCreateWallet_WhenWalletDoesNotExist_ShouldCreateNewWallet` - Auto-creation logic
- ✅ `getTransactionHistory_ShouldReturnPagedLedgerEntries` - Transaction pagination
- ✅ `getEarningsSummary_ShouldReturnCompleteEarningsSummary` - Summary with trip count, last payout, etc.
- ✅ `getAvailableBalance_ShouldReturnCorrectBalance` - Balance queries

**Mocks**: `DriverWalletRepository`, `EarningsLedgerRepository`, `PayoutRequestRepository`

---

#### 3. **PayoutServiceTest.java**
**Purpose**: Test payout lifecycle and business rules

**Key Test Cases**:
- ✅ `requestPayout_WithValidRequest_ShouldCreatePendingPayout` - Payout creation and fund reservation
- ✅ `requestPayout_WithAmountBelowMinimum_ShouldThrowException` - Minimum ₦5,000 validation
- ✅ `requestPayout_WithInsufficientBalance_ShouldThrowException` - Balance validation
- ✅ `requestPayout_WithUnverifiedBankAccount_ShouldThrowException` - Bank verification check
- ✅ `requestPayout_WithExistingPendingPayout_ShouldThrowException` - Single pending payout rule
- ✅ `approvePayoutRequest_ShouldChangeStatusToApproved` - Admin approval workflow
- ✅ `rejectPayoutRequest_ShouldReleaseReservedFunds` - Fund release on rejection
- ✅ `markAsCompleted_ShouldUpdateWalletAndPayoutStatus` - Completion workflow
- ✅ `markAsFailed_ShouldReleaseReservedFundsAndSetFailureReason` - Failure handling

**Mocks**: All repositories, `FinancialConfig`, `PayoutEventProducer`, `AuditService`

---

#### 4. **SettlementServiceTest.java**
**Purpose**: Test settlement batch processing

**Key Test Cases**:
- ✅ `createSettlementBatch_ShouldCreateBatchWithApprovedPayouts` - Batch creation
- ✅ `createSettlementBatch_WithNoApprovedPayouts_ShouldReturnNull` - Empty batch handling
- ✅ `processSettlement_WithSuccessfulTransfers_ShouldCompleteSettlement` - Successful batch processing
- ✅ `processSettlement_WithPartialFailures_ShouldMarkAsPartiallyCompleted` - Partial failure handling
- ✅ `processSettlement_WithAllFailures_ShouldMarkAsFailed` - Complete failure scenario
- ✅ `retrySettlement_ShouldOnlyRetryFailedPayouts` - Retry logic for failed payouts

**Mocks**: `SettlementRepository`, `PayoutRequestRepository`, `PaymentProviderFactory`, `PayoutService`

---

#### 5. **BankAccountServiceTest.java**
**Purpose**: Test bank account management

**Key Test Cases**:
- ✅ `addBankAccount_WithValidDetails_ShouldVerifyAndCreateAccount` - Account creation with Paystack verification
- ✅ `addBankAccount_WhenVerificationFails_ShouldThrowException` - Verification failure handling
- ✅ `addBankAccount_WhenOtherAccountsExist_ShouldNotSetAsPrimary` - Primary account logic
- ✅ `setPrimaryAccount_ShouldUpdatePrimaryStatus` - Primary account switching
- ✅ `deleteBankAccount_WhenAccountIsPrimary_ShouldThrowException` - Cannot delete primary
- ✅ `getPrimaryBankAccount_ShouldReturnPrimaryAccount` - Primary account retrieval

**Mocks**: `BankAccountRepository`, `BankVerificationService`, `AuditService`

---

#### 6. **DriverWalletTest.java** (Entity Tests)
**Purpose**: Test wallet entity business methods

**Key Test Cases**:
- ✅ `creditEarnings_ShouldUpdateBalancesCorrectly` - Balance updates
- ✅ `reserveForPayout_WithSufficientBalance_ShouldReserveAmount` - Fund reservation
- ✅ `reserveForPayout_WithInsufficientBalance_ShouldThrowException` - Insufficient funds
- ✅ `releaseReservedAmount_ShouldReturnFundsToAvailableBalance` - Fund release
- ✅ `completePayout_ShouldUpdateBalancesAndTotalPaidOut` - Payout completion
- ✅ Timestamp updates (lastEarningAt, lastPayoutAt)

**Mocks**: None (pure entity tests)

---

#### 7. **PayoutRequestTest.java** (Entity Tests)
**Purpose**: Test payout state machine transitions

**Key Test Cases**:
- ✅ `approve_FromPendingStatus_ShouldTransitionToApproved` - PENDING → APPROVED
- ✅ `reject_FromPendingStatus_ShouldTransitionToRejected` - PENDING → REJECTED
- ✅ `markAsProcessing_FromApprovedStatus_ShouldTransitionToProcessing` - APPROVED → PROCESSING
- ✅ `markAsCompleted_FromProcessingStatus_ShouldTransitionToCompleted` - PROCESSING → COMPLETED
- ✅ `markAsFailed_FromProcessingStatus_ShouldTransitionToFailed` - PROCESSING → FAILED
- ✅ `stateMachine_FullHappyPath_ShouldTransitionCorrectly` - Full workflow validation
- ✅ Invalid state transitions throw exceptions

**Mocks**: None (pure entity tests)

---

#### 8. **FinancialConfigTest.java**
**Purpose**: Test financial configuration and calculations

**Key Test Cases**:
- ✅ `config_ShouldLoadCommissionProperties` - Configuration binding
- ✅ `calculateDriverEarnings_WithStandardCommission_ShouldCalculateCorrectly` - 85% calculation
- ✅ `calculatePlatformCommission_WithStandardRate_ShouldCalculateCorrectly` - 15% calculation
- ✅ `calculateEarnings_ShouldEqualTotalPriceMinusCommission` - Mathematical correctness
- ✅ `calculateDriverEarnings_WithDecimalAmount_ShouldRoundCorrectly` - Decimal precision

**Mocks**: None (uses Spring Boot test context)

---

### Integration Tests (3 files)

#### 9. **PayoutFlowIntegrationTest.java**
**Purpose**: End-to-end payout workflow testing

**Technologies**:
- Testcontainers: PostgreSQL 14-alpine, Kafka 7.5.0
- Spring Boot Test
- Awaitility for async assertions

**Key Test Cases**:
- ✅ `fullPayoutFlow_FromTripCompletionToPayoutCompletion` - Complete happy path:
  1. Trip completed event → wallet credited
  2. Driver requests payout → funds reserved
  3. Admin approves → status changed
  4. Settlement processes → transfer initiated
  5. Transfer succeeds → payout completed
- ✅ `payoutFlow_WithRejection_ShouldReleaseFunds` - Rejection path with fund release
- ✅ `payoutFlow_WithFailure_ShouldReleaseFunds` - Failure path with fund release
- ✅ `multipleTrips_ShouldAccumulateEarnings` - Multiple trip earnings accumulation

**What It Tests**:
- Kafka consumer processing
- Database transactions
- Wallet state management
- Payout state machine
- Fund reservation/release

---

#### 10. **KafkaIntegrationTest.java**
**Purpose**: Kafka consumer and producer integration

**Technologies**:
- Testcontainers: Kafka, PostgreSQL
- Manual Kafka consumer for verification

**Key Test Cases**:
- ✅ `tripCompletedConsumer_ShouldProcessEventAndCreditWallet` - Consumer processing
- ✅ `tripCompletedConsumer_WithMultipleEvents_ShouldProcessSequentially` - Event ordering
- ✅ `payoutEventProducer_ShouldPublishPayoutRequested` - Event publishing
- ✅ `payoutEventProducer_ShouldPublishPayoutApproved` - Approval events
- ✅ `payoutEventProducer_ShouldPublishPayoutCompleted` - Completion events
- ✅ `payoutEventProducer_ShouldPublishPayoutFailed` - Failure events
- ✅ `tripCompletedConsumer_WithInvalidData_ShouldHandleGracefully` - Error handling

**What It Tests**:
- Kafka topic configuration
- Consumer group behavior
- Event serialization/deserialization
- Error handling and retries

---

#### 11. **SettlementJobIntegrationTest.java**
**Purpose**: Scheduled job execution and distributed locking

**Technologies**:
- Testcontainers: PostgreSQL, Redis 7-alpine
- ExecutorService for concurrency testing

**Key Test Cases**:
- ✅ `settlementJob_ShouldCreateBatchForApprovedPayouts` - Job execution
- ✅ `settlementJob_WithNoApprovedPayouts_ShouldNotCreateBatch` - Empty state handling
- ✅ `settlementJob_WithDistributedLock_ShouldPreventConcurrentExecution` - Redisson lock verification
- ✅ `settlementService_ShouldProcessBatchSuccessfully` - Batch processing
- ✅ `settlementJob_ShouldHandleMultipleBatchesOverTime` - Multiple executions
- ✅ `settlementService_RetrySettlement_ShouldUpdateBatchStatus` - Retry logic

**What It Tests**:
- Scheduled job execution
- Distributed locking (prevents duplicate settlements)
- Batch creation and processing
- Settlement retry mechanism

---

### Test Utilities (3 files)

#### 12. **TestDataBuilder.java**
**Purpose**: Fluent builders for test entities

**Builders**:
- `DriverWalletBuilder` - Wallet with customizable balances
- `PayoutRequestBuilder` - Payout with status transitions
- `BankAccountBuilder` - Bank accounts with verification
- `EarningsLedgerBuilder` - Ledger entries
- `SettlementBuilder` - Settlement batches

**Usage Example**:
```java
DriverWallet wallet = TestDataBuilder.wallet()
    .driverId(TEST_DRIVER_ID)
    .availableBalance(BigDecimal.valueOf(50000))
    .build();

PayoutRequest payout = TestDataBuilder.payout()
    .amount(BigDecimal.valueOf(20000))
    .approved(adminId)
    .build();
```

---

#### 13. **MockPaystackProvider.java**
**Purpose**: Mock payment provider for testing

**Features**:
- Configurable success/failure responses
- Pre-configured verification results
- Transfer result simulation
- Error message customization

**Usage Example**:
```java
MockPaystackProvider provider = MockPaystackProvider.alwaysSucceeds();

// Or configure specific behavior
provider.configureBankVerification("0123456789", "058", true, "John Doe", "GTBank");
provider.configureTransfer("REF_123", false, null, "Insufficient funds");
```

---

#### 14. **TestConstants.java**
**Purpose**: Shared test constants

**Constants**:
- Test UUIDs (drivers, wallets, trips, payouts)
- Test amounts (trip amounts, payout amounts, balances)
- Commission rates (15% platform, 85% driver)
- Bank account details (codes, names, numbers)
- Error messages
- Kafka topics
- Settlement configuration

---

## Test Configuration

### **application-test.yml**
```yaml
spring:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL
  
  kafka:
    consumer:
      auto-offset-reset: earliest
      enable-auto-commit: false
    
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

payouts:
  financial:
    commission:
      platform-rate: 0.15
    payout:
      minimum-amount: 5000.00
```

---

## Running Tests

### **Unit Tests Only**
```bash
mvn test -Dtest=*Test
```

### **Integration Tests Only**
```bash
mvn test -Dtest=*IntegrationTest
```

### **All Tests**
```bash
mvn test
```

### **With Coverage**
```bash
mvn clean test jacoco:report
```

---

## Test Dependencies

### **Testing Frameworks**
- **JUnit 5 (Jupiter)** - Test runner
- **Mockito 5.x** - Mocking framework
- **Spring Boot Test** - Integration test utilities
- **AssertJ** - Fluent assertions

### **Integration Testing**
- **Testcontainers 1.19.3** - Docker containers for tests
  - PostgreSQL 14-alpine
  - Kafka 7.5.0
  - Redis 7-alpine
- **Awaitility 4.2.0** - Async assertion library

### **Test Database**
- **H2** - In-memory database for unit tests
- **PostgreSQL (Testcontainers)** - Real database for integration tests

---

## Coverage Targets

| Component | Target | Rationale |
|-----------|--------|-----------|
| **Service Layer** | 80%+ | Core business logic must be well-tested |
| **Entity Layer** | 90%+ | Domain models and business rules critical |
| **Controller Layer** | 70%+ | Integration tests cover most controller logic |
| **Repository Layer** | 60%+ | Custom queries tested, JPA methods trusted |
| **Overall** | 75%+ | Production-ready coverage |

---

## Best Practices

### **Unit Tests**
✅ Use `@ExtendWith(MockitoExtension.class)` for mock injection  
✅ Mock all external dependencies  
✅ Test both happy paths and error scenarios  
✅ Use `ArgumentCaptor` to verify method arguments  
✅ Keep tests isolated and independent

### **Integration Tests**
✅ Use `@Testcontainers` for real infrastructure  
✅ Clean up data between tests (`@BeforeEach`)  
✅ Use `Awaitility` for async assertions  
✅ Test full workflows, not just individual components  
✅ Verify database state changes

### **Test Data**
✅ Use `TestDataBuilder` for consistent test objects  
✅ Use `TestConstants` for shared values  
✅ Make test data meaningful and realistic  
✅ Avoid magic numbers in assertions

---

## CI/CD Integration

### **GitHub Actions Example**
```yaml
- name: Run Tests
  run: mvn clean test

- name: Generate Coverage Report
  run: mvn jacoco:report

- name: Upload Coverage to Codecov
  uses: codecov/codecov-action@v3
  with:
    file: ./target/site/jacoco/jacoco.xml
```

---

## Troubleshooting

### **Testcontainers Issues**
- Ensure Docker is running
- Check Docker resource limits (memory, CPU)
- Verify network connectivity for image pulls

### **Kafka Test Failures**
- Increase timeout values in `Awaitility` assertions
- Check Kafka container logs: `docker logs <container_id>`
- Verify topic configuration in test properties

### **Database Test Failures**
- Check Flyway migrations are applied
- Verify test data cleanup in `@BeforeEach`
- Check for transaction isolation issues

---

## Summary

**Phase 9 Test Suite**: ✅ **COMPLETE**

- **14 Test Files**: 8 unit tests, 3 integration tests, 3 utilities
- **100+ Test Cases**: Comprehensive coverage of all features
- **Production-Ready**: Follows industry best practices
- **CI/CD Ready**: Designed for automated pipelines
- **Maintainable**: Well-structured with reusable utilities

The test suite ensures the Payouts & Financial Management Service is robust, reliable, and ready for production deployment.

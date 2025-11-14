package com.openride.payouts.util;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test constants for consistent test data across test classes.
 */
public final class TestConstants {

    private TestConstants() {
        // Utility class
    }

    // Test UUIDs
    public static final UUID TEST_DRIVER_ID_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID TEST_DRIVER_ID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID TEST_DRIVER_ID_3 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    
    public static final UUID TEST_WALLET_ID_1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UUID TEST_WALLET_ID_2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    
    public static final UUID TEST_TRIP_ID_1 = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    public static final UUID TEST_TRIP_ID_2 = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    
    public static final UUID TEST_PAYOUT_ID_1 = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    public static final UUID TEST_PAYOUT_ID_2 = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    
    public static final UUID TEST_BANK_ACCOUNT_ID_1 = UUID.fromString("99999999-9999-9999-9999-999999999999");
    public static final UUID TEST_BANK_ACCOUNT_ID_2 = UUID.fromString("88888888-8888-8888-8888-888888888888");
    
    public static final UUID TEST_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    // Test amounts
    public static final BigDecimal TRIP_AMOUNT_SMALL = BigDecimal.valueOf(5000.00);
    public static final BigDecimal TRIP_AMOUNT_MEDIUM = BigDecimal.valueOf(10000.00);
    public static final BigDecimal TRIP_AMOUNT_LARGE = BigDecimal.valueOf(20000.00);
    
    public static final BigDecimal PAYOUT_MINIMUM = BigDecimal.valueOf(5000.00);
    public static final BigDecimal PAYOUT_AMOUNT_SMALL = BigDecimal.valueOf(10000.00);
    public static final BigDecimal PAYOUT_AMOUNT_MEDIUM = BigDecimal.valueOf(20000.00);
    public static final BigDecimal PAYOUT_AMOUNT_LARGE = BigDecimal.valueOf(50000.00);
    
    public static final BigDecimal WALLET_BALANCE_LOW = BigDecimal.valueOf(3000.00);
    public static final BigDecimal WALLET_BALANCE_MEDIUM = BigDecimal.valueOf(25000.00);
    public static final BigDecimal WALLET_BALANCE_HIGH = BigDecimal.valueOf(100000.00);

    // Commission rates
    public static final BigDecimal PLATFORM_COMMISSION_RATE = BigDecimal.valueOf(0.15); // 15%
    public static final BigDecimal DRIVER_EARNINGS_RATE = BigDecimal.valueOf(0.85); // 85%

    // Bank account details
    public static final String BANK_CODE_GT = "058";
    public static final String BANK_CODE_ACCESS = "044";
    public static final String BANK_CODE_ZENITH = "057";
    
    public static final String BANK_NAME_GT = "GTBank";
    public static final String BANK_NAME_ACCESS = "Access Bank";
    public static final String BANK_NAME_ZENITH = "Zenith Bank";
    
    public static final String TEST_ACCOUNT_NUMBER_1 = "0123456789";
    public static final String TEST_ACCOUNT_NUMBER_2 = "9876543210";
    public static final String TEST_ACCOUNT_NUMBER_3 = "1234567890";
    
    public static final String TEST_ACCOUNT_NAME_1 = "John Doe";
    public static final String TEST_ACCOUNT_NAME_2 = "Jane Smith";
    public static final String TEST_ACCOUNT_NAME_3 = "Robert Johnson";

    // Provider references
    public static final String PAYSTACK_REFERENCE_PREFIX = "PAY_";
    public static final String PAYSTACK_RECIPIENT_PREFIX = "RCP_";
    public static final String TEST_PROVIDER_REFERENCE_1 = "PAY_TEST_123456";
    public static final String TEST_PROVIDER_REFERENCE_2 = "PAY_TEST_789012";
    public static final String TEST_RECIPIENT_CODE_1 = "RCP_TEST_ABC123";

    // Time constants
    public static final LocalDateTime TEST_TIMESTAMP_NOW = LocalDateTime.now();
    public static final LocalDateTime TEST_TIMESTAMP_YESTERDAY = LocalDateTime.now().minusDays(1);
    public static final LocalDateTime TEST_TIMESTAMP_LAST_WEEK = LocalDateTime.now().minusWeeks(1);
    public static final LocalDateTime TEST_TIMESTAMP_LAST_MONTH = LocalDateTime.now().minusMonths(1);

    // Error messages
    public static final String ERROR_INSUFFICIENT_BALANCE = "Insufficient balance for payout";
    public static final String ERROR_MINIMUM_PAYOUT = "Amount is below minimum payout threshold";
    public static final String ERROR_WALLET_NOT_FOUND = "Wallet not found";
    public static final String ERROR_BANK_ACCOUNT_NOT_VERIFIED = "Bank account not verified";
    public static final String ERROR_PENDING_PAYOUT_EXISTS = "Pending payout request already exists";
    public static final String ERROR_BANK_TRANSFER_FAILED = "Bank transfer failed";
    public static final String ERROR_PROVIDER_ERROR = "Payment provider error";

    // Kafka topics
    public static final String TOPIC_TRIP_COMPLETED = "trip.completed";
    public static final String TOPIC_PAYOUT_REQUESTED = "payout.requested";
    public static final String TOPIC_PAYOUT_APPROVED = "payout.approved";
    public static final String TOPIC_PAYOUT_REJECTED = "payout.rejected";
    public static final String TOPIC_PAYOUT_COMPLETED = "payout.completed";
    public static final String TOPIC_PAYOUT_FAILED = "payout.failed";

    // Settlement
    public static final String SETTLEMENT_SCHEDULE_WEEKLY = "WEEKLY";
    public static final String SETTLEMENT_DAY_MONDAY = "MONDAY";
    public static final String SETTLEMENT_TIME = "02:00";
}

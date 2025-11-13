package com.openride.payments;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Basic application context test.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.redis.host=localhost",
    "spring.redis.port=6379",
    "korapay.secret-key=test-secret",
    "korapay.public-key=test-public",
    "korapay.encryption-key=test-encryption",
    "korapay.webhook-secret=test-webhook-secret",
    "korapay.base-url=https://api.korapay.com",
    "jwt.secret=test-jwt-secret-key-for-testing-minimum-256-bits",
    "jwt.expiration-ms=86400000",
    "booking-service.url=http://localhost:8082",
    "booking-service.retry-attempts=3",
    "booking-service.retry-delay-seconds=2"
})
class PaymentsServiceApplicationTests {

    @Test
    void contextLoads() {
        // Application context loads successfully
    }
}

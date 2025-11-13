package com.openride.commons.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for REST clients used for inter-service communication.
 * Includes timeouts, connection pooling, and circuit breaker patterns.
 */
@Configuration
public class RestClientConfig {
    
    /**
     * RestTemplate bean for making HTTP requests to other services.
     * Configured with timeouts and error handling.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5 seconds
        factory.setReadTimeout(10000);   // 10 seconds
        
        return builder
                .requestFactory(() -> factory)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
    
    /**
     * Circuit breaker for ticketing service calls.
     * Prevents cascading failures if ticketing service is down.
     */
    @Bean
    public CircuitBreaker ticketingServiceCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // Open circuit if 50% of calls fail
                .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s before retry
                .slidingWindowSize(10) // Consider last 10 calls
                .minimumNumberOfCalls(5) // Need 5 calls before calculating failure rate
                .permittedNumberOfCallsInHalfOpenState(3) // Allow 3 test calls in half-open
                .build();
        
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        return registry.circuitBreaker("ticketingService");
    }
}

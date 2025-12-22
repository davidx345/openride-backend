package com.openride.booking.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Rate limiting configuration using Bucket4j + Redis
 * 
 * Limits:
 * - Per user: 100 requests per minute
 * - Burst capacity: 20 requests
 */
@Configuration
public class RateLimitingConfig {

    @Value("${rate-limiting.per-user.requests-per-minute:100}")
    private int requestsPerMinute;

    @Value("${rate-limiting.per-user.burst-capacity:20}")
    private int burstCapacity;

    @Value("${spring.redis.url:redis://localhost:6379/3}")
    private String redisUrl;

    @Bean
    public ProxyManager<String> proxyManager() {
        // Parse REDIS_URL directly instead of separate host/port
        RedisClient redisClient = RedisClient.create(redisUrl);
        
        StatefulRedisConnection<String, byte[]> connection = redisClient.connect(
            io.lettuce.core.codec.RedisCodec.of(
                io.lettuce.core.codec.StringCodec.UTF8,
                io.lettuce.core.codec.ByteArrayCodec.INSTANCE
            )
        );

        return LettuceBasedProxyManager.builderFor(connection)
            .withExpirationStrategy(
                io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager.ExpirationAfterWriteStrategy
                    .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1))
            )
            .build();
    }

    @Bean
    public Supplier<BucketConfiguration> bucketConfiguration() {
        return () -> {
            Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillIntervally(requestsPerMinute, Duration.ofMinutes(1))
                .initialTokens(burstCapacity)
                .build();

            return BucketConfiguration.builder()
                .addLimit(limit)
                .build();
        };
    }
}

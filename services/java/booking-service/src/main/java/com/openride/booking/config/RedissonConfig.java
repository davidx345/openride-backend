package com.openride.booking.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson configuration for distributed locks and Redis operations
 * 
 * Provides:
 * - Distributed locks for seat hold operations
 * - High-performance async Redis client
 * - Automatic connection pooling
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.url:redis://localhost:6379/3}")
    private String redisUrl;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        
        // Use REDIS_URL directly - Redisson will parse it
        // Format: redis://[password@]host:port[/database]
        config.useSingleServer()
            .setAddress(redisUrl)
            .setConnectionPoolSize(50)
            .setConnectionMinimumIdleSize(10)
            .setRetryAttempts(3)
            .setRetryInterval(1500)
            .setTimeout(3000)
            .setConnectTimeout(5000);

        return Redisson.create(config);
    }
}

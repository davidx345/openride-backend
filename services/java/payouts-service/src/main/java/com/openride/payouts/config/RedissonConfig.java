package com.openride.payouts.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Redisson (Redis distributed lock client).
 */
@Configuration
public class RedissonConfig {

    @Value("${redis.host:localhost}")
    private String redisHost;

    @Value("${redis.port:6379}")
    private int redisPort;

    @Value("${redis.password:}")
    private String redisPassword;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        
        String address = String.format("redis://%s:%d", redisHost, redisPort);
        
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.useSingleServer()
                    .setAddress(address)
                    .setPassword(redisPassword)
                    .setConnectionPoolSize(50)
                    .setConnectionMinimumIdleSize(10)
                    .setConnectTimeout(10000)
                    .setTimeout(3000)
                    .setRetryAttempts(3)
                    .setRetryInterval(1500);
        } else {
            config.useSingleServer()
                    .setAddress(address)
                    .setConnectionPoolSize(50)
                    .setConnectionMinimumIdleSize(10)
                    .setConnectTimeout(10000)
                    .setTimeout(3000)
                    .setRetryAttempts(3)
                    .setRetryInterval(1500);
        }
        
        return Redisson.create(config);
    }
}

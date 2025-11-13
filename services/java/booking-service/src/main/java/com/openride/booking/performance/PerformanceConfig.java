package com.openride.booking.performance;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * Performance-tuned database configuration
 * 
 * Optimizations:
 * - Connection pool sizing based on load
 * - Statement caching
 * - Connection validation
 * - Leak detection
 */
@Configuration
@Profile("production")
public class PerformanceConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        
        // Basic configuration
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");

        // Pool sizing
        config.setMaximumPoolSize(50);
        config.setMinimumIdle(10);
        
        // Connection timeout
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(600000);
        
        // Performance
        config.setAutoCommit(false);
        config.setCachePrepStmts(true);
        config.setPrepStmtCacheSize(250);
        config.setPrepStmtCacheSqlLimit(2048);
        config.setUseServerPrepStmts(true);
        
        // Leak detection
        config.setLeakDetectionThreshold(60000);
        
        // Pool name
        config.setPoolName("BookingServiceHikariPool-Prod");

        return new HikariDataSource(config);
    }
}

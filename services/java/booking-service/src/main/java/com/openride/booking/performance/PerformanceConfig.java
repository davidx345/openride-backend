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
 * 
 * Note: For Supabase/PgBouncer, credentials are embedded in the JDBC URL,
 * so username/password properties are optional with defaults.
 */
@Configuration
@Profile("production")
public class PerformanceConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username:}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        
        // Basic configuration - JDBC URL contains credentials for Supabase
        config.setJdbcUrl(jdbcUrl);
        // Only set username/password if provided (Supabase embeds them in URL)
        if (username != null && !username.isEmpty()) {
            config.setUsername(username);
        }
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }
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
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        
        // Leak detection
        config.setLeakDetectionThreshold(60000);
        
        // Pool name
        config.setPoolName("BookingServiceHikariPool-Prod");

        return new HikariDataSource(config);
    }
}

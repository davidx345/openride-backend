package com.openride.ticketing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Blockchain configuration properties.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ticketing.blockchain")
public class BlockchainProperties {
    
    /**
     * Blockchain type (POLYGON, HYPERLEDGER, ETHEREUM, OTHER).
     */
    private String type = "POLYGON";
    
    /**
     * RPC endpoint URL.
     * Examples:
     * - Polygon Mumbai: https://rpc-mumbai.maticvigil.com/
     * - Polygon Mainnet: https://polygon-rpc.com/
     */
    private String rpcUrl;
    
    /**
     * Private key for signing transactions (hex format without 0x prefix).
     */
    private String privateKey;
    
    /**
     * Smart contract address for storing Merkle roots.
     */
    private String contractAddress;
    
    /**
     * Chain ID (80001 for Mumbai, 137 for Polygon mainnet).
     */
    private Integer chainId = 80001;
    
    /**
     * Required confirmations before considering transaction final.
     */
    private Integer requiredConfirmations = 12;
    
    /**
     * Gas price multiplier (e.g., 1.1 for 10% higher than current).
     */
    private Double gasPriceMultiplier = 1.1;
    
    /**
     * Maximum retry attempts for failed transactions.
     */
    private Integer maxRetries = 3;
    
    /**
     * Retry delay in milliseconds.
     */
    private Long retryDelayMs = 30000L;
    
    /**
     * Transaction timeout in seconds.
     */
    private Integer transactionTimeoutSeconds = 300;
}

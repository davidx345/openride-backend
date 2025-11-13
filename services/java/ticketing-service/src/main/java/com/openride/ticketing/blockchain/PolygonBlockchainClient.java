package com.openride.ticketing.blockchain;

import com.openride.ticketing.config.BlockchainProperties;
import com.openride.ticketing.model.BlockchainAnchor;
import com.openride.ticketing.model.BlockchainAnchorStatus;
import com.openride.ticketing.model.BlockchainType;
import com.openride.ticketing.model.MerkleBatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;

import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * Polygon blockchain client implementation using Web3j.
 * Handles Merkle root anchoring on Polygon (Mumbai testnet or mainnet).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ticketing.blockchain.type", havingValue = "polygon")
public class PolygonBlockchainClient implements BlockchainClient {
    
    private final Web3j web3j;
    private final Credentials credentials;
    private final BlockchainProperties properties;
    private final String contractAddress;
    
    public PolygonBlockchainClient(BlockchainProperties properties) {
        this.properties = properties;
        this.web3j = Web3j.build(new HttpService(properties.getRpcUrl()));
        this.credentials = Credentials.create(properties.getPrivateKey());
        this.contractAddress = properties.getContractAddress();
        
        log.info("Initialized Polygon blockchain client with RPC: {}", properties.getRpcUrl());
        log.info("Using wallet address: {}", credentials.getAddress());
    }
    
    @Override
    public CompletableFuture<BlockchainAnchor> anchorMerkleRoot(MerkleBatch batch) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Anchoring Merkle root to Polygon: {}", batch.getMerkleRoot());
                
                // Get current gas price
                EthGasPrice gasPrice = web3j.ethGasPrice().send();
                BigInteger currentGasPrice = gasPrice.getGasPrice();
                
                // Add 10% buffer for faster confirmation
                BigInteger bufferedGasPrice = currentGasPrice.multiply(BigInteger.valueOf(110))
                        .divide(BigInteger.valueOf(100));
                
                // Get nonce
                EthGetTransactionCount transactionCount = web3j.ethGetTransactionCount(
                        credentials.getAddress(),
                        DefaultBlockParameterName.PENDING
                ).send();
                BigInteger nonce = transactionCount.getTransactionCount();
                
                // Create function call (storing merkle root as string in contract)
                Function function = new Function(
                        "storeMerkleRoot",
                        Collections.singletonList(new Utf8String(batch.getMerkleRoot())),
                        Collections.emptyList()
                );
                
                String encodedFunction = FunctionEncoder.encode(function);
                
                // Estimate gas limit
                Transaction transaction = Transaction.createFunctionCallTransaction(
                        credentials.getAddress(),
                        nonce,
                        bufferedGasPrice,
                        DefaultGasProvider.GAS_LIMIT,
                        contractAddress,
                        encodedFunction
                );
                
                EthEstimateGas estimateGas = web3j.ethEstimateGas(transaction).send();
                BigInteger gasLimit = estimateGas.getAmountUsed();
                
                // Send transaction
                org.web3j.protocol.core.methods.request.Transaction signedTransaction =
                        org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
                                credentials.getAddress(),
                                nonce,
                                bufferedGasPrice,
                                gasLimit,
                                contractAddress,
                                encodedFunction
                        );
                
                EthSendTransaction response = web3j.ethSendTransaction(signedTransaction).send();
                
                if (response.hasError()) {
                    String errorMessage = response.getError().getMessage();
                    log.error("Failed to anchor Merkle root: {}", errorMessage);
                    
                    // Create failed anchor record
                    return createFailedAnchor(batch, errorMessage);
                }
                
                String transactionHash = response.getTransactionHash();
                log.info("Merkle root anchored successfully. Transaction hash: {}", transactionHash);
                
                // Create blockchain anchor record
                BlockchainAnchor anchor = new BlockchainAnchor();
                anchor.setMerkleBatch(batch);
                anchor.setTransactionHash(transactionHash);
                anchor.setBlockchainType(BlockchainType.POLYGON);
                anchor.setStatus(BlockchainAnchorStatus.SUBMITTED);
                anchor.setGasPrice(bufferedGasPrice.longValue());
                anchor.setGasUsed(gasLimit.longValue());
                anchor.setRetryCount(0);
                
                return anchor;
                
            } catch (IOException e) {
                log.error("Error anchoring Merkle root to Polygon", e);
                return createFailedAnchor(batch, e.getMessage());
            }
        });
    }
    
    @Override
    public CompletableFuture<Integer> getConfirmationCount(String transactionHash) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get transaction receipt
                EthGetTransactionReceipt receipt = web3j.ethGetTransactionReceipt(transactionHash).send();
                
                if (receipt.getTransactionReceipt().isEmpty()) {
                    log.warn("Transaction not found: {}", transactionHash);
                    return 0;
                }
                
                TransactionReceipt txReceipt = receipt.getTransactionReceipt().get();
                BigInteger txBlockNumber = txReceipt.getBlockNumber();
                
                // Get latest block number
                EthBlockNumber latestBlock = web3j.ethBlockNumber().send();
                BigInteger currentBlockNumber = latestBlock.getBlockNumber();
                
                // Calculate confirmations
                int confirmations = currentBlockNumber.subtract(txBlockNumber).intValue();
                
                log.debug("Transaction {} has {} confirmations", transactionHash, confirmations);
                return confirmations;
                
            } catch (IOException e) {
                log.error("Error getting confirmation count for transaction: {}", transactionHash, e);
                return 0;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> verifyMerkleRoot(String merkleRoot) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create function call to check if merkle root exists
                Function function = new Function(
                        "merkleRootExists",
                        Collections.singletonList(new Utf8String(merkleRoot)),
                        Collections.emptyList()
                );
                
                String encodedFunction = FunctionEncoder.encode(function);
                
                org.web3j.protocol.core.methods.request.Transaction transaction =
                        org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                                credentials.getAddress(),
                                contractAddress,
                                encodedFunction
                        );
                
                EthCall response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();
                
                if (response.hasError()) {
                    log.error("Error verifying Merkle root: {}", response.getError().getMessage());
                    return false;
                }
                
                // Parse boolean result from response
                String result = response.getValue();
                return !result.equals("0x0000000000000000000000000000000000000000000000000000000000000000");
                
            } catch (IOException e) {
                log.error("Error verifying Merkle root on blockchain", e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Long> estimateGasPrice() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                EthGasPrice gasPrice = web3j.ethGasPrice().send();
                return gasPrice.getGasPrice().longValue();
            } catch (IOException e) {
                log.error("Error estimating gas price", e);
                return DefaultGasProvider.GAS_PRICE.longValue();
            }
        });
    }
    
    @Override
    public boolean isHealthy() {
        try {
            // Check if we can connect to the RPC endpoint
            EthBlockNumber blockNumber = web3j.ethBlockNumber().send();
            return blockNumber.getBlockNumber() != null;
        } catch (IOException e) {
            log.error("Blockchain health check failed", e);
            return false;
        }
    }
    
    @Override
    public String getBlockchainType() {
        return BlockchainType.POLYGON.name();
    }
    
    /**
     * Create a failed blockchain anchor record.
     */
    private BlockchainAnchor createFailedAnchor(MerkleBatch batch, String errorMessage) {
        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setMerkleBatch(batch);
        anchor.setBlockchainType(BlockchainType.POLYGON);
        anchor.setStatus(BlockchainAnchorStatus.FAILED);
        anchor.setErrorMessage(errorMessage);
        anchor.setRetryCount(0);
        return anchor;
    }
}

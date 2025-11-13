package com.openride.booking.service;

import com.openride.booking.exception.LockAcquisitionException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed lock service using Redisson
 * 
 * Provides:
 * - Route-level locking for seat operations
 * - Automatic lock release on method completion
 * - Configurable wait and lease times
 * - Deadlock prevention
 * 
 * Lock Key Patterns:
 * - Route booking: "lock:route:{routeId}:{travelDate}"
 * - Booking update: "lock:booking:{bookingId}"
 */
@Slf4j
@Service
public class DistributedLockService {

    private final RedissonClient redissonClient;
    private final long waitTimeSeconds;
    private final long leaseTimeSeconds;

    public DistributedLockService(
        RedissonClient redissonClient,
        @Value("${booking.lock.wait-time-seconds:5}") long waitTimeSeconds,
        @Value("${booking.lock.lease-time-seconds:10}") long leaseTimeSeconds
    ) {
        this.redissonClient = redissonClient;
        this.waitTimeSeconds = waitTimeSeconds;
        this.leaseTimeSeconds = leaseTimeSeconds;
    }

    /**
     * Execute action with distributed lock
     * 
     * @param lockKey Redis lock key
     * @param action Action to execute
     * @param <T> Return type
     * @return Action result
     * @throws LockAcquisitionException if lock cannot be acquired
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> action) {
        return executeWithLock(lockKey, waitTimeSeconds, leaseTimeSeconds, TimeUnit.SECONDS, action);
    }

    /**
     * Execute action with distributed lock and custom timeouts
     * 
     * @param lockKey Redis lock key
     * @param waitTime Maximum wait time for lock
     * @param leaseTime Automatic lock release time
     * @param timeUnit Time unit
     * @param action Action to execute
     * @param <T> Return type
     * @return Action result
     * @throws LockAcquisitionException if lock cannot be acquired
     */
    public <T> T executeWithLock(
        String lockKey,
        long waitTime,
        long leaseTime,
        TimeUnit timeUnit,
        Supplier<T> action
    ) {
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, timeUnit);
            
            if (!acquired) {
                log.warn("Failed to acquire lock: {} after {} {}", 
                    lockKey, waitTime, timeUnit);
                throw new LockAcquisitionException(
                    "Failed to acquire lock: " + lockKey + ". Please try again."
                );
            }
            
            log.debug("Lock acquired: {}", lockKey);
            
            // Execute action with lock held
            return action.get();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted: {}", lockKey, e);
            throw new LockAcquisitionException(
                "Lock acquisition interrupted", e
            );
        } finally {
            // Release lock if held by current thread
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released: {}", lockKey);
            }
        }
    }

    /**
     * Execute void action with distributed lock
     * 
     * @param lockKey Redis lock key
     * @param action Action to execute
     * @throws LockAcquisitionException if lock cannot be acquired
     */
    public void executeWithLock(String lockKey, Runnable action) {
        executeWithLock(lockKey, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Build lock key for route booking operations
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @return Lock key
     */
    public String buildRouteLockKey(UUID routeId, LocalDate travelDate) {
        return String.format("lock:route:%s:%s", routeId, travelDate);
    }

    /**
     * Build lock key for booking update operations
     * 
     * @param bookingId Booking ID
     * @return Lock key
     */
    public String buildBookingLockKey(UUID bookingId) {
        return String.format("lock:booking:%s", bookingId);
    }

    /**
     * Check if lock is currently held
     * 
     * @param lockKey Lock key
     * @return true if lock is held
     */
    public boolean isLocked(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        return lock.isLocked();
    }

    /**
     * Force unlock (use with caution)
     * 
     * @param lockKey Lock key
     */
    public void forceUnlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isLocked()) {
            lock.forceUnlock();
            log.warn("Force unlocked: {}", lockKey);
        }
    }
}

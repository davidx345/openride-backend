package com.openride.booking.filter;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Rate limiting filter using Bucket4j
 * 
 * Limits API requests per user to prevent abuse
 */
@Slf4j
@Component
@Order(2) // After JWT filter
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ProxyManager<String> proxyManager;
    private final Supplier<BucketConfiguration> bucketConfiguration;

    public RateLimitingFilter(
        ProxyManager<String> proxyManager,
        Supplier<BucketConfiguration> bucketConfiguration
    ) {
        this.proxyManager = proxyManager;
        this.bucketConfiguration = bucketConfiguration;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        // Skip rate limiting for health checks and actuator
        String path = request.getRequestURI();
        if (path.startsWith("/api/actuator") || path.equals("/api/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get user ID from security context
        String userId = getUserId();
        if (userId == null) {
            // Anonymous requests - use IP address
            userId = request.getRemoteAddr();
        }

        // Get or create bucket for user
        String bucketKey = "rate-limit:user:" + userId;
        Bucket bucket = proxyManager.builder()
            .build(bucketKey, bucketConfiguration);

        // Try to consume 1 token
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // Request allowed
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
            
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                "{\"error\":\"Too many requests\",\"message\":\"Rate limit exceeded. Try again in %d seconds.\"}",
                waitForRefill
            ));

            log.warn("Rate limit exceeded for user: {} from IP: {}", userId, request.getRemoteAddr());
        }
    }

    private String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && 
            !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName();
        }
        return null;
    }
}

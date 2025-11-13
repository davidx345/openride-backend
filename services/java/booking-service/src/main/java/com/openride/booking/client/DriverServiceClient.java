package com.openride.booking.client;

import com.openride.booking.dto.RouteDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Client for communicating with Driver Service
 */
@Slf4j
@Component
public class DriverServiceClient {

    private final RestClient restClient;
    private final String baseUrl;

    public DriverServiceClient(
        RestClient.Builder restClientBuilder,
        @Value("${services.driver-service.base-url}") String baseUrl
    ) {
        this.baseUrl = baseUrl;
        this.restClient = restClientBuilder
            .baseUrl(baseUrl)
            .build();
    }

    /**
     * Get route by ID
     * 
     * @param routeId Route ID
     * @return Route DTO
     * @throws RestClientException if request fails
     */
    public RouteDTO getRouteById(UUID routeId) {
        try {
            log.debug("Fetching route {} from driver service", routeId);
            
            RouteDTO route = restClient.get()
                .uri("/v1/routes/{id}", routeId)
                .retrieve()
                .body(RouteDTO.class);
            
            if (route == null) {
                throw new RuntimeException("Route not found: " + routeId);
            }
            
            log.debug("Successfully fetched route: {}", route.getName());
            return route;
            
        } catch (RestClientException e) {
            log.error("Failed to fetch route {} from driver service", routeId, e);
            throw new RuntimeException("Failed to fetch route details", e);
        }
    }

    /**
     * Validate route exists and is active
     * 
     * @param routeId Route ID
     * @return true if route is valid and active
     */
    public boolean isRouteActive(UUID routeId) {
        try {
            RouteDTO route = getRouteById(routeId);
            return "ACTIVE".equalsIgnoreCase(route.getStatus());
        } catch (Exception e) {
            log.warn("Route validation failed for {}", routeId, e);
            return false;
        }
    }
}

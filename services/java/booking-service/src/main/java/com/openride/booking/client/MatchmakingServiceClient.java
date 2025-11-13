package com.openride.booking.client;

import com.openride.booking.dto.MatchRequest;
import com.openride.booking.dto.MatchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Client for Matchmaking Service integration
 * 
 * Provides:
 * - Route matching and validation
 * - Dynamic pricing calculation
 * - Seat availability verification
 */
@Slf4j
@Component
public class MatchmakingServiceClient {

    private final RestClient restClient;

    public MatchmakingServiceClient(
        @Value("${services.matchmaking-service.base-url}") String baseUrl,
        @Value("${services.matchmaking-service.timeout-ms:5000}") int timeoutMs
    ) {
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .build();
    }

    /**
     * Request route match and pricing
     * 
     * @param request Match request with origin, destination, date, seats
     * @return Match response with pricing and route details
     */
    public MatchResponse requestMatch(MatchRequest request) {
        try {
            log.debug("Requesting match for route {} on {}", 
                request.getRouteId(), request.getTravelDate());

            MatchResponse response = restClient.post()
                .uri("/v1/match")
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    log.error("Match request failed: {} - {}", 
                        res.getStatusCode(), res.getStatusText());
                    throw new MatchServiceException("Match request failed: " + res.getStatusText());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    log.error("Match service error: {} - {}", 
                        res.getStatusCode(), res.getStatusText());
                    throw new MatchServiceException("Match service unavailable");
                })
                .body(MatchResponse.class);

            log.info("Match successful: routeId={}, price={}, available={}", 
                response.getRouteId(), 
                response.getTotalPrice(), 
                response.getAvailableSeats());

            return response;

        } catch (Exception e) {
            log.error("Failed to request match: {}", e.getMessage(), e);
            throw new MatchServiceException("Failed to contact matchmaking service", e);
        }
    }

    /**
     * Validate route availability
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @param seatsRequired Number of seats required
     * @return True if route has sufficient seats available
     */
    public boolean validateRouteAvailability(UUID routeId, LocalDate travelDate, int seatsRequired) {
        try {
            MatchRequest request = MatchRequest.builder()
                .routeId(routeId)
                .travelDate(travelDate)
                .seatsRequired(seatsRequired)
                .build();

            MatchResponse response = requestMatch(request);
            
            return response.getAvailableSeats() >= seatsRequired;

        } catch (Exception e) {
            log.warn("Route availability check failed for route {}: {}", 
                routeId, e.getMessage());
            return false;
        }
    }

    /**
     * Custom exception for match service errors
     */
    public static class MatchServiceException extends RuntimeException {
        public MatchServiceException(String message) {
            super(message);
        }

        public MatchServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

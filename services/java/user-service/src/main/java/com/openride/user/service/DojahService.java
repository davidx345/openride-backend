package com.openride.user.service;

import com.openride.user.config.DojahProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for Dojah KYC verification API integration.
 * Handles NIN verification, driver's license verification, and selfie matching.
 * 
 * In test mode, all verifications are auto-approved.
 * 
 * @see <a href="https://docs.dojah.io/">Dojah API Documentation</a>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DojahService {

    private final DojahProperties dojahProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Result of NIN verification.
     */
    public record NinVerificationResult(
        boolean verified,
        String firstName,
        String lastName,
        String middleName,
        String dateOfBirth,
        String gender,
        String photoUrl,
        String referenceId,
        String errorMessage
    ) {}

    /**
     * Result of driver's license verification.
     */
    public record LicenseVerificationResult(
        boolean verified,
        String firstName,
        String lastName,
        LocalDate expiryDate,
        String licenseClass,
        String referenceId,
        String errorMessage
    ) {}

    /**
     * Result of selfie-to-photo matching.
     */
    public record SelfieMatchResult(
        boolean matched,
        BigDecimal matchScore,
        String referenceId,
        String errorMessage
    ) {}

    /**
     * Verifies Nigerian National Identification Number (NIN).
     *
     * @param nin 11-digit NIN
     * @return verification result
     */
    public NinVerificationResult verifyNin(String nin) {
        log.info("Verifying NIN: {}****{}", nin.substring(0, 3), nin.substring(nin.length() - 2));

        // Test mode - auto approve
        if (dojahProperties.isTestMode()) {
            log.info("TEST MODE: Auto-approving NIN verification");
            return new NinVerificationResult(
                true,
                "Test",
                "User",
                "Mode",
                "1990-01-01",
                "M",
                null,
                "test-ref-" + UUID.randomUUID().toString().substring(0, 8),
                null
            );
        }

        try {
            String url = dojahProperties.getBaseUrl() + "/api/v1/kyc/nin";

            HttpHeaders headers = createHeaders();
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("nin", nin);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, request, Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Map<String, Object> entity = (Map<String, Object>) body.get("entity");

                if (entity != null) {
                    return new NinVerificationResult(
                        true,
                        (String) entity.get("first_name"),
                        (String) entity.get("last_name"),
                        (String) entity.get("middle_name"),
                        (String) entity.get("date_of_birth"),
                        (String) entity.get("gender"),
                        (String) entity.get("photo"),
                        (String) body.get("reference_id"),
                        null
                    );
                }
            }

            return new NinVerificationResult(
                false, null, null, null, null, null, null, null,
                "NIN verification failed"
            );

        } catch (Exception e) {
            log.error("NIN verification failed: {}", e.getMessage(), e);
            return new NinVerificationResult(
                false, null, null, null, null, null, null, null,
                "NIN verification error: " + e.getMessage()
            );
        }
    }

    /**
     * Verifies Nigerian driver's license number via FRSC.
     *
     * @param licenseNumber driver's license number
     * @return verification result
     */
    public LicenseVerificationResult verifyDriversLicense(String licenseNumber) {
        log.info("Verifying driver's license: {}****", licenseNumber.substring(0, 3));

        // Test mode - auto approve
        if (dojahProperties.isTestMode()) {
            log.info("TEST MODE: Auto-approving driver's license verification");
            return new LicenseVerificationResult(
                true,
                "Test",
                "Captain",
                LocalDate.now().plusYears(3),
                "B",
                "test-ref-" + UUID.randomUUID().toString().substring(0, 8),
                null
            );
        }

        try {
            String url = dojahProperties.getBaseUrl() + "/api/v1/kyc/dl";

            HttpHeaders headers = createHeaders();
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("license_number", licenseNumber);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, request, Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Map<String, Object> entity = (Map<String, Object>) body.get("entity");

                if (entity != null) {
                    String expiryStr = (String) entity.get("expiry_date");
                    LocalDate expiryDate = expiryStr != null ? LocalDate.parse(expiryStr) : null;

                    return new LicenseVerificationResult(
                        true,
                        (String) entity.get("first_name"),
                        (String) entity.get("last_name"),
                        expiryDate,
                        (String) entity.get("license_class"),
                        (String) body.get("reference_id"),
                        null
                    );
                }
            }

            return new LicenseVerificationResult(
                false, null, null, null, null, null,
                "Driver's license verification failed"
            );

        } catch (Exception e) {
            log.error("Driver's license verification failed: {}", e.getMessage(), e);
            return new LicenseVerificationResult(
                false, null, null, null, null, null,
                "Driver's license verification error: " + e.getMessage()
            );
        }
    }

    /**
     * Performs selfie-to-photo matching using Dojah's biometric API.
     *
     * @param selfieUrl URL of user's selfie image
     * @param referencePhotoUrl URL of reference photo (NIN photo or ID card)
     * @return match result with score
     */
    public SelfieMatchResult matchSelfie(String selfieUrl, String referencePhotoUrl) {
        log.info("Performing selfie matching");

        // Test mode - auto approve with high score
        if (dojahProperties.isTestMode()) {
            log.info("TEST MODE: Auto-approving selfie match");
            return new SelfieMatchResult(
                true,
                BigDecimal.valueOf(0.95),
                "test-ref-" + UUID.randomUUID().toString().substring(0, 8),
                null
            );
        }

        try {
            String url = dojahProperties.getBaseUrl() + "/api/v1/ml/face_match";

            HttpHeaders headers = createHeaders();
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("image_one", selfieUrl);
            requestBody.put("image_two", referencePhotoUrl);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Map<String, Object> entity = (Map<String, Object>) body.get("entity");

                if (entity != null) {
                    Number matchNumber = (Number) entity.get("match");
                    BigDecimal matchScore = matchNumber != null 
                        ? BigDecimal.valueOf(matchNumber.doubleValue())
                        : BigDecimal.ZERO;

                    boolean matched = matchScore.compareTo(
                        BigDecimal.valueOf(dojahProperties.getMinSelfieMatchScore())
                    ) >= 0;

                    return new SelfieMatchResult(
                        matched,
                        matchScore,
                        (String) body.get("reference_id"),
                        matched ? null : "Face match score below threshold"
                    );
                }
            }

            return new SelfieMatchResult(
                false, BigDecimal.ZERO, null,
                "Selfie matching failed"
            );

        } catch (Exception e) {
            log.error("Selfie matching failed: {}", e.getMessage(), e);
            return new SelfieMatchResult(
                false, BigDecimal.ZERO, null,
                "Selfie matching error: " + e.getMessage()
            );
        }
    }

    /**
     * Creates HTTP headers with Dojah authentication.
     *
     * @return configured headers
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("AppId", dojahProperties.getAppId());
        headers.set("Authorization", dojahProperties.getSecretKey());
        return headers;
    }
}

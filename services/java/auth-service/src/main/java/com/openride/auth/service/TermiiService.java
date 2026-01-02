package com.openride.auth.service;

import com.openride.auth.config.TermiiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending SMS notifications using Termii.
 * Termii is a popular SMS gateway in Nigeria.
 * 
 * In test mode, SMS is not actually sent and OTP "123456" is always valid.
 * 
 * @see <a href="https://developers.termii.com/">Termii API Documentation</a>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TermiiService {

    private final TermiiProperties termiiProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Sends an OTP via SMS to the specified phone number.
     * In test mode, no actual SMS is sent.
     *
     * @param phoneNumber recipient phone number (with country code, e.g., +234...)
     * @param otpCode OTP code to send
     * @return true if sent successfully (or test mode), false otherwise
     */
    public boolean sendOtp(String phoneNumber, String otpCode) {
        // Test mode - don't actually send SMS
        if (termiiProperties.isTestMode()) {
            log.info("TEST MODE: OTP for {} would be: {} (use 123456 to bypass)", 
                phoneNumber, otpCode);
            return true;
        }

        // Check if API key is configured
        if (termiiProperties.getApiKey() == null || termiiProperties.getApiKey().isEmpty()) {
            log.warn("Termii API key not configured. OTP would be sent to {}: {}", 
                phoneNumber, otpCode);
            return true; // Return true for development without API key
        }

        try {
            String url = termiiProperties.getBaseUrl() + "/sms/send";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Format phone number - remove + if present for Termii
            String formattedPhone = phoneNumber.startsWith("+") 
                ? phoneNumber.substring(1) 
                : phoneNumber;

            String messageBody = String.format(
                "Your OpenRide verification code is: %s. Valid for 5 minutes. Do not share this code with anyone.",
                otpCode
            );

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("api_key", termiiProperties.getApiKey());
            requestBody.put("to", formattedPhone);
            requestBody.put("from", termiiProperties.getSenderId());
            requestBody.put("sms", messageBody);
            requestBody.put("type", termiiProperties.getMessageType());
            requestBody.put("channel", termiiProperties.getChannel());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            if (response != null && "ok".equalsIgnoreCase((String) response.get("code"))) {
                log.info("SMS sent successfully to {} via Termii. Message ID: {}", 
                    phoneNumber, response.get("message_id"));
                return true;
            } else {
                log.error("Failed to send SMS via Termii. Response: {}", response);
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to send SMS to {} via Termii: {}", phoneNumber, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Checks if the provided OTP code is valid for test mode bypass.
     *
     * @param code the OTP code to check
     * @return true if test mode is enabled and code matches test code
     */
    public boolean isTestModeBypass(String code) {
        return termiiProperties.isTestMode() 
            && termiiProperties.getTestOtpCode().equals(code);
    }

    /**
     * Checks if test mode is enabled.
     *
     * @return true if test mode is enabled
     */
    public boolean isTestMode() {
        return termiiProperties.isTestMode();
    }
}

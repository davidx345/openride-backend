package com.openride.payouts.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Paystack payment provider implementation.
 * API Documentation: https://paystack.com/docs/api/
 */
@Slf4j
@Component
public class PaystackProvider implements PaymentProvider {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;

    public PaystackProvider(
            RestTemplate restTemplate,
            @Value("${app.payment.paystack.api-key}") String apiKey,
            @Value("${app.payment.paystack.base-url:https://api.paystack.co}") String baseUrl
    ) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    @Override
    public String verifyBankAccount(String accountNumber, String bankCode) {
        log.info("Verifying bank account with Paystack: account={}, bank={}", 
                maskAccountNumber(accountNumber), bankCode);

        try {
            String url = String.format("%s/bank/resolve?account_number=%s&bank_code=%s",
                    baseUrl, accountNumber, bankCode);

            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                String accountName = (String) data.get("account_name");
                
                log.info("Bank account verified: accountName={}", accountName);
                return accountName;
            } else {
                throw new RuntimeException("Bank account verification failed");
            }
        } catch (Exception e) {
            log.error("Failed to verify bank account with Paystack", e);
            throw new RuntimeException("Bank account verification failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String initiateBankTransfer(String accountNumber, String bankCode, BigDecimal amount, String narration) {
        log.info("Initiating bank transfer with Paystack: account={}, amount={}, narration={}",
                maskAccountNumber(accountNumber), amount, narration);

        try {
            String url = baseUrl + "/transfer";

            // Create transfer recipient first
            String recipientCode = createTransferRecipient(accountNumber, bankCode);

            // Convert amount to kobo (Paystack uses kobo, not naira)
            long amountInKobo = amount.multiply(new BigDecimal("100")).longValue();

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("source", "balance");
            requestBody.put("reason", narration);
            requestBody.put("amount", amountInKobo);
            requestBody.put("recipient", recipientCode);

            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                String transferCode = (String) data.get("transfer_code");
                String reference = (String) data.get("reference");
                
                log.info("Bank transfer initiated: transferCode={}, reference={}", transferCode, reference);
                return reference;
            } else {
                throw new RuntimeException("Bank transfer initiation failed");
            }
        } catch (Exception e) {
            log.error("Failed to initiate bank transfer with Paystack", e);
            throw new RuntimeException("Bank transfer failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "PAYSTACK";
    }

    private String createTransferRecipient(String accountNumber, String bankCode) {
        log.debug("Creating transfer recipient: account={}, bank={}", 
                maskAccountNumber(accountNumber), bankCode);

        try {
            String url = baseUrl + "/transferrecipient";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("type", "nuban");
            requestBody.put("name", "OpenRide Driver");
            requestBody.put("account_number", accountNumber);
            requestBody.put("bank_code", bankCode);
            requestBody.put("currency", "NGN");

            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                String recipientCode = (String) data.get("recipient_code");
                
                log.debug("Transfer recipient created: recipientCode={}", recipientCode);
                return recipientCode;
            } else {
                throw new RuntimeException("Failed to create transfer recipient");
            }
        } catch (Exception e) {
            log.error("Failed to create transfer recipient", e);
            throw new RuntimeException("Failed to create transfer recipient: " + e.getMessage(), e);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "******" + accountNumber.substring(accountNumber.length() - 4);
    }
}

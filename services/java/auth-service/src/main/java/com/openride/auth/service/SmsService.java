package com.openride.auth.service;

import com.openride.auth.config.TwilioProperties;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for sending SMS notifications using Twilio.
 */
@Service
@Slf4j
public class SmsService {

    private final TwilioProperties twilioProperties;

    public SmsService(TwilioProperties twilioProperties) {
        this.twilioProperties = twilioProperties;
        if (twilioProperties.getAccountSid() != null && !twilioProperties.getAccountSid().isEmpty()) {
            Twilio.init(twilioProperties.getAccountSid(), twilioProperties.getAuthToken());
        }
    }

    /**
     * Sends an OTP via SMS to the specified phone number.
     *
     * @param phoneNumber recipient phone number
     * @param otpCode OTP code to send
     * @return true if sent successfully, false otherwise
     */
    public boolean sendOtp(String phoneNumber, String otpCode) {
        try {
            if (twilioProperties.getAccountSid() == null || twilioProperties.getAccountSid().isEmpty()) {
                log.warn("Twilio not configured. OTP would be sent to {}: {}", phoneNumber, otpCode);
                return true; // For development/testing
            }

            String messageBody = String.format(
                "Your OpenRide verification code is: %s. Valid for 5 minutes. Do not share this code.",
                otpCode
            );

            Message message = Message.creator(
                new PhoneNumber(phoneNumber),
                new PhoneNumber(twilioProperties.getPhoneNumber()),
                messageBody
            ).create();

            log.info("SMS sent successfully to {}. SID: {}", phoneNumber, message.getSid());
            return true;

        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", phoneNumber, e.getMessage(), e);
            return false;
        }
    }
}

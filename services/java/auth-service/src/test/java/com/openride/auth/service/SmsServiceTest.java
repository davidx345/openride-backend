package com.openride.auth.service;

import com.openride.auth.config.AuthProperties;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SmsService.
 * Tests Twilio SMS integration and fallback behavior.
 */
@ExtendWith(MockitoExtension.class)
class SmsServiceTest {

    @Mock
    private AuthProperties authProperties;

    @Mock
    private AuthProperties.TwilioConfig twilioConfig;

    @InjectMocks
    private SmsService smsService;

    private static final String TEST_PHONE = "+2348012345678";
    private static final String TEST_OTP = "123456";
    private static final String FROM_PHONE = "+1234567890";

    @BeforeEach
    void setUp() {
        when(authProperties.getTwilio()).thenReturn(twilioConfig);
        when(twilioConfig.getFromPhoneNumber()).thenReturn(FROM_PHONE);
    }

    @Test
    void sendOtp_Success() {
        // Given
        Message mockMessage = mock(Message.class);
        when(mockMessage.getSid()).thenReturn("SM123456789");
        
        MessageCreator mockCreator = mock(MessageCreator.class);
        when(mockCreator.create()).thenReturn(mockMessage);

        try (MockedStatic<Message> messageMock = mockStatic(Message.class)) {
            messageMock.when(() -> Message.creator(
                any(PhoneNumber.class),
                any(PhoneNumber.class),
                anyString()
            )).thenReturn(mockCreator);

            // When
            assertThatCode(() -> smsService.sendOtp(TEST_PHONE, TEST_OTP))
                .doesNotThrowAnyException();

            // Then
            messageMock.verify(() -> Message.creator(
                argThat(to -> to.toString().equals(TEST_PHONE)),
                argThat(from -> from.toString().equals(FROM_PHONE)),
                contains(TEST_OTP)
            ));
        }
    }

    @Test
    void sendOtp_TwilioFailure_DoesNotThrowException() {
        // Given
        try (MockedStatic<Message> messageMock = mockStatic(Message.class)) {
            messageMock.when(() -> Message.creator(
                any(PhoneNumber.class),
                any(PhoneNumber.class),
                anyString()
            )).thenThrow(new RuntimeException("Twilio error"));

            // When & Then - Should not throw exception (fail-open behavior)
            assertThatCode(() -> smsService.sendOtp(TEST_PHONE, TEST_OTP))
                .doesNotThrowAnyException();
        }
    }

    @Test
    void sendOtp_FormatsMessageCorrectly() {
        // Given
        Message mockMessage = mock(Message.class);
        when(mockMessage.getSid()).thenReturn("SM123456789");
        
        MessageCreator mockCreator = mock(MessageCreator.class);
        when(mockCreator.create()).thenReturn(mockMessage);

        try (MockedStatic<Message> messageMock = mockStatic(Message.class)) {
            messageMock.when(() -> Message.creator(
                any(PhoneNumber.class),
                any(PhoneNumber.class),
                anyString()
            )).thenReturn(mockCreator);

            // When
            smsService.sendOtp(TEST_PHONE, TEST_OTP);

            // Then - Verify message contains OTP and app name
            messageMock.verify(() -> Message.creator(
                any(PhoneNumber.class),
                any(PhoneNumber.class),
                argThat(msg -> msg.contains(TEST_OTP) && 
                              msg.contains("OpenRide") &&
                              msg.contains("verification"))
            ));
        }
    }
}

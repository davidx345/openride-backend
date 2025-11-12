package com.openride.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for OTP send operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendOtpResponse {

    private String message;
    private int expiresIn;
}

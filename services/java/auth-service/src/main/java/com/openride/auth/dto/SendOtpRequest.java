package com.openride.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for sending OTP to a phone number.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendOtpRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^\\+234[7-9][0-1][0-9]{8}$",
        message = "Phone number must be valid Nigerian format (+234XXXXXXXXXX)"
    )
    private String phone;
}

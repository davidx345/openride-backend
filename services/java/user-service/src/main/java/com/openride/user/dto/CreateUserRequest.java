package com.openride.user.dto;

import com.openride.user.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new user.
 * Called internally by Auth Service after OTP verification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^\\+234[7-9][0-1][0-9]{8}$",
        message = "Phone number must be valid Nigerian format (+234XXXXXXXXXX)"
    )
    private String phone;

    private UserRole role;
}

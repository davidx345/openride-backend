package com.openride.payments.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Represents the authenticated user principal.
 * Stored in Spring Security context after JWT validation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal {

    private UUID userId;
    private String phone;
    private String role;

    /**
     * Checks if user has ADMIN role.
     */
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    /**
     * Checks if user has RIDER role.
     */
    public boolean isRider() {
        return "RIDER".equals(role);
    }

    /**
     * Checks if user has DRIVER role.
     */
    public boolean isDriver() {
        return "DRIVER".equals(role);
    }
}

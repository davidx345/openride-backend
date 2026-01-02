package com.openride.user.enums;

/**
 * User verification status enumeration.
 * Tracks the multi-step verification process for both Passengers and Captains.
 * 
 * UNVERIFIED = Phone verified only, no identity/affiliation verification
 * IDENTITY_PENDING = Identity verification submitted, awaiting result
 * IDENTITY_VERIFIED = NIN + Government ID verified via Dojah
 * AFFILIATION_PENDING = Work/Student ID or email verification in progress
 * FULLY_VERIFIED = Identity + Affiliation verified (can book rides as Passenger)
 * CAPTAIN_PENDING = Captain documents submitted, awaiting verification
 * CAPTAIN_VERIFIED = All Captain verifications complete (can offer rides)
 * REJECTED = Verification rejected (with reason)
 * SUSPENDED = Account suspended by admin
 */
public enum KycStatus {
    UNVERIFIED,
    IDENTITY_PENDING,
    IDENTITY_VERIFIED,
    AFFILIATION_PENDING,
    FULLY_VERIFIED,
    CAPTAIN_PENDING,
    CAPTAIN_VERIFIED,
    REJECTED,
    SUSPENDED
}

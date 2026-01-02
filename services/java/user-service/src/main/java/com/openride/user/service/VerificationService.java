package com.openride.user.service;

import com.openride.commons.exception.BusinessException;
import com.openride.user.dto.AffiliationVerificationRequest;
import com.openride.user.dto.CaptainVerificationRequest;
import com.openride.user.dto.IdentityVerificationRequest;
import com.openride.user.dto.VehicleRequest;
import com.openride.user.dto.VehicleResponse;
import com.openride.user.dto.VerificationStatusResponse;
import com.openride.user.entity.AffiliationVerification;
import com.openride.user.entity.CaptainVerification;
import com.openride.user.entity.IdentityVerification;
import com.openride.user.entity.User;
import com.openride.user.entity.Vehicle;
import com.openride.user.enums.KycStatus;
import com.openride.user.enums.UserRole;
import com.openride.user.enums.VerificationMethod;
import com.openride.user.repository.AffiliationVerificationRepository;
import com.openride.user.repository.CaptainVerificationRepository;
import com.openride.user.repository.IdentityVerificationRepository;
import com.openride.user.repository.UserRepository;
import com.openride.user.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for handling user verification workflows.
 * Supports multi-step verification for both Passengers and Captains.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationService {

    private final UserRepository userRepository;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final AffiliationVerificationRepository affiliationVerificationRepository;
    private final CaptainVerificationRepository captainVerificationRepository;
    private final VehicleRepository vehicleRepository;
    private final DojahService dojahService;
    private final EncryptionService encryptionService;

    /**
     * Gets verification status for a user.
     *
     * @param userId user ID
     * @return verification status response
     */
    public VerificationStatusResponse getVerificationStatus(UUID userId) {
        User user = getUserById(userId);

        var identityOpt = identityVerificationRepository.findByUserId(userId);
        var affiliationOpt = affiliationVerificationRepository.findByUserId(userId);
        var captainOpt = captainVerificationRepository.findByUserId(userId);
        var vehicleOpt = vehicleRepository.findByUserIdAndIsActiveTrue(userId);

        VerificationStatusResponse.IdentityStatus identityStatus = identityOpt
            .map(iv -> VerificationStatusResponse.IdentityStatus.builder()
                .submitted(true)
                .verified(iv.getIsVerified())
                .ninVerified(iv.getNinVerified())
                .selfieMatchScore(iv.getSelfieMatchScore())
                .rejectionReason(iv.getRejectionReason())
                .verifiedAt(iv.getVerifiedAt())
                .build())
            .orElse(VerificationStatusResponse.IdentityStatus.builder()
                .submitted(false)
                .verified(false)
                .build());

        VerificationStatusResponse.AffiliationStatus affiliationStatus = affiliationOpt
            .map(av -> VerificationStatusResponse.AffiliationStatus.builder()
                .submitted(true)
                .verified(av.getIsVerified())
                .affiliationType(av.getAffiliationType().name())
                .organizationName(av.getOrganizationName())
                .verificationMethod(av.getVerificationMethod().name())
                .emailVerified(av.getEmailVerified())
                .rejectionReason(av.getRejectionReason())
                .verifiedAt(av.getVerifiedAt())
                .build())
            .orElse(VerificationStatusResponse.AffiliationStatus.builder()
                .submitted(false)
                .verified(false)
                .build());

        VerificationStatusResponse.CaptainStatus captainStatus = null;
        if (user.getRole() == UserRole.CAPTAIN) {
            captainStatus = captainOpt
                .map(cv -> VerificationStatusResponse.CaptainStatus.builder()
                    .submitted(true)
                    .verified(cv.getIsVerified())
                    .licenseVerified(cv.getLicenseVerified())
                    .licenseExpiryDate(cv.getLicenseExpiryDate() != null 
                        ? cv.getLicenseExpiryDate().toString() : null)
                    .hasVehicle(vehicleOpt.isPresent())
                    .rejectionReason(cv.getRejectionReason())
                    .verifiedAt(cv.getVerifiedAt())
                    .build())
                .orElse(VerificationStatusResponse.CaptainStatus.builder()
                    .submitted(false)
                    .verified(false)
                    .hasVehicle(vehicleOpt.isPresent())
                    .build());
        }

        String nextStep = determineNextStep(user, identityStatus, affiliationStatus, captainStatus);
        boolean canBookRides = user.isFullyVerified();
        boolean canOfferRides = user.isCaptainVerified();

        return VerificationStatusResponse.builder()
            .kycStatus(user.getKycStatus())
            .statusMessage(getStatusMessage(user.getKycStatus()))
            .identity(identityStatus)
            .affiliation(affiliationStatus)
            .captain(captainStatus)
            .canBookRides(canBookRides)
            .canOfferRides(canOfferRides)
            .nextStep(nextStep)
            .build();
    }

    /**
     * Submits identity verification (NIN + Government ID + Selfie).
     *
     * @param userId user ID
     * @param request identity verification request
     * @return updated verification status
     */
    @Transactional
    public VerificationStatusResponse submitIdentityVerification(UUID userId, 
            IdentityVerificationRequest request) {
        log.info("Submitting identity verification for user: {}", userId);
        
        User user = getUserById(userId);

        // Check if already verified
        if (user.isIdentityVerified()) {
            throw new BusinessException(
                "ALREADY_VERIFIED",
                "Identity is already verified",
                HttpStatus.BAD_REQUEST
            );
        }

        // Get or create identity verification record
        IdentityVerification identity = identityVerificationRepository.findByUserId(userId)
            .orElse(IdentityVerification.builder().user(user).build());

        // Encrypt and store NIN
        identity.setNinEncrypted(encryptionService.encrypt(request.getNin()));
        identity.setGovernmentIdType(request.getGovernmentIdType());
        identity.setGovernmentIdUrl(request.getGovernmentIdUrl());
        identity.setSelfieUrl(request.getSelfieUrl());

        // Verify NIN via Dojah
        var ninResult = dojahService.verifyNin(request.getNin());
        if (!ninResult.verified()) {
            identity.setNinVerified(false);
            identity.markAsRejected("NIN verification failed: " + ninResult.errorMessage());
            identityVerificationRepository.save(identity);
            
            user.updateKycStatus(KycStatus.REJECTED);
            userRepository.save(user);
            
            throw new BusinessException(
                "NIN_VERIFICATION_FAILED",
                ninResult.errorMessage(),
                HttpStatus.BAD_REQUEST
            );
        }

        identity.setNinVerified(true);
        identity.setDojahReferenceId(ninResult.referenceId());

        // Perform selfie matching (if NIN photo available)
        if (ninResult.photoUrl() != null) {
            var selfieResult = dojahService.matchSelfie(request.getSelfieUrl(), ninResult.photoUrl());
            identity.setSelfieMatchScore(selfieResult.matchScore());
            
            if (!selfieResult.matched()) {
                identity.markAsRejected("Selfie match failed: " + selfieResult.errorMessage());
                identityVerificationRepository.save(identity);
                
                user.updateKycStatus(KycStatus.REJECTED);
                userRepository.save(user);
                
                throw new BusinessException(
                    "SELFIE_MATCH_FAILED",
                    selfieResult.errorMessage(),
                    HttpStatus.BAD_REQUEST
                );
            }
        }

        // Mark as verified
        identity.markAsVerified();
        identityVerificationRepository.save(identity);

        // Update user status
        user.updateKycStatus(KycStatus.IDENTITY_VERIFIED);
        user.setSelfieUrl(request.getSelfieUrl());
        userRepository.save(user);

        log.info("Identity verification completed for user: {}", userId);
        return getVerificationStatus(userId);
    }

    /**
     * Submits affiliation verification (Work/Student ID or Email).
     *
     * @param userId user ID
     * @param request affiliation verification request
     * @return updated verification status
     */
    @Transactional
    public VerificationStatusResponse submitAffiliationVerification(UUID userId,
            AffiliationVerificationRequest request) {
        log.info("Submitting affiliation verification for user: {}", userId);
        
        User user = getUserById(userId);

        // Check if identity is verified first
        if (!user.isIdentityVerified()) {
            throw new BusinessException(
                "IDENTITY_NOT_VERIFIED",
                "Please complete identity verification first",
                HttpStatus.BAD_REQUEST
            );
        }

        // Check if already fully verified
        if (user.isFullyVerified()) {
            throw new BusinessException(
                "ALREADY_VERIFIED",
                "Affiliation is already verified",
                HttpStatus.BAD_REQUEST
            );
        }

        // Validate request based on verification method
        if (request.getVerificationMethod() == VerificationMethod.ID_CARD 
                && (request.getIdCardUrl() == null || request.getIdCardUrl().isEmpty())) {
            throw new BusinessException(
                "ID_CARD_REQUIRED",
                "ID card image is required for ID card verification",
                HttpStatus.BAD_REQUEST
            );
        }

        if (request.getVerificationMethod() == VerificationMethod.EMAIL 
                && (request.getVerificationEmail() == null || request.getVerificationEmail().isEmpty())) {
            throw new BusinessException(
                "EMAIL_REQUIRED",
                "Email address is required for email verification",
                HttpStatus.BAD_REQUEST
            );
        }

        // Get or create affiliation verification record
        AffiliationVerification affiliation = affiliationVerificationRepository.findByUserId(userId)
            .orElse(AffiliationVerification.builder().user(user).build());

        affiliation.setAffiliationType(request.getAffiliationType());
        affiliation.setOrganizationName(request.getOrganizationName());
        affiliation.setVerificationMethod(request.getVerificationMethod());

        if (request.getVerificationMethod() == VerificationMethod.ID_CARD) {
            affiliation.setIdCardUrl(request.getIdCardUrl());
            // In test mode, auto-approve ID card
            affiliation.markAsVerified();
            user.updateKycStatus(KycStatus.FULLY_VERIFIED);
            
        } else {
            // EMAIL verification
            affiliation.setVerificationEmail(request.getVerificationEmail());
            String token = UUID.randomUUID().toString();
            affiliation.setVerificationToken(token, 24); // 24 hour expiry
            
            // TODO: Send verification email
            log.info("Verification email would be sent to: {} with token: {}", 
                request.getVerificationEmail(), token);
            
            user.updateKycStatus(KycStatus.AFFILIATION_PENDING);
        }

        affiliationVerificationRepository.save(affiliation);
        userRepository.save(user);

        log.info("Affiliation verification submitted for user: {}", userId);
        return getVerificationStatus(userId);
    }

    /**
     * Verifies email token for affiliation verification.
     *
     * @param token email verification token
     * @return updated verification status
     */
    @Transactional
    public VerificationStatusResponse verifyAffiliationEmail(String token) {
        log.info("Verifying affiliation email with token");

        AffiliationVerification affiliation = affiliationVerificationRepository
            .findByEmailVerificationToken(token)
            .orElseThrow(() -> new BusinessException(
                "INVALID_TOKEN",
                "Invalid or expired verification token",
                HttpStatus.BAD_REQUEST
            ));

        if (!affiliation.isTokenValid()) {
            throw new BusinessException(
                "TOKEN_EXPIRED",
                "Verification token has expired",
                HttpStatus.BAD_REQUEST
            );
        }

        affiliation.markEmailAsVerified();
        affiliationVerificationRepository.save(affiliation);

        User user = affiliation.getUser();
        user.updateKycStatus(KycStatus.FULLY_VERIFIED);
        userRepository.save(user);

        log.info("Email verification completed for user: {}", user.getId());
        return getVerificationStatus(user.getId());
    }

    /**
     * Submits Captain verification (Driver's License).
     *
     * @param userId user ID
     * @param request captain verification request
     * @return updated verification status
     */
    @Transactional
    public VerificationStatusResponse submitCaptainVerification(UUID userId,
            CaptainVerificationRequest request) {
        log.info("Submitting captain verification for user: {}", userId);
        
        User user = getUserById(userId);

        // Check if fully verified first
        if (!user.isFullyVerified()) {
            throw new BusinessException(
                "NOT_FULLY_VERIFIED",
                "Please complete identity and affiliation verification first",
                HttpStatus.BAD_REQUEST
            );
        }

        // Upgrade to Captain role if needed
        if (!user.isCaptain()) {
            user.upgradeToCaptain();
        }

        // Get or create captain verification record
        CaptainVerification captain = captainVerificationRepository.findByUserId(userId)
            .orElse(CaptainVerification.builder().user(user).build());

        captain.setLicenseNumberEncrypted(encryptionService.encrypt(request.getLicenseNumber()));
        captain.setLicensePhotoUrl(request.getLicensePhotoUrl());

        // Verify driver's license via Dojah
        var licenseResult = dojahService.verifyDriversLicense(request.getLicenseNumber());
        if (!licenseResult.verified()) {
            captain.setLicenseVerified(false);
            captain.markAsRejected("License verification failed: " + licenseResult.errorMessage());
            captainVerificationRepository.save(captain);
            
            user.updateKycStatus(KycStatus.REJECTED);
            userRepository.save(user);
            
            throw new BusinessException(
                "LICENSE_VERIFICATION_FAILED",
                licenseResult.errorMessage(),
                HttpStatus.BAD_REQUEST
            );
        }

        captain.setLicenseVerified(true);
        captain.setLicenseExpiryDate(licenseResult.expiryDate());
        captain.setDojahReferenceId(licenseResult.referenceId());

        // Check if license is expired
        if (captain.isLicenseExpired()) {
            captain.markAsRejected("Driver's license is expired");
            captainVerificationRepository.save(captain);
            
            throw new BusinessException(
                "LICENSE_EXPIRED",
                "Your driver's license has expired. Please renew and try again.",
                HttpStatus.BAD_REQUEST
            );
        }

        captain.markAsVerified();
        captainVerificationRepository.save(captain);

        // Check if user has vehicle registered
        boolean hasVehicle = vehicleRepository.findByUserIdAndIsActiveTrue(userId).isPresent();
        if (hasVehicle) {
            user.updateKycStatus(KycStatus.CAPTAIN_VERIFIED);
        } else {
            user.updateKycStatus(KycStatus.CAPTAIN_PENDING);
        }
        userRepository.save(user);

        log.info("Captain verification completed for user: {}", userId);
        return getVerificationStatus(userId);
    }

    /**
     * Registers a vehicle for a Captain.
     *
     * @param userId user ID
     * @param request vehicle request
     * @return vehicle response
     */
    @Transactional
    public VehicleResponse registerVehicle(UUID userId, VehicleRequest request) {
        log.info("Registering vehicle for user: {}", userId);
        
        User user = getUserById(userId);

        // Check if user is a Captain or becoming one
        if (!user.isCaptain() && !user.isFullyVerified()) {
            throw new BusinessException(
                "NOT_ELIGIBLE",
                "Please complete verification to register a vehicle",
                HttpStatus.BAD_REQUEST
            );
        }

        // Check if license plate already exists
        if (vehicleRepository.existsByLicensePlate(request.getLicensePlate())) {
            throw new BusinessException(
                "PLATE_EXISTS",
                "A vehicle with this license plate is already registered",
                HttpStatus.CONFLICT
            );
        }

        // Deactivate other vehicles
        vehicleRepository.deactivateAllVehiclesForUser(userId);

        // Create new vehicle
        Vehicle vehicle = Vehicle.builder()
            .user(user)
            .make(request.getMake())
            .model(request.getModel())
            .year(request.getYear())
            .color(request.getColor())
            .licensePlate(request.getLicensePlate().toUpperCase())
            .seatsAvailable(request.getSeatsAvailable())
            .registrationUrl(request.getRegistrationUrl())
            .insuranceUrl(request.getInsuranceUrl())
            .photoFrontUrl(request.getPhotoFrontUrl())
            .photoBackUrl(request.getPhotoBackUrl())
            .photoInteriorUrl(request.getPhotoInteriorUrl())
            .isActive(true)
            .isVerified(true) // Auto-verify in test mode
            .build();

        vehicle = vehicleRepository.save(vehicle);

        // Update user status if captain verification is complete
        if (user.getKycStatus() == KycStatus.CAPTAIN_PENDING) {
            user.updateKycStatus(KycStatus.CAPTAIN_VERIFIED);
            userRepository.save(user);
        }

        log.info("Vehicle registered for user: {}", userId);
        return mapToVehicleResponse(vehicle);
    }

    /**
     * Gets all vehicles for a user.
     *
     * @param userId user ID
     * @return list of vehicles
     */
    public List<VehicleResponse> getVehicles(UUID userId) {
        return vehicleRepository.findByUserId(userId).stream()
            .map(this::mapToVehicleResponse)
            .collect(Collectors.toList());
    }

    /**
     * Sets a vehicle as active.
     *
     * @param userId user ID
     * @param vehicleId vehicle ID
     * @return updated vehicle response
     */
    @Transactional
    public VehicleResponse setActiveVehicle(UUID userId, UUID vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new BusinessException(
                "VEHICLE_NOT_FOUND",
                "Vehicle not found",
                HttpStatus.NOT_FOUND
            ));

        if (!vehicle.getUser().getId().equals(userId)) {
            throw new BusinessException(
                "NOT_OWNER",
                "You are not the owner of this vehicle",
                HttpStatus.FORBIDDEN
            );
        }

        vehicleRepository.deactivateAllVehiclesForUser(userId);
        vehicle.activate();
        vehicleRepository.save(vehicle);

        return mapToVehicleResponse(vehicle);
    }

    // ==================== Helper Methods ====================

    private User getUserById(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(
                "USER_NOT_FOUND",
                "User not found",
                HttpStatus.NOT_FOUND
            ));
    }

    private String determineNextStep(User user, 
            VerificationStatusResponse.IdentityStatus identity,
            VerificationStatusResponse.AffiliationStatus affiliation,
            VerificationStatusResponse.CaptainStatus captain) {
        
        if (!identity.isSubmitted() || !identity.isVerified()) {
            return "VERIFY_IDENTITY";
        }
        
        if (!affiliation.isSubmitted() || !affiliation.isVerified()) {
            if (affiliation.isSubmitted() && !affiliation.isEmailVerified() 
                    && "EMAIL".equals(affiliation.getVerificationMethod())) {
                return "VERIFY_EMAIL";
            }
            return "VERIFY_AFFILIATION";
        }
        
        if (user.getRole() == UserRole.CAPTAIN) {
            if (captain == null || !captain.isSubmitted() || !captain.isVerified()) {
                return "VERIFY_LICENSE";
            }
            if (!captain.isHasVehicle()) {
                return "REGISTER_VEHICLE";
            }
        }
        
        return "COMPLETE";
    }

    private String getStatusMessage(KycStatus status) {
        return switch (status) {
            case UNVERIFIED -> "Please verify your identity to start booking rides";
            case IDENTITY_PENDING -> "Your identity verification is being processed";
            case IDENTITY_VERIFIED -> "Identity verified! Please verify your affiliation";
            case AFFILIATION_PENDING -> "Please check your email to complete verification";
            case FULLY_VERIFIED -> "You are fully verified and can book rides";
            case CAPTAIN_PENDING -> "Please register your vehicle to start offering rides";
            case CAPTAIN_VERIFIED -> "You are verified as a Captain and can offer rides";
            case REJECTED -> "Your verification was rejected. Please check the details and try again";
            case SUSPENDED -> "Your account has been suspended. Please contact support";
        };
    }

    private VehicleResponse mapToVehicleResponse(Vehicle vehicle) {
        return VehicleResponse.builder()
            .id(vehicle.getId())
            .make(vehicle.getMake())
            .model(vehicle.getModel())
            .year(vehicle.getYear())
            .color(vehicle.getColor())
            .licensePlate(vehicle.getLicensePlate())
            .seatsAvailable(vehicle.getSeatsAvailable())
            .registrationUrl(vehicle.getRegistrationUrl())
            .insuranceUrl(vehicle.getInsuranceUrl())
            .photoFrontUrl(vehicle.getPhotoFrontUrl())
            .photoBackUrl(vehicle.getPhotoBackUrl())
            .photoInteriorUrl(vehicle.getPhotoInteriorUrl())
            .isVerified(vehicle.getIsVerified())
            .isActive(vehicle.getIsActive())
            .displayName(vehicle.getDisplayName())
            .createdAt(vehicle.getCreatedAt())
            .build();
    }
}

package com.openride.user.service;

import com.openride.commons.exception.BusinessException;
import com.openride.user.dto.CreateUserRequest;
import com.openride.user.dto.KycDocumentsRequest;
import com.openride.user.dto.UpdateKycStatusRequest;
import com.openride.user.dto.UpdateUserRequest;
import com.openride.user.dto.UserResponse;
import com.openride.user.entity.DriverProfile;
import com.openride.user.entity.User;
import com.openride.user.enums.KycStatus;
import com.openride.user.enums.UserRole;
import com.openride.user.repository.DriverProfileRepository;
import com.openride.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for user management operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final EncryptionService encryptionService;

    /**
     * Creates a new user or returns existing user by phone.
     * Called internally by Auth Service.
     *
     * @param request create user request
     * @return user response
     */
    @Transactional
    public UserResponse createOrGetUser(CreateUserRequest request) {
        log.info("Creating or getting user with phone: {}", request.getPhone());

        User user = userRepository.findByPhone(request.getPhone())
            .orElseGet(() -> {
                User newUser = User.builder()
                    .phone(request.getPhone())
                    .role(request.getRole() != null ? request.getRole() : UserRole.RIDER)
                    .build();
                return userRepository.save(newUser);
            });

        log.info("User retrieved/created with ID: {}", user.getId());
        return mapToResponse(user);
    }

    /**
     * Gets user by ID.
     *
     * @param userId user ID
     * @return user response
     */
    public UserResponse getUserById(UUID userId) {
        log.info("Getting user by ID: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(
                "USER_NOT_FOUND",
                "User not found with ID: " + userId,
                HttpStatus.NOT_FOUND
            ));

        return mapToResponse(user);
    }

    /**
     * Gets user by phone number.
     *
     * @param phone phone number
     * @return user response
     */
    public UserResponse getUserByPhone(String phone) {
        log.info("Getting user by phone: {}", phone);

        User user = userRepository.findByPhone(phone)
            .orElseThrow(() -> new BusinessException(
                "USER_NOT_FOUND",
                "User not found with phone: " + phone,
                HttpStatus.NOT_FOUND
            ));

        return mapToResponse(user);
    }

    /**
     * Updates user profile.
     *
     * @param userId user ID
     * @param request update request
     * @return updated user response
     */
    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        log.info("Updating user: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(
                "USER_NOT_FOUND",
                "User not found",
                HttpStatus.NOT_FOUND
            ));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }

        userRepository.save(user);

        log.info("User updated successfully: {}", userId);
        return mapToResponse(user);
    }

    /**
     * Upgrades a rider to driver role.
     *
     * @param userId user ID
     * @return updated user response
     */
    @Transactional
    public UserResponse upgradeToDriver(UUID userId) {
        log.info("Upgrading user to driver: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(
                "USER_NOT_FOUND",
                "User not found",
                HttpStatus.NOT_FOUND
            ));

        if (user.isDriver()) {
            throw new BusinessException(
                "ALREADY_DRIVER",
                "User is already a driver",
                HttpStatus.BAD_REQUEST
            );
        }

        user.upgradeToDriver();
        userRepository.save(user);

        DriverProfile driverProfile = DriverProfile.builder()
            .user(user)
            .build();
        driverProfileRepository.save(driverProfile);

        log.info("User upgraded to driver successfully: {}", userId);
        return mapToResponse(user);
    }

    /**
     * Submits KYC documents for driver verification.
     *
     * @param userId user ID
     * @param request KYC documents request
     * @return updated user response
     */
    @Transactional
    public UserResponse submitKycDocuments(UUID userId, KycDocumentsRequest request) {
        log.info("Submitting KYC documents for user: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(
                "USER_NOT_FOUND",
                "User not found",
                HttpStatus.NOT_FOUND
            ));

        if (!user.isDriver()) {
            throw new BusinessException(
                "NOT_A_DRIVER",
                "User must be a driver to submit KYC documents",
                HttpStatus.BAD_REQUEST
            );
        }

        DriverProfile driverProfile = driverProfileRepository.findByUser(user)
            .orElseThrow(() -> new BusinessException(
                "DRIVER_PROFILE_NOT_FOUND",
                "Driver profile not found",
                HttpStatus.NOT_FOUND
            ));

        String encryptedBvn = encryptionService.encrypt(request.getBvn());
        String encryptedLicense = encryptionService.encrypt(request.getLicenseNumber());

        driverProfile.setBvnEncrypted(encryptedBvn);
        driverProfile.setLicenseNumberEncrypted(encryptedLicense);
        driverProfile.setLicensePhotoUrl(request.getLicensePhotoUrl());
        driverProfile.setVehiclePhotoUrl(request.getVehiclePhotoUrl());

        driverProfileRepository.save(driverProfile);

        user.updateKycStatus(KycStatus.PENDING);
        userRepository.save(user);

        log.info("KYC documents submitted successfully for user: {}", userId);
        return mapToResponse(user);
    }

    /**
     * Updates KYC status (admin only).
     *
     * @param userId user ID
     * @param request update KYC status request
     * @return updated user response
     */
    @Transactional
    public UserResponse updateKycStatus(UUID userId, UpdateKycStatusRequest request) {
        log.info("Updating KYC status for user: {} to {}", userId, request.getStatus());

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(
                "USER_NOT_FOUND",
                "User not found",
                HttpStatus.NOT_FOUND
            ));

        if (!user.isDriver()) {
            throw new BusinessException(
                "NOT_A_DRIVER",
                "User must be a driver to update KYC status",
                HttpStatus.BAD_REQUEST
            );
        }

        user.updateKycStatus(request.getStatus());
        userRepository.save(user);

        if (request.getNotes() != null) {
            DriverProfile driverProfile = driverProfileRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(
                    "DRIVER_PROFILE_NOT_FOUND",
                    "Driver profile not found",
                    HttpStatus.NOT_FOUND
                ));
            driverProfile.setKycNotes(request.getNotes());
            driverProfileRepository.save(driverProfile);
        }

        log.info("KYC status updated successfully for user: {}", userId);
        return mapToResponse(user);
    }

    /**
     * Gets all drivers with pending KYC status.
     *
     * @return list of user responses
     */
    public List<UserResponse> getPendingKycDrivers() {
        log.info("Getting all drivers with pending KYC");

        List<User> users = userRepository.findByRoleAndKycStatus(
            UserRole.DRIVER,
            KycStatus.PENDING
        );

        return users.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Maps User entity to UserResponse DTO.
     *
     * @param user user entity
     * @return user response
     */
    private UserResponse mapToResponse(User user) {
        UserResponse.UserResponseBuilder builder = UserResponse.builder()
            .id(user.getId())
            .phone(user.getPhone())
            .fullName(user.getFullName())
            .email(user.getEmail())
            .role(user.getRole())
            .kycStatus(user.getKycStatus())
            .rating(user.getRating())
            .isActive(user.getIsActive())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt());

        if (user.isDriver()) {
            driverProfileRepository.findByUser(user).ifPresent(profile -> {
                builder.driverProfile(UserResponse.DriverProfileResponse.builder()
                    .id(profile.getId())
                    .licensePhotoUrl(profile.getLicensePhotoUrl())
                    .vehiclePhotoUrl(profile.getVehiclePhotoUrl())
                    .kycNotes(profile.getKycNotes())
                    .totalTrips(profile.getTotalTrips())
                    .totalEarnings(profile.getTotalEarnings())
                    .build());
            });
        }

        return builder.build();
    }
}

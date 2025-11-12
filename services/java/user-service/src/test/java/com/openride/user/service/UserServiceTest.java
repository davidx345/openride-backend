package com.openride.user.service;

import com.openride.commons.exception.BusinessException;
import com.openride.user.dto.*;
import com.openride.user.entity.DriverProfile;
import com.openride.user.entity.User;
import com.openride.user.enums.KycStatus;
import com.openride.user.enums.UserRole;
import com.openride.user.repository.DriverProfileRepository;
import com.openride.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService.
 * Tests user management, driver upgrade, and KYC workflow.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private UserService userService;

    private static final String TEST_USER_ID = UUID.randomUUID().toString();
    private static final String TEST_PHONE = "+2348012345678";
    private static final String TEST_BVN = "12345678901";
    private static final String TEST_LICENSE = "LAG-123-ABC";
    private static final String ENCRYPTED_BVN = "encrypted_bvn_base64";
    private static final String ENCRYPTED_LICENSE = "encrypted_license_base64";

    private User testUser;
    private DriverProfile testDriverProfile;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(UUID.fromString(TEST_USER_ID))
            .phone(TEST_PHONE)
            .fullName("John Doe")
            .email("john@example.com")
            .role(UserRole.RIDER)
            .kycStatus(KycStatus.NONE)
            .rating(null)
            .isActive(true)
            .build();

        testDriverProfile = DriverProfile.builder()
            .id(UUID.randomUUID())
            .user(testUser)
            .bvnEncrypted(ENCRYPTED_BVN)
            .licenseNumberEncrypted(ENCRYPTED_LICENSE)
            .licensePhotoUrl("https://example.com/license.jpg")
            .vehiclePhotoUrl("https://example.com/vehicle.jpg")
            .kycNotes(null)
            .totalTrips(0)
            .totalEarnings(BigDecimal.ZERO)
            .build();
    }

    @Test
    void createOrGetUser_NewUser_CreatesUser() {
        // Given
        CreateUserRequest request = new CreateUserRequest(TEST_PHONE, "RIDER");
        when(userRepository.findByPhone(TEST_PHONE)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // When
        UserResponse response = userService.createOrGetUser(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPhone()).isEqualTo(TEST_PHONE);
        assertThat(response.getRole()).isEqualTo("RIDER");
        assertThat(response.getKycStatus()).isEqualTo("NONE");
        assertThat(response.isActive()).isTrue();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getPhone()).isEqualTo(TEST_PHONE);
        assertThat(savedUser.getRole()).isEqualTo(UserRole.RIDER);
    }

    @Test
    void createOrGetUser_ExistingUser_ReturnsExistingUser() {
        // Given
        CreateUserRequest request = new CreateUserRequest(TEST_PHONE, "RIDER");
        when(userRepository.findByPhone(TEST_PHONE)).thenReturn(Optional.of(testUser));

        // When
        UserResponse response = userService.createOrGetUser(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(response.getPhone()).isEqualTo(TEST_PHONE);

        verify(userRepository, never()).save(any());
    }

    @Test
    void getUserById_UserExists_ReturnsUser() {
        // Given
        when(userRepository.findById(UUID.fromString(TEST_USER_ID))).thenReturn(Optional.of(testUser));

        // When
        UserResponse response = userService.getUserById(TEST_USER_ID);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(response.getPhone()).isEqualTo(TEST_PHONE);
        assertThat(response.getFullName()).isEqualTo("John Doe");
    }

    @Test
    void getUserById_UserNotFound_ThrowsException() {
        // Given
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getUserById(TEST_USER_ID))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("User not found")
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void getUserByPhone_UserExists_ReturnsUser() {
        // Given
        when(userRepository.findByPhone(TEST_PHONE)).thenReturn(Optional.of(testUser));

        // When
        UserResponse response = userService.getUserByPhone(TEST_PHONE);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPhone()).isEqualTo(TEST_PHONE);
    }

    @Test
    void getUserByPhone_UserNotFound_ThrowsException() {
        // Given
        when(userRepository.findByPhone(TEST_PHONE)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getUserByPhone(TEST_PHONE))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("User not found")
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void updateUser_ValidRequest_UpdatesUser() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest("Jane Doe", "jane@example.com");
        when(userRepository.findById(UUID.fromString(TEST_USER_ID))).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // When
        UserResponse response = userService.updateUser(TEST_USER_ID, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getFullName()).isEqualTo("Jane Doe");
        assertThat(response.getEmail()).isEqualTo("jane@example.com");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User updatedUser = userCaptor.getValue();
        assertThat(updatedUser.getFullName()).isEqualTo("Jane Doe");
        assertThat(updatedUser.getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void updateUser_PartialUpdate_UpdatesOnlyProvidedFields() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest("Jane Doe", null);
        when(userRepository.findById(UUID.fromString(TEST_USER_ID))).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // When
        UserResponse response = userService.updateUser(TEST_USER_ID, request);

        // Then
        assertThat(response.getFullName()).isEqualTo("Jane Doe");
        assertThat(response.getEmail()).isEqualTo("john@example.com"); // Original email preserved
    }

    @Test
    void upgradeToDriver_RiderUser_UpgradesSuccessfully() {
        // Given
        when(userRepository.findById(UUID.fromString(TEST_USER_ID))).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(driverProfileRepository.save(any(DriverProfile.class))).thenAnswer(i -> i.getArgument(0));

        // When
        UserResponse response = userService.upgradeToDriver(TEST_USER_ID);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRole()).isEqualTo("DRIVER");
        assertThat(response.getDriverProfile()).isNotNull();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.DRIVER);

        verify(driverProfileRepository).save(any(DriverProfile.class));
    }

    @Test
    void upgradeToDriver_AlreadyDriver_ThrowsException() {
        // Given
        testUser.setRole(UserRole.DRIVER);
        when(userRepository.findById(UUID.fromString(TEST_USER_ID))).thenReturn(Optional.of(testUser));

        // When & Then
        assertThatThrownBy(() -> userService.upgradeToDriver(TEST_USER_ID))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("already a driver")
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("ALREADY_DRIVER");

        verify(userRepository, never()).save(any());
        verify(driverProfileRepository, never()).save(any());
    }

    @Test
    void submitKycDocuments_DriverUser_EncryptsAndSavesDocuments() {
        // Given
        testUser.setRole(UserRole.DRIVER);
        KycDocumentsRequest request = new KycDocumentsRequest(
            TEST_BVN,
            TEST_LICENSE,
            "https://example.com/license.jpg",
            "https://example.com/vehicle.jpg"
        );

        when(userRepository.findById(UUID.fromString(TEST_USER_ID))).thenReturn(Optional.of(testUser));
        when(driverProfileRepository.findByUserId(UUID.fromString(TEST_USER_ID)))
            .thenReturn(Optional.of(testDriverProfile));
        when(encryptionService.encrypt(TEST_BVN)).thenReturn(ENCRYPTED_BVN);
        when(encryptionService.encrypt(TEST_LICENSE)).thenReturn(ENCRYPTED_LICENSE);
        when(driverProfileRepository.save(any(DriverProfile.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // When
        UserResponse response = userService.submitKycDocuments(TEST_USER_ID, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getKycStatus()).isEqualTo("PENDING");

        // Verify encryption was called
        verify(encryptionService).encrypt(TEST_BVN);
        verify(encryptionService).encrypt(TEST_LICENSE);

        // Verify driver profile was updated
        ArgumentCaptor<DriverProfile> profileCaptor = ArgumentCaptor.forClass(DriverProfile.class);
        verify(driverProfileRepository).save(profileCaptor.capture());
        DriverProfile savedProfile = profileCaptor.getValue();
        assertThat(savedProfile.getBvnEncrypted()).isEqualTo(ENCRYPTED_BVN);
        assertThat(savedProfile.getLicenseNumberEncrypted()).isEqualTo(ENCRYPTED_LICENSE);
        assertThat(savedProfile.getLicensePhotoUrl()).isEqualTo("https://example.com/license.jpg");
        assertThat(savedProfile.getVehiclePhotoUrl()).isEqualTo("https://example.com/vehicle.jpg");

        // Verify user KYC status was updated to PENDING
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getKycStatus()).isEqualTo(KycStatus.PENDING);
    }

    @Test
    void submitKycDocuments_RiderUser_ThrowsException() {
        // Given
        KycDocumentsRequest request = new KycDocumentsRequest(
            TEST_BVN,
            TEST_LICENSE,
            "https://example.com/license.jpg",
            "https://example.com/vehicle.jpg"
        );

        when(userRepository.findById(UUID.fromString(TEST_USER_ID))).thenReturn(Optional.of(testUser));

        // When & Then
        assertThatThrownBy(() -> userService.submitKycDocuments(TEST_USER_ID, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("not a driver")
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("NOT_A_DRIVER");

        verify(encryptionService, never()).encrypt(anyString());
        verify(driverProfileRepository, never()).save(any());
    }

    @Test
    void submitKycDocuments_DriverProfileNotFound_ThrowsException() {
        // Given
        testUser.setRole(UserRole.DRIVER);
        KycDocumentsRequest request = new KycDocumentsRequest(
            TEST_BVN,
            TEST_LICENSE,
            "https://example.com/license.jpg",
            "https://example.com/vehicle.jpg"
        );

        when(userRepository.findById(UUID.fromString(TEST_USER_ID))).thenReturn(Optional.of(testUser));
        when(driverProfileRepository.findByUserId(UUID.fromString(TEST_USER_ID)))
            .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.submitKycDocuments(TEST_USER_ID, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Driver profile not found")
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("DRIVER_PROFILE_NOT_FOUND");
    }

    @Test
    void updateKycStatus_ValidRequest_UpdatesStatus() {
        // Given
        testUser.setRole(UserRole.DRIVER);
        testUser.setKycStatus(KycStatus.PENDING);
        UpdateKycStatusRequest request = new UpdateKycStatusRequest("VERIFIED", "Documents approved");

        when(userRepository.findById(UUID.fromString(TEST_USER_ID))).thenReturn(Optional.of(testUser));
        when(driverProfileRepository.findByUserId(UUID.fromString(TEST_USER_ID)))
            .thenReturn(Optional.of(testDriverProfile));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(driverProfileRepository.save(any(DriverProfile.class))).thenAnswer(i -> i.getArgument(0));

        // When
        UserResponse response = userService.updateKycStatus(TEST_USER_ID, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getKycStatus()).isEqualTo("VERIFIED");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getKycStatus()).isEqualTo(KycStatus.VERIFIED);

        ArgumentCaptor<DriverProfile> profileCaptor = ArgumentCaptor.forClass(DriverProfile.class);
        verify(driverProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getKycNotes()).isEqualTo("Documents approved");
    }

    @Test
    void updateKycStatus_RejectStatus_UpdatesCorrectly() {
        // Given
        testUser.setRole(UserRole.DRIVER);
        testUser.setKycStatus(KycStatus.PENDING);
        UpdateKycStatusRequest request = new UpdateKycStatusRequest("REJECTED", "Invalid license photo");

        when(userRepository.findById(UUID.fromString(TEST_USER_ID))).thenReturn(Optional.of(testUser));
        when(driverProfileRepository.findByUserId(UUID.fromString(TEST_USER_ID)))
            .thenReturn(Optional.of(testDriverProfile));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(driverProfileRepository.save(any(DriverProfile.class))).thenAnswer(i -> i.getArgument(0));

        // When
        UserResponse response = userService.updateKycStatus(TEST_USER_ID, request);

        // Then
        assertThat(response.getKycStatus()).isEqualTo("REJECTED");

        ArgumentCaptor<DriverProfile> profileCaptor = ArgumentCaptor.forClass(DriverProfile.class);
        verify(driverProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getKycNotes()).isEqualTo("Invalid license photo");
    }

    @Test
    void getPendingKycDrivers_ReturnsPendingDrivers() {
        // Given
        User driver1 = User.builder()
            .id(UUID.randomUUID())
            .phone("+2348011111111")
            .role(UserRole.DRIVER)
            .kycStatus(KycStatus.PENDING)
            .isActive(true)
            .build();

        User driver2 = User.builder()
            .id(UUID.randomUUID())
            .phone("+2348022222222")
            .role(UserRole.DRIVER)
            .kycStatus(KycStatus.PENDING)
            .isActive(true)
            .build();

        when(userRepository.findByRoleAndKycStatus(UserRole.DRIVER, KycStatus.PENDING))
            .thenReturn(Arrays.asList(driver1, driver2));

        // When
        List<UserResponse> responses = userService.getPendingKycDrivers();

        // Then
        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(UserResponse::getRole).containsOnly("DRIVER");
        assertThat(responses).extracting(UserResponse::getKycStatus).containsOnly("PENDING");
    }

    @Test
    void getPendingKycDrivers_NoPendingDrivers_ReturnsEmptyList() {
        // Given
        when(userRepository.findByRoleAndKycStatus(UserRole.DRIVER, KycStatus.PENDING))
            .thenReturn(Arrays.asList());

        // When
        List<UserResponse> responses = userService.getPendingKycDrivers();

        // Then
        assertThat(responses).isEmpty();
    }

    @Test
    void mapToResponse_RiderUser_DoesNotIncludeDriverProfile() {
        // Given - testUser is a RIDER by default

        // When
        UserResponse response = userService.mapToResponse(testUser);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(response.getRole()).isEqualTo("RIDER");
        assertThat(response.getDriverProfile()).isNull();
    }

    @Test
    void mapToResponse_DriverUser_IncludesDriverProfile() {
        // Given
        testUser.setRole(UserRole.DRIVER);
        when(driverProfileRepository.findByUserId(UUID.fromString(TEST_USER_ID)))
            .thenReturn(Optional.of(testDriverProfile));

        // When
        UserResponse response = userService.mapToResponse(testUser);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRole()).isEqualTo("DRIVER");
        assertThat(response.getDriverProfile()).isNotNull();
        assertThat(response.getDriverProfile().getLicensePhotoUrl())
            .isEqualTo("https://example.com/license.jpg");
        assertThat(response.getDriverProfile().getTotalTrips()).isEqualTo(0);
    }
}

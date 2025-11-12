package com.openride.user.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openride.commons.util.JwtUtil;
import com.openride.user.dto.*;
import com.openride.user.entity.DriverProfile;
import com.openride.user.entity.User;
import com.openride.user.enums.KycStatus;
import com.openride.user.enums.UserRole;
import com.openride.user.repository.DriverProfileRepository;
import com.openride.user.repository.UserRepository;
import com.openride.user.service.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for User Service.
 * Tests end-to-end user management, driver upgrade, and KYC workflows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriverProfileRepository driverProfileRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    private static final String BASE_URL = "/api/v1/users";
    private static final String ADMIN_URL = "/api/v1/admin";
    private static final String TEST_PHONE = "+2348012345678";
    private static final String TEST_BVN = "12345678901";
    private static final String TEST_LICENSE = "LAG-123-ABC";

    @BeforeEach
    void setUp() {
        // Clean up database before each test
        driverProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void completeUserLifecycle_CreateToUpdateToDriverToKycVerified_Success() throws Exception {
        // Step 1: Create user (internal endpoint, no auth)
        CreateUserRequest createRequest = new CreateUserRequest(TEST_PHONE, "RIDER");
        
        MvcResult createResult = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phone").value(TEST_PHONE))
            .andExpect(jsonPath("$.role").value("RIDER"))
            .andExpect(jsonPath("$.kycStatus").value("NONE"))
            .andExpect(jsonPath("$.isActive").value(true))
            .andReturn();

        UserResponse createdUser = objectMapper.readValue(
            createResult.getResponse().getContentAsString(),
            UserResponse.class
        );
        String userId = createdUser.getUserId();

        // Verify user in database
        User dbUser = userRepository.findById(UUID.fromString(userId)).get();
        assertThat(dbUser.getPhone()).isEqualTo(TEST_PHONE);
        assertThat(dbUser.getRole()).isEqualTo(UserRole.RIDER);

        // Step 2: Get current user (requires auth)
        String accessToken = generateAccessToken(userId, "RIDER");
        
        mockMvc.perform(get(BASE_URL + "/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.phone").value(TEST_PHONE));

        // Step 3: Update user profile
        UpdateUserRequest updateRequest = new UpdateUserRequest("John Doe", "john@example.com");
        
        mockMvc.perform(patch(BASE_URL + "/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("John Doe"))
            .andExpect(jsonPath("$.email").value("john@example.com"));

        // Verify update in database
        User updatedUser = userRepository.findById(UUID.fromString(userId)).get();
        assertThat(updatedUser.getFullName()).isEqualTo("John Doe");
        assertThat(updatedUser.getEmail()).isEqualTo("john@example.com");

        // Step 4: Upgrade to driver
        mockMvc.perform(post(BASE_URL + "/upgrade-to-driver")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("DRIVER"))
            .andExpect(jsonPath("$.driverProfile").exists())
            .andExpect(jsonPath("$.driverProfile.totalTrips").value(0));

        // Verify driver profile created
        User driverUser = userRepository.findById(UUID.fromString(userId)).get();
        assertThat(driverUser.getRole()).isEqualTo(UserRole.DRIVER);
        
        DriverProfile driverProfile = driverProfileRepository.findByUserId(UUID.fromString(userId)).get();
        assertThat(driverProfile).isNotNull();
        assertThat(driverProfile.getTotalTrips()).isEqualTo(0);
        assertThat(driverProfile.getTotalEarnings()).isEqualTo(BigDecimal.ZERO);

        // Step 5: Submit KYC documents (need new token with DRIVER role)
        String driverToken = generateAccessToken(userId, "DRIVER");
        KycDocumentsRequest kycRequest = new KycDocumentsRequest(
            TEST_BVN,
            TEST_LICENSE,
            "https://example.com/license.jpg",
            "https://example.com/vehicle.jpg"
        );
        
        mockMvc.perform(post(BASE_URL + "/drivers/kyc-documents")
                .header("Authorization", "Bearer " + driverToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(kycRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kycStatus").value("PENDING"))
            .andExpect(jsonPath("$.driverProfile.licensePhotoUrl").value("https://example.com/license.jpg"));

        // Verify KYC data is encrypted in database
        User kycUser = userRepository.findById(UUID.fromString(userId)).get();
        assertThat(kycUser.getKycStatus()).isEqualTo(KycStatus.PENDING);
        
        DriverProfile kycProfile = driverProfileRepository.findByUserId(UUID.fromString(userId)).get();
        assertThat(kycProfile.getBvnEncrypted()).isNotNull();
        assertThat(kycProfile.getBvnEncrypted()).isNotEqualTo(TEST_BVN);
        assertThat(kycProfile.getLicenseNumberEncrypted()).isNotNull();
        assertThat(kycProfile.getLicenseNumberEncrypted()).isNotEqualTo(TEST_LICENSE);
        
        // Verify decryption works
        String decryptedBvn = encryptionService.decrypt(kycProfile.getBvnEncrypted());
        String decryptedLicense = encryptionService.decrypt(kycProfile.getLicenseNumberEncrypted());
        assertThat(decryptedBvn).isEqualTo(TEST_BVN);
        assertThat(decryptedLicense).isEqualTo(TEST_LICENSE);

        // Step 6: Admin gets pending KYC drivers
        String adminToken = generateAccessToken("admin-123", "ADMIN");
        
        MvcResult pendingResult = mockMvc.perform(get(ADMIN_URL + "/drivers/pending-verification")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].userId").value(userId))
            .andExpect(jsonPath("$[0].kycStatus").value("PENDING"))
            .andReturn();

        // Step 7: Admin verifies KYC
        UpdateKycStatusRequest verifyRequest = new UpdateKycStatusRequest("VERIFIED", "All documents approved");
        
        mockMvc.perform(patch(ADMIN_URL + "/users/" + userId + "/kyc-status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kycStatus").value("VERIFIED"));

        // Verify final state
        User verifiedUser = userRepository.findById(UUID.fromString(userId)).get();
        assertThat(verifiedUser.getKycStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(verifiedUser.getRole()).isEqualTo(UserRole.DRIVER);
        
        DriverProfile verifiedProfile = driverProfileRepository.findByUserId(UUID.fromString(userId)).get();
        assertThat(verifiedProfile.getKycNotes()).isEqualTo("All documents approved");
    }

    @Test
    void createUser_ExistingPhone_ReturnsExistingUser() throws Exception {
        // Given - Create user first time
        CreateUserRequest request = new CreateUserRequest(TEST_PHONE, "RIDER");
        
        MvcResult firstResult = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

        UserResponse firstUser = objectMapper.readValue(
            firstResult.getResponse().getContentAsString(),
            UserResponse.class
        );

        // When - Create user again with same phone
        MvcResult secondResult = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

        UserResponse secondUser = objectMapper.readValue(
            secondResult.getResponse().getContentAsString(),
            UserResponse.class
        );

        // Then - Should return same user
        assertThat(secondUser.getUserId()).isEqualTo(firstUser.getUserId());
        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    void upgradeToDriver_AlreadyDriver_ReturnsError() throws Exception {
        // Given - Create driver user
        User user = User.builder()
            .phone(TEST_PHONE)
            .role(UserRole.DRIVER)
            .kycStatus(KycStatus.NONE)
            .isActive(true)
            .build();
        userRepository.save(user);

        String accessToken = generateAccessToken(user.getId().toString(), "DRIVER");

        // When & Then
        mockMvc.perform(post(BASE_URL + "/upgrade-to-driver")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("ALREADY_DRIVER"));
    }

    @Test
    void submitKycDocuments_NotDriver_ReturnsError() throws Exception {
        // Given - Create rider user
        User user = User.builder()
            .phone(TEST_PHONE)
            .role(UserRole.RIDER)
            .kycStatus(KycStatus.NONE)
            .isActive(true)
            .build();
        userRepository.save(user);

        String accessToken = generateAccessToken(user.getId().toString(), "RIDER");
        KycDocumentsRequest request = new KycDocumentsRequest(
            TEST_BVN,
            TEST_LICENSE,
            "https://example.com/license.jpg",
            "https://example.com/vehicle.jpg"
        );

        // When & Then
        mockMvc.perform(post(BASE_URL + "/drivers/kyc-documents")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("NOT_A_DRIVER"));
    }

    @Test
    void adminEndpoints_NonAdminUser_ReturnsForbidden() throws Exception {
        // Given
        String riderToken = generateAccessToken("rider-123", "RIDER");
        String driverToken = generateAccessToken("driver-123", "DRIVER");

        // When & Then - Rider tries to access admin endpoint
        mockMvc.perform(get(ADMIN_URL + "/drivers/pending-verification")
                .header("Authorization", "Bearer " + riderToken))
            .andExpect(status().isForbidden());

        // When & Then - Driver tries to access admin endpoint
        mockMvc.perform(get(ADMIN_URL + "/drivers/pending-verification")
                .header("Authorization", "Bearer " + driverToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void kycWorkflow_AdminReject_UpdatesStatusCorrectly() throws Exception {
        // Given - Create driver with pending KYC
        User driver = User.builder()
            .phone(TEST_PHONE)
            .fullName("John Doe")
            .role(UserRole.DRIVER)
            .kycStatus(KycStatus.PENDING)
            .isActive(true)
            .build();
        userRepository.save(driver);

        DriverProfile profile = DriverProfile.builder()
            .user(driver)
            .bvnEncrypted(encryptionService.encrypt(TEST_BVN))
            .licenseNumberEncrypted(encryptionService.encrypt(TEST_LICENSE))
            .totalTrips(0)
            .totalEarnings(BigDecimal.ZERO)
            .build();
        driverProfileRepository.save(profile);

        String adminToken = generateAccessToken("admin-123", "ADMIN");
        UpdateKycStatusRequest rejectRequest = new UpdateKycStatusRequest(
            "REJECTED",
            "License photo is blurry"
        );

        // When
        mockMvc.perform(patch(ADMIN_URL + "/users/" + driver.getId() + "/kyc-status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rejectRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kycStatus").value("REJECTED"));

        // Then
        User rejectedUser = userRepository.findById(driver.getId()).get();
        assertThat(rejectedUser.getKycStatus()).isEqualTo(KycStatus.REJECTED);
        
        DriverProfile rejectedProfile = driverProfileRepository.findByUserId(driver.getId()).get();
        assertThat(rejectedProfile.getKycNotes()).isEqualTo("License photo is blurry");
    }

    @Test
    void updateUser_PartialUpdate_OnlyUpdatesProvidedFields() throws Exception {
        // Given
        User user = User.builder()
            .phone(TEST_PHONE)
            .fullName("Original Name")
            .email("original@example.com")
            .role(UserRole.RIDER)
            .kycStatus(KycStatus.NONE)
            .isActive(true)
            .build();
        userRepository.save(user);

        String accessToken = generateAccessToken(user.getId().toString(), "RIDER");
        UpdateUserRequest partialUpdate = new UpdateUserRequest("New Name", null);

        // When
        mockMvc.perform(patch(BASE_URL + "/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(partialUpdate)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("New Name"))
            .andExpect(jsonPath("$.email").value("original@example.com"));

        // Then
        User updatedUser = userRepository.findById(user.getId()).get();
        assertThat(updatedUser.getFullName()).isEqualTo("New Name");
        assertThat(updatedUser.getEmail()).isEqualTo("original@example.com");
    }

    @Test
    void encryptionService_EncryptsAndDecryptsSensitiveData() {
        // Given
        String[] sensitiveData = {TEST_BVN, TEST_LICENSE, "ABC-123-XYZ-789"};

        // When & Then
        for (String data : sensitiveData) {
            String encrypted = encryptionService.encrypt(data);
            
            // Encrypted should be different
            assertThat(encrypted).isNotEqualTo(data);
            assertThat(encrypted).isNotNull();
            
            // Multiple encryptions should produce different ciphertexts
            String encrypted2 = encryptionService.encrypt(data);
            assertThat(encrypted2).isNotEqualTo(encrypted);
            
            // Both should decrypt to original
            assertThat(encryptionService.decrypt(encrypted)).isEqualTo(data);
            assertThat(encryptionService.decrypt(encrypted2)).isEqualTo(data);
        }
    }

    private String generateAccessToken(String userId, String role) {
        return JwtUtil.generateToken(userId, role, 3600000, jwtSecret);
    }
}

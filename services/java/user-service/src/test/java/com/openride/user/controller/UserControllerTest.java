package com.openride.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openride.commons.exception.BusinessException;
import com.openride.user.dto.*;
import com.openride.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for UserController.
 * Tests all user management endpoints with MockMvc.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    private static final String BASE_URL = "/api/v1/users";
    private static final String TEST_USER_ID = UUID.randomUUID().toString();
    private static final String TEST_PHONE = "+2348012345678";

    @Test
    void createUser_ValidRequest_ReturnsCreatedUser() throws Exception {
        // Given
        CreateUserRequest request = new CreateUserRequest(TEST_PHONE, "RIDER");
        UserResponse response = UserResponse.builder()
            .userId(TEST_USER_ID)
            .phone(TEST_PHONE)
            .role("RIDER")
            .kycStatus("NONE")
            .isActive(true)
            .build();

        when(userService.createOrGetUser(any(CreateUserRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post(BASE_URL)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(TEST_USER_ID))
            .andExpect(jsonPath("$.phone").value(TEST_PHONE))
            .andExpect(jsonPath("$.role").value("RIDER"));

        verify(userService).createOrGetUser(any(CreateUserRequest.class));
    }

    @Test
    void createUser_InvalidPhone_ReturnsBadRequest() throws Exception {
        // Given
        CreateUserRequest request = new CreateUserRequest("invalid-phone", "RIDER");

        // When & Then
        mockMvc.perform(post(BASE_URL)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.phone").exists());

        verify(userService, never()).createOrGetUser(any());
    }

    @Test
    @WithMockUser(username = TEST_USER_ID)
    void getCurrentUser_Authenticated_ReturnsCurrentUser() throws Exception {
        // Given
        UserResponse response = UserResponse.builder()
            .userId(TEST_USER_ID)
            .phone(TEST_PHONE)
            .fullName("John Doe")
            .role("RIDER")
            .kycStatus("NONE")
            .isActive(true)
            .build();

        when(userService.getUserById(TEST_USER_ID)).thenReturn(response);

        // When & Then
        mockMvc.perform(get(BASE_URL + "/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(TEST_USER_ID))
            .andExpect(jsonPath("$.phone").value(TEST_PHONE))
            .andExpect(jsonPath("$.fullName").value("John Doe"));

        verify(userService).getUserById(TEST_USER_ID);
    }

    @Test
    void getCurrentUser_NotAuthenticated_ReturnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get(BASE_URL + "/me"))
            .andExpect(status().isUnauthorized());

        verify(userService, never()).getUserById(anyString());
    }

    @Test
    @WithMockUser(username = TEST_USER_ID)
    void getUserById_ValidId_ReturnsUser() throws Exception {
        // Given
        String targetUserId = UUID.randomUUID().toString();
        UserResponse response = UserResponse.builder()
            .userId(targetUserId)
            .phone("+2348087654321")
            .role("DRIVER")
            .kycStatus("VERIFIED")
            .isActive(true)
            .build();

        when(userService.getUserById(targetUserId)).thenReturn(response);

        // When & Then
        mockMvc.perform(get(BASE_URL + "/" + targetUserId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(targetUserId))
            .andExpect(jsonPath("$.role").value("DRIVER"));

        verify(userService).getUserById(targetUserId);
    }

    @Test
    @WithMockUser(username = TEST_USER_ID)
    void getUserById_UserNotFound_ReturnsNotFound() throws Exception {
        // Given
        String targetUserId = UUID.randomUUID().toString();
        when(userService.getUserById(targetUserId))
            .thenThrow(new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));

        // When & Then
        mockMvc.perform(get(BASE_URL + "/" + targetUserId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = TEST_USER_ID)
    void updateCurrentUser_ValidRequest_ReturnsUpdatedUser() throws Exception {
        // Given
        UpdateUserRequest request = new UpdateUserRequest("Jane Doe", "jane@example.com");
        UserResponse response = UserResponse.builder()
            .userId(TEST_USER_ID)
            .phone(TEST_PHONE)
            .fullName("Jane Doe")
            .email("jane@example.com")
            .role("RIDER")
            .kycStatus("NONE")
            .isActive(true)
            .build();

        when(userService.updateUser(eq(TEST_USER_ID), any(UpdateUserRequest.class)))
            .thenReturn(response);

        // When & Then
        mockMvc.perform(patch(BASE_URL + "/me")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Jane Doe"))
            .andExpect(jsonPath("$.email").value("jane@example.com"));

        verify(userService).updateUser(eq(TEST_USER_ID), any(UpdateUserRequest.class));
    }

    @Test
    @WithMockUser(username = TEST_USER_ID)
    void updateCurrentUser_InvalidEmail_ReturnsBadRequest() throws Exception {
        // Given
        UpdateUserRequest request = new UpdateUserRequest("Jane Doe", "invalid-email");

        // When & Then
        mockMvc.perform(patch(BASE_URL + "/me")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.email").exists());

        verify(userService, never()).updateUser(anyString(), any());
    }

    @Test
    @WithMockUser(username = TEST_USER_ID)
    void upgradeToDriver_Success_ReturnsDriverUser() throws Exception {
        // Given
        UserResponse response = UserResponse.builder()
            .userId(TEST_USER_ID)
            .phone(TEST_PHONE)
            .fullName("John Doe")
            .role("DRIVER")
            .kycStatus("NONE")
            .isActive(true)
            .driverProfile(DriverProfileResponse.builder()
                .totalTrips(0)
                .totalEarnings("0.00")
                .build())
            .build();

        when(userService.upgradeToDriver(TEST_USER_ID)).thenReturn(response);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/upgrade-to-driver")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("DRIVER"))
            .andExpect(jsonPath("$.driverProfile").exists())
            .andExpect(jsonPath("$.driverProfile.totalTrips").value(0));

        verify(userService).upgradeToDriver(TEST_USER_ID);
    }

    @Test
    @WithMockUser(username = TEST_USER_ID)
    void upgradeToDriver_AlreadyDriver_ReturnsBadRequest() throws Exception {
        // Given
        when(userService.upgradeToDriver(TEST_USER_ID))
            .thenThrow(new BusinessException("ALREADY_DRIVER", "User is already a driver", HttpStatus.BAD_REQUEST));

        // When & Then
        mockMvc.perform(post(BASE_URL + "/upgrade-to-driver")
                .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("ALREADY_DRIVER"));
    }

    @Test
    @WithMockUser(username = TEST_USER_ID)
    void submitKycDocuments_ValidRequest_ReturnsUpdatedUser() throws Exception {
        // Given
        KycDocumentsRequest request = new KycDocumentsRequest(
            "12345678901",
            "LAG-123-ABC",
            "https://example.com/license.jpg",
            "https://example.com/vehicle.jpg"
        );

        UserResponse response = UserResponse.builder()
            .userId(TEST_USER_ID)
            .phone(TEST_PHONE)
            .role("DRIVER")
            .kycStatus("PENDING")
            .isActive(true)
            .driverProfile(DriverProfileResponse.builder()
                .licensePhotoUrl("https://example.com/license.jpg")
                .vehiclePhotoUrl("https://example.com/vehicle.jpg")
                .totalTrips(0)
                .totalEarnings("0.00")
                .build())
            .build();

        when(userService.submitKycDocuments(eq(TEST_USER_ID), any(KycDocumentsRequest.class)))
            .thenReturn(response);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/drivers/kyc-documents")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kycStatus").value("PENDING"))
            .andExpect(jsonPath("$.driverProfile.licensePhotoUrl").value("https://example.com/license.jpg"));

        verify(userService).submitKycDocuments(eq(TEST_USER_ID), any(KycDocumentsRequest.class));
    }

    @Test
    @WithMockUser(username = TEST_USER_ID)
    void submitKycDocuments_InvalidBvn_ReturnsBadRequest() throws Exception {
        // Given
        KycDocumentsRequest request = new KycDocumentsRequest(
            "123", // Invalid BVN (too short)
            "LAG-123-ABC",
            "https://example.com/license.jpg",
            "https://example.com/vehicle.jpg"
        );

        // When & Then
        mockMvc.perform(post(BASE_URL + "/drivers/kyc-documents")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.bvn").exists());

        verify(userService, never()).submitKycDocuments(anyString(), any());
    }

    @Test
    @WithMockUser(username = TEST_USER_ID)
    void submitKycDocuments_NotDriver_ReturnsBadRequest() throws Exception {
        // Given
        KycDocumentsRequest request = new KycDocumentsRequest(
            "12345678901",
            "LAG-123-ABC",
            "https://example.com/license.jpg",
            "https://example.com/vehicle.jpg"
        );

        when(userService.submitKycDocuments(eq(TEST_USER_ID), any(KycDocumentsRequest.class)))
            .thenThrow(new BusinessException("NOT_A_DRIVER", "User is not a driver", HttpStatus.BAD_REQUEST));

        // When & Then
        mockMvc.perform(post(BASE_URL + "/drivers/kyc-documents")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("NOT_A_DRIVER"));
    }

    @Test
    @WithMockUser(username = TEST_USER_ID)
    void submitKycDocuments_NullFields_ReturnsBadRequest() throws Exception {
        // Given
        KycDocumentsRequest request = new KycDocumentsRequest(null, null, null, null);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/drivers/kyc-documents")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.bvn").exists())
            .andExpect(jsonPath("$.errors.licenseNumber").exists())
            .andExpect(jsonPath("$.errors.licensePhotoUrl").exists())
            .andExpect(jsonPath("$.errors.vehiclePhotoUrl").exists());

        verify(userService, never()).submitKycDocuments(anyString(), any());
    }
}

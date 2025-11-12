package com.openride.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openride.commons.exception.BusinessException;
import com.openride.user.dto.UpdateKycStatusRequest;
import com.openride.user.dto.UserResponse;
import com.openride.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AdminController.
 * Tests admin-only endpoints with role-based access control.
 */
@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    private static final String BASE_URL = "/api/v1/admin";
    private static final String TEST_USER_ID = UUID.randomUUID().toString();

    @Test
    @WithMockUser(username = "admin-123", roles = {"ADMIN"})
    void getPendingKycDrivers_AsAdmin_ReturnsDriversList() throws Exception {
        // Given
        List<UserResponse> drivers = Arrays.asList(
            UserResponse.builder()
                .userId(UUID.randomUUID().toString())
                .phone("+2348011111111")
                .fullName("Driver One")
                .role("DRIVER")
                .kycStatus("PENDING")
                .isActive(true)
                .build(),
            UserResponse.builder()
                .userId(UUID.randomUUID().toString())
                .phone("+2348022222222")
                .fullName("Driver Two")
                .role("DRIVER")
                .kycStatus("PENDING")
                .isActive(true)
                .build()
        );

        when(userService.getPendingKycDrivers()).thenReturn(drivers);

        // When & Then
        mockMvc.perform(get(BASE_URL + "/drivers/pending-verification"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].role").value("DRIVER"))
            .andExpect(jsonPath("$[0].kycStatus").value("PENDING"))
            .andExpect(jsonPath("$[1].role").value("DRIVER"))
            .andExpect(jsonPath("$[1].kycStatus").value("PENDING"));

        verify(userService).getPendingKycDrivers();
    }

    @Test
    @WithMockUser(username = "admin-123", roles = {"ADMIN"})
    void getPendingKycDrivers_AsAdmin_NoPendingDrivers_ReturnsEmptyList() throws Exception {
        // Given
        when(userService.getPendingKycDrivers()).thenReturn(Arrays.asList());

        // When & Then
        mockMvc.perform(get(BASE_URL + "/drivers/pending-verification"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));

        verify(userService).getPendingKycDrivers();
    }

    @Test
    @WithMockUser(username = TEST_USER_ID, roles = {"RIDER"})
    void getPendingKycDrivers_AsRider_ReturnsForbidden() throws Exception {
        // When & Then
        mockMvc.perform(get(BASE_URL + "/drivers/pending-verification"))
            .andExpect(status().isForbidden());

        verify(userService, never()).getPendingKycDrivers();
    }

    @Test
    @WithMockUser(username = TEST_USER_ID, roles = {"DRIVER"})
    void getPendingKycDrivers_AsDriver_ReturnsForbidden() throws Exception {
        // When & Then
        mockMvc.perform(get(BASE_URL + "/drivers/pending-verification"))
            .andExpect(status().isForbidden());

        verify(userService, never()).getPendingKycDrivers();
    }

    @Test
    void getPendingKycDrivers_NotAuthenticated_ReturnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get(BASE_URL + "/drivers/pending-verification"))
            .andExpect(status().isUnauthorized());

        verify(userService, never()).getPendingKycDrivers();
    }

    @Test
    @WithMockUser(username = "admin-123", roles = {"ADMIN"})
    void updateKycStatus_AsAdmin_VerifyDriver_ReturnsUpdatedUser() throws Exception {
        // Given
        UpdateKycStatusRequest request = new UpdateKycStatusRequest("VERIFIED", "Documents approved");
        UserResponse response = UserResponse.builder()
            .userId(TEST_USER_ID)
            .phone("+2348012345678")
            .fullName("Driver One")
            .role("DRIVER")
            .kycStatus("VERIFIED")
            .isActive(true)
            .build();

        when(userService.updateKycStatus(eq(TEST_USER_ID), any(UpdateKycStatusRequest.class)))
            .thenReturn(response);

        // When & Then
        mockMvc.perform(patch(BASE_URL + "/users/" + TEST_USER_ID + "/kyc-status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(TEST_USER_ID))
            .andExpect(jsonPath("$.kycStatus").value("VERIFIED"));

        verify(userService).updateKycStatus(eq(TEST_USER_ID), any(UpdateKycStatusRequest.class));
    }

    @Test
    @WithMockUser(username = "admin-123", roles = {"ADMIN"})
    void updateKycStatus_AsAdmin_RejectDriver_ReturnsUpdatedUser() throws Exception {
        // Given
        UpdateKycStatusRequest request = new UpdateKycStatusRequest("REJECTED", "Invalid license photo");
        UserResponse response = UserResponse.builder()
            .userId(TEST_USER_ID)
            .phone("+2348012345678")
            .fullName("Driver One")
            .role("DRIVER")
            .kycStatus("REJECTED")
            .isActive(true)
            .build();

        when(userService.updateKycStatus(eq(TEST_USER_ID), any(UpdateKycStatusRequest.class)))
            .thenReturn(response);

        // When & Then
        mockMvc.perform(patch(BASE_URL + "/users/" + TEST_USER_ID + "/kyc-status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kycStatus").value("REJECTED"));

        verify(userService).updateKycStatus(eq(TEST_USER_ID), any(UpdateKycStatusRequest.class));
    }

    @Test
    @WithMockUser(username = "admin-123", roles = {"ADMIN"})
    void updateKycStatus_AsAdmin_UserNotFound_ReturnsNotFound() throws Exception {
        // Given
        UpdateKycStatusRequest request = new UpdateKycStatusRequest("VERIFIED", "Documents approved");
        
        when(userService.updateKycStatus(eq(TEST_USER_ID), any(UpdateKycStatusRequest.class)))
            .thenThrow(new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));

        // When & Then
        mockMvc.perform(patch(BASE_URL + "/users/" + TEST_USER_ID + "/kyc-status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "admin-123", roles = {"ADMIN"})
    void updateKycStatus_AsAdmin_InvalidStatus_ReturnsBadRequest() throws Exception {
        // Given
        UpdateKycStatusRequest request = new UpdateKycStatusRequest("INVALID_STATUS", "Notes");

        // When & Then
        mockMvc.perform(patch(BASE_URL + "/users/" + TEST_USER_ID + "/kyc-status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        verify(userService, never()).updateKycStatus(anyString(), any());
    }

    @Test
    @WithMockUser(username = "admin-123", roles = {"ADMIN"})
    void updateKycStatus_AsAdmin_NullFields_ReturnsBadRequest() throws Exception {
        // Given
        UpdateKycStatusRequest request = new UpdateKycStatusRequest(null, null);

        // When & Then
        mockMvc.perform(patch(BASE_URL + "/users/" + TEST_USER_ID + "/kyc-status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.status").exists());

        verify(userService, never()).updateKycStatus(anyString(), any());
    }

    @Test
    @WithMockUser(username = TEST_USER_ID, roles = {"RIDER"})
    void updateKycStatus_AsRider_ReturnsForbidden() throws Exception {
        // Given
        UpdateKycStatusRequest request = new UpdateKycStatusRequest("VERIFIED", "Documents approved");

        // When & Then
        mockMvc.perform(patch(BASE_URL + "/users/" + TEST_USER_ID + "/kyc-status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());

        verify(userService, never()).updateKycStatus(anyString(), any());
    }

    @Test
    @WithMockUser(username = TEST_USER_ID, roles = {"DRIVER"})
    void updateKycStatus_AsDriver_ReturnsForbidden() throws Exception {
        // Given
        UpdateKycStatusRequest request = new UpdateKycStatusRequest("VERIFIED", "Documents approved");

        // When & Then
        mockMvc.perform(patch(BASE_URL + "/users/" + TEST_USER_ID + "/kyc-status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());

        verify(userService, never()).updateKycStatus(anyString(), any());
    }

    @Test
    void updateKycStatus_NotAuthenticated_ReturnsUnauthorized() throws Exception {
        // Given
        UpdateKycStatusRequest request = new UpdateKycStatusRequest("VERIFIED", "Documents approved");

        // When & Then
        mockMvc.perform(patch(BASE_URL + "/users/" + TEST_USER_ID + "/kyc-status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());

        verify(userService, never()).updateKycStatus(anyString(), any());
    }
}

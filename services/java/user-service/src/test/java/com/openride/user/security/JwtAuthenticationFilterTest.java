package com.openride.user.security;

import com.openride.commons.util.JwtUtil;
import com.openride.user.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationFilter.
 * Tests JWT token extraction, validation, and SecurityContext setting.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private SecurityProperties.JwtConfig jwtConfig;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final String TEST_JWT_SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm";
    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_ROLE = "RIDER";

    @BeforeEach
    void setUp() {
        when(securityProperties.getJwt()).thenReturn(jwtConfig);
        when(jwtConfig.getSecret()).thenReturn(TEST_JWT_SECRET);
        
        jwtAuthenticationFilter = new JwtAuthenticationFilter(securityProperties);
        
        // Clear SecurityContext before each test
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_ValidToken_SetsAuthentication() throws ServletException, IOException {
        // Given
        String token = JwtUtil.generateToken(TEST_USER_ID, TEST_ROLE, 3600000, TEST_JWT_SECRET);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(TEST_USER_ID);
        assertThat(authentication.getAuthorities())
            .containsExactly(new SimpleGrantedAuthority("ROLE_" + TEST_ROLE));
        assertThat(authentication.isAuthenticated()).isTrue();

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_NoAuthorizationHeader_ContinuesFilterChain() throws ServletException, IOException {
        // Given
        when(request.getHeader("Authorization")).thenReturn(null);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_InvalidTokenFormat_ContinuesFilterChain() throws ServletException, IOException {
        // Given - Missing "Bearer " prefix
        when(request.getHeader("Authorization")).thenReturn("InvalidToken");

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_InvalidToken_ContinuesFilterChain() throws ServletException, IOException {
        // Given
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-jwt-token");

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ExpiredToken_ContinuesFilterChain() throws ServletException, IOException {
        // Given - Token that expired 1 hour ago
        String expiredToken = JwtUtil.generateToken(TEST_USER_ID, TEST_ROLE, -3600000, TEST_JWT_SECRET);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + expiredToken);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_DriverRole_AddsDriverAuthority() throws ServletException, IOException {
        // Given
        String token = JwtUtil.generateToken(TEST_USER_ID, "DRIVER", 3600000, TEST_JWT_SECRET);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
            .containsExactly(new SimpleGrantedAuthority("ROLE_DRIVER"));

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_AdminRole_AddsAdminAuthority() throws ServletException, IOException {
        // Given
        String token = JwtUtil.generateToken(TEST_USER_ID, "ADMIN", 3600000, TEST_JWT_SECRET);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
            .containsExactly(new SimpleGrantedAuthority("ROLE_ADMIN"));

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_TokenWithoutRoleClaim_SetsAuthenticationWithoutAuthorities() throws ServletException, IOException {
        // Given - Token generated without role
        String token = JwtUtil.generateToken(TEST_USER_ID, null, 3600000, TEST_JWT_SECRET);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(TEST_USER_ID);
        assertThat(authentication.getAuthorities()).isEmpty();

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_MultipleCalls_UpdatesSecurityContext() throws ServletException, IOException {
        // Given - First request with RIDER token
        String riderToken = JwtUtil.generateToken("user-1", "RIDER", 3600000, TEST_JWT_SECRET);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + riderToken);

        // When - First call
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication auth1 = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth1.getName()).isEqualTo("user-1");
        assertThat(auth1.getAuthorities()).containsExactly(new SimpleGrantedAuthority("ROLE_RIDER"));

        // Given - Second request with DRIVER token
        SecurityContextHolder.clearContext();
        String driverToken = JwtUtil.generateToken("user-2", "DRIVER", 3600000, TEST_JWT_SECRET);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + driverToken);

        // When - Second call
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication auth2 = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth2.getName()).isEqualTo("user-2");
        assertThat(auth2.getAuthorities()).containsExactly(new SimpleGrantedAuthority("ROLE_DRIVER"));
    }
}

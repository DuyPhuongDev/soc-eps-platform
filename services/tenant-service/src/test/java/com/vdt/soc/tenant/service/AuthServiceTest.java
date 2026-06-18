package com.vdt.soc.tenant.service;

import com.vdt.soc.tenant.dto.LoginRequest;
import com.vdt.soc.tenant.dto.LoginResponse;
import com.vdt.soc.tenant.entity.User;
import com.vdt.soc.common.model.enumeration.UserRole;
import com.vdt.soc.common.security.JwtProperties;
import com.vdt.soc.common.model.enumeration.UserStatus;
import com.vdt.soc.tenant.exception.UnauthorizedException;
import com.vdt.soc.tenant.repository.UserRepository;
import com.vdt.soc.tenant.security.TenantJwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantJwtUtil tenantJwtUtil;
    @Mock private JwtProperties jwtProperties;

    @InjectMocks private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .username("alice")
                .passwordHash("hashed")
                .email("alice@x.com")
                .tenantId(UUID.randomUUID())
                .role(UserRole.TENANT_ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void login_returnsTokenWhenCredentialsValid() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(tenantJwtUtil.generateToken(any(User.class))).thenReturn("jwt-token");
        when(jwtProperties.getExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = authService.login(
                LoginRequest.builder().username("alice").password("secret").build());

        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresInSeconds()).isEqualTo(3600);
        assertThat(response.getRole()).isEqualTo(UserRole.TENANT_ADMIN);
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getTenantId()).isEqualTo(user.getTenantId());
        assertThat(response.getUserId()).isEqualTo(user.getId());
    }

    @Test
    void login_throwsWhenUserNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                LoginRequest.builder().username("ghost").password("x").build()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_throwsWhenPasswordWrong() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                LoginRequest.builder().username("alice").password("wrong").build()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_throwsWhenUserNotActive() {
        user.setStatus(UserStatus.LOCKED);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(
                LoginRequest.builder().username("alice").password("secret").build()))
                .isInstanceOf(UnauthorizedException.class);
    }
}

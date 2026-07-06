package com.vdt.soc.tenant.service;

import com.vdt.soc.common.core.enumeration.UserStatus;
import com.vdt.soc.common.security.JwtProperties;
import com.vdt.soc.tenant.dto.LoginRequest;
import com.vdt.soc.tenant.dto.LoginResponse;
import com.vdt.soc.tenant.dto.UserResponse;
import com.vdt.soc.tenant.entity.User;
import com.vdt.soc.tenant.exception.ResourceNotFoundException;
import com.vdt.soc.tenant.exception.UnauthorizedException;
import com.vdt.soc.tenant.repository.UserRepository;
import com.vdt.soc.tenant.security.TenantJwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantJwtUtil tenantJwtUtil;
    private final JwtProperties jwtProperties;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("User account is not active");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        String token = tenantJwtUtil.generateToken(user);

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInSeconds(jwtProperties.getExpirationSeconds())
                .userId(user.getId())
                .tenantId(user.getTenant() != null ? user.getTenant().getId() : null)
                .role(user.getRole())
                .username(user.getUsername())
                .build();
    }

    public UserResponse getUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        return UserResponse.toResponse(user);
    }
}

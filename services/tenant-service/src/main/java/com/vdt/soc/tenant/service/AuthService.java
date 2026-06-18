package com.vdt.soc.tenant.service;

import com.vdt.soc.tenant.dto.LoginRequest;
import com.vdt.soc.tenant.dto.LoginResponse;
import com.vdt.soc.tenant.entity.User;
import com.vdt.soc.common.model.enumeration.UserStatus;
import com.vdt.soc.tenant.exception.UnauthorizedException;
import com.vdt.soc.tenant.repository.UserRepository;
import com.vdt.soc.common.security.JwtProperties;
import com.vdt.soc.tenant.security.TenantJwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantJwtUtil tenantJwtUtil;
    private final JwtProperties jwtProperties;

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
                .tenantId(user.getTenantId())
                .role(user.getRole())
                .username(user.getUsername())
                .build();
    }
}

package com.vdt.soc.tenant.controller;

import com.vdt.soc.common.security.JwtPrincipal;
import com.vdt.soc.tenant.dto.LoginRequest;
import com.vdt.soc.tenant.dto.LoginResponse;
import com.vdt.soc.tenant.dto.UserResponse;
import com.vdt.soc.tenant.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getLoggedUser(@AuthenticationPrincipal JwtPrincipal jwtPrincipal) {
        return ResponseEntity.ok(authService.getUser(jwtPrincipal.userId()));
    }
}

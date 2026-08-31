package com.hammerly.backend.controller;

import com.hammerly.backend.dto.AuthDtos.LoginRequest;
import com.hammerly.backend.dto.AuthDtos.RegisterRequest;
import com.hammerly.backend.security.AuthRateLimiter;
import com.hammerly.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;

    public AuthController(AuthService authService, AuthRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    @Operation(summary = "User registration", tags = "Auth")
    ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request,
                                                  HttpServletRequest servletRequest) {
        rateLimiter.checkRegistration(servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "User login", tags = "Auth")
    Map<String, Object> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        rateLimiter.checkLogin(servletRequest);
        return authService.login(request);
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout", tags = "Auth", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> logout() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Logout acknowledged; the client must discard its access token");
        return response;
    }
}

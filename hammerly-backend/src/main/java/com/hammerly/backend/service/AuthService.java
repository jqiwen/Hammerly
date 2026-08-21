package com.hammerly.backend.service;

import com.hammerly.backend.dto.AuthDtos.LoginRequest;
import com.hammerly.backend.dto.AuthDtos.RegisterRequest;
import com.hammerly.backend.exception.ApiException;
import com.hammerly.backend.model.User;
import com.hammerly.backend.repository.UserRepository;
import com.hammerly.backend.security.JwtService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    public static final String DEFAULT_AVATAR = "/images/user.jpg";
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Map<String, Object> register(RegisterRequest request) {
        if (request == null || missing(request.email()) || missing(request.password()) ||
            missing(request.firstName()) || missing(request.lastName()) || missing(request.phone())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "All fields are required");
        }
        if (users.findByEmail(request.email()).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email is already in use");
        }
        long id = users.insert(request.email(), passwordEncoder.encode(request.password()), request.firstName(),
            request.lastName(), request.phone(), DEFAULT_AVATAR);
        String token = jwtService.generateToken(id, request.email());
        return authResponse("User registered successfully", token, id, request.firstName(), request.lastName(),
            request.email(), request.phone(), DEFAULT_AVATAR);
    }

    public Map<String, Object> login(LoginRequest request) {
        if (request == null || missing(request.email()) || missing(request.password())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email and password are required");
        }
        User user = users.findByEmail(request.email())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.password())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return authResponse("Login successful", jwtService.generateToken(user.id(), user.email()), user.id(),
            user.firstName(), user.lastName(), user.email(), user.phone(),
            missing(user.avatarImage()) ? DEFAULT_AVATAR : user.avatarImage());
    }

    private Map<String, Object> authResponse(String message, String token, long id, String firstName,
                                             String lastName, String email, String phone, String avatarImage) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("firstName", firstName);
        user.put("lastName", lastName);
        user.put("email", email);
        user.put("phone", phone);
        user.put("avatarImage", avatarImage);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("token", token);
        response.put("user", user);
        return response;
    }

    private boolean missing(String value) {
        return value == null || value.isEmpty();
    }
}

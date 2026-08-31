package com.hammerly.backend.service;

import com.hammerly.backend.dto.AuthDtos.LoginRequest;
import com.hammerly.backend.dto.AuthDtos.RegisterRequest;
import com.hammerly.backend.exception.ApiException;
import com.hammerly.backend.model.User;
import com.hammerly.backend.repository.UserRepository;
import com.hammerly.backend.security.JwtService;
import com.hammerly.backend.util.EmailNormalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    public static final String DEFAULT_AVATAR = "/images/user.jpg";
    private static final String DUMMY_PASSWORD_HASH =
        "$2a$10$I8c3IFAfwOmMZUIufnXwfOQ/p1dDZiULV49ss8xCzKFu7ps.Hxj3.";
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public Map<String, Object> register(RegisterRequest request) {
        if (request == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid request");
        String email = EmailNormalizer.normalize(request.email());
        if (users.findByEmail(email).isPresent()) {
            throw duplicateEmail();
        }
        long id;
        try {
            id = users.insert(email, passwordEncoder.encode(request.password()), request.firstName(),
                request.lastName(), request.phone(), DEFAULT_AVATAR);
        } catch (DuplicateKeyException exception) {
            throw duplicateEmail();
        }
        String token = jwtService.generateToken(id, email);
        return authResponse("User registered successfully", token, id, request.firstName(), request.lastName(),
            email, request.phone(), DEFAULT_AVATAR);
    }

    public Map<String, Object> login(LoginRequest request) {
        if (request == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid request");
        String email = EmailNormalizer.normalize(request.email());
        User user = users.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), user.password())) {
            throw invalidCredentials();
        }
        return authResponse("Login successful", jwtService.generateToken(user.id(), user.email()), user.id(),
            user.firstName(), user.lastName(), user.email(), user.phone(),
            missing(user.avatarImage()) ? DEFAULT_AVATAR : user.avatarImage());
    }

    private ApiException duplicateEmail() {
        return new ApiException(HttpStatus.CONFLICT, "An account with that email already exists");
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
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

package com.hammerly.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hammerly.backend.dto.AuthDtos.LoginRequest;
import com.hammerly.backend.dto.AuthDtos.RegisterRequest;
import com.hammerly.backend.exception.ApiException;
import com.hammerly.backend.model.User;
import com.hammerly.backend.repository.UserRepository;
import com.hammerly.backend.security.JwtService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock JwtService jwtService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, new BCryptPasswordEncoder(10), jwtService);
    }

    @Test
    void registrationNormalizesEmailAndStoresBcryptHash() {
        when(users.findByEmail("mixed@example.com")).thenReturn(Optional.empty());
        when(users.insert(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(7L);
        when(jwtService.generateToken(7, "mixed@example.com")).thenReturn("signed-token");

        Map<String, Object> response = service.register(new RegisterRequest(
            "  Mixed@Example.COM ", "password123", " First ", " Last ", " 555-0100 "));

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(users).insert(org.mockito.ArgumentMatchers.eq("mixed@example.com"), hash.capture(),
            org.mockito.ArgumentMatchers.eq("First"), org.mockito.ArgumentMatchers.eq("Last"),
            org.mockito.ArgumentMatchers.eq("555-0100"), org.mockito.ArgumentMatchers.anyString());
        assertThat(hash.getValue()).startsWith("$2a$10$");
        assertThat(new BCryptPasswordEncoder().matches("password123", hash.getValue())).isTrue();
        assertThat(response.toString()).doesNotContain("password123").doesNotContain(hash.getValue());
    }

    @Test
    void duplicatePrecheckAndDatabaseRaceBothReturnConflict() {
        when(users.findByEmail("duplicate@example.com")).thenReturn(Optional.of(user("duplicate@example.com", "hash")));
        assertConflict(() -> service.register(request("duplicate@example.com")));

        when(users.findByEmail("race@example.com")).thenReturn(Optional.empty());
        when(users.insert(org.mockito.ArgumentMatchers.eq("race@example.com"), anyString(), anyString(), anyString(),
            anyString(), anyString())).thenThrow(new DuplicateKeyException("unique violation"));
        assertConflict(() -> service.register(request("race@example.com")));
    }

    @Test
    void unknownEmailAndWrongPasswordUseSameGenericFailure() {
        when(users.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        assertGenericLoginFailure(() -> service.login(new LoginRequest("unknown@example.com", "password123")));

        when(users.findByEmail("known@example.com")).thenReturn(Optional.of(user(
            "known@example.com", new BCryptPasswordEncoder().encode("correct-password"))));
        assertGenericLoginFailure(() -> service.login(new LoginRequest("known@example.com", "wrong-password")));
    }

    private RegisterRequest request(String email) {
        return new RegisterRequest(email, "password123", "Test", "User", "555-0100");
    }

    private User user(String email, String password) {
        return new User(9, "Test", "User", email, password, "555-0100", "", "2026-01-01T00:00:00Z");
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getMessage()).isEqualTo("An account with that email already exists");
        });
    }

    private void assertGenericLoginFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(exception.getMessage()).isEqualTo("Invalid email or password");
        });
    }
}

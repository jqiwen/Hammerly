package com.hammerly.backend.dto;

import com.hammerly.backend.util.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must be 254 characters or fewer")
        String email,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password,
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be 100 characters or fewer")
        String firstName,
        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must be 100 characters or fewer")
        String lastName,
        @NotBlank(message = "Phone number is required")
        @Size(max = 32, message = "Phone number must be 32 characters or fewer")
        @Pattern(regexp = "^[0-9+() .-]{7,32}$", message = "Phone number contains invalid characters")
        String phone
    ) {
        public RegisterRequest {
            email = EmailNormalizer.normalize(email);
            firstName = trim(firstName);
            lastName = trim(lastName);
            phone = trim(phone);
        }
    }

    public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must be 254 characters or fewer")
        String email,
        @NotBlank(message = "Password is required")
        @Size(max = 128, message = "Password must be 128 characters or fewer")
        String password
    ) {
        public LoginRequest {
            email = EmailNormalizer.normalize(email);
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}

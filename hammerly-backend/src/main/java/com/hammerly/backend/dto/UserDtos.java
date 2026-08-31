package com.hammerly.backend.dto;

import com.hammerly.backend.util.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {
    }

    public record UpdateProfileRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be 100 characters or fewer")
        String firstName,
        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must be 100 characters or fewer")
        String lastName,
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must be 254 characters or fewer")
        String email,
        @Size(max = 32, message = "Phone number must be 32 characters or fewer")
        @Pattern(regexp = "^$|^[0-9+() .-]{7,32}$", message = "Phone number contains invalid characters")
        String phone
    ) {
        public UpdateProfileRequest {
            firstName = trim(firstName);
            lastName = trim(lastName);
            email = EmailNormalizer.normalize(email);
            phone = trim(phone);
        }
    }

    public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        @Size(max = 128, message = "Current password must be 128 characters or fewer")
        String currentPassword,
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 128, message = "New password must be between 8 and 128 characters")
        String newPassword,
        @NotBlank(message = "Password confirmation is required")
        @Size(max = 128, message = "Password confirmation must be 128 characters or fewer")
        String confirmPassword
    ) {
    }

    public record UpdateAvatarRequest(String avatarImage) {
    }

    public record AddPaymentMethodRequest(
        String cardType,
        String cardNumber,
        Integer expiryMonth,
        Integer expiryYear,
        String cardholderName,
        Boolean isDefault,
        String billingAddress,
        String billingCity,
        String billingProvince,
        String billingPostalCode,
        String billingCountry
    ) {
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}

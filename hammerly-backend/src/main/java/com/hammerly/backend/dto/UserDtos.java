package com.hammerly.backend.dto;

public final class UserDtos {
    private UserDtos() {
    }

    public record UpdateProfileRequest(String firstName, String lastName, String email, String phone) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword, String confirmPassword) {
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
}

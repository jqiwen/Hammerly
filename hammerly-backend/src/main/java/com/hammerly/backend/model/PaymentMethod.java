package com.hammerly.backend.model;

public record PaymentMethod(
    long id,
    long userId,
    String cardType,
    String cardNumber,
    int expiryMonth,
    int expiryYear,
    String cardholderName,
    int isDefault,
    String billingAddress,
    String billingCity,
    String billingProvince,
    String billingPostalCode,
    String billingCountry,
    String createdAt
) {
}

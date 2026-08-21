package com.hammerly.backend.model;

public record User(
    long id,
    String firstName,
    String lastName,
    String email,
    String password,
    String phone,
    String avatarImage,
    String createdAt
) {
}

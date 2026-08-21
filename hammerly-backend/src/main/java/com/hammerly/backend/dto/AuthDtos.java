package com.hammerly.backend.dto;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(String email, String password, String firstName, String lastName, String phone) {
    }

    public record LoginRequest(String email, String password) {
    }
}

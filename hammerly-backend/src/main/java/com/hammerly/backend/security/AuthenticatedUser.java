package com.hammerly.backend.security;

public record AuthenticatedUser(long userId, String email) {
}

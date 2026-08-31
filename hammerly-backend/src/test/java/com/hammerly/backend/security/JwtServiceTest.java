package com.hammerly.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTVerificationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "test-jwt-secret-that-is-at-least-thirty-two-characters";

    @Test
    void validTokenContainsRequiredIdentityAndConfiguredExpiry() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtService service = new JwtService(SECRET, Duration.ofMinutes(45), Clock.fixed(now, ZoneOffset.UTC));

        String token = service.generateToken(42, "person@example.com");
        AuthenticatedUser principal = service.verify(token);

        assertThat(principal.userId()).isEqualTo(42);
        assertThat(principal.email()).isEqualTo("person@example.com");
        assertThat(JWT.decode(token).getExpiresAtAsInstant()).isEqualTo(now.plus(Duration.ofMinutes(45)));
    }

    @Test
    void expiredTokenIsRejected() {
        Instant past = Instant.now().minus(Duration.ofHours(2));
        JwtService service = new JwtService(SECRET, Duration.ofMinutes(1), Clock.fixed(past, ZoneOffset.UTC));

        String token = service.generateToken(42, "person@example.com");

        assertThatThrownBy(() -> service.verify(token)).isInstanceOf(JWTVerificationException.class);
    }

    @Test
    void modifiedSignatureIsRejected() {
        JwtService service = new JwtService(SECRET, Duration.ofMinutes(45), Clock.systemUTC());
        String token = service.generateToken(42, "person@example.com");
        char replacement = token.endsWith("a") ? 'b' : 'a';
        String modified = token.substring(0, token.length() - 1) + replacement;

        assertThatThrownBy(() -> service.verify(modified)).isInstanceOf(JWTVerificationException.class);
    }
}

package com.hammerly.backend.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String ISSUER = "hammerly-core";
    private static final String AUDIENCE = "hammerly-web";
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final Duration timeToLive;
    private final Clock clock;

    @Autowired
    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${hammerly.auth.jwt-ttl}") Duration timeToLive) {
        this(secret, timeToLive, Clock.systemUTC());
    }

    JwtService(String secret, Duration timeToLive, Clock clock) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 characters");
        }
        if (timeToLive == null || timeToLive.isNegative() || timeToLive.isZero()) {
            throw new IllegalArgumentException("HAMMERLY_AUTH_JWT_TTL must be positive");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).withIssuer(ISSUER).withAudience(AUDIENCE).build();
        this.timeToLive = timeToLive;
        this.clock = clock;
    }

    public String generateToken(long userId, String email) {
        if (userId <= 0 || email == null || email.isBlank()) {
            throw new IllegalArgumentException("JWT identity claims are required");
        }
        Instant now = clock.instant();
        return JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plus(timeToLive)))
            .sign(algorithm);
    }

    public AuthenticatedUser verify(String token) {
        DecodedJWT decoded = verifier.verify(token);
        Long userId = decoded.getClaim("userId").asLong();
        String email = decoded.getClaim("email").asString();
        if (userId == null || userId <= 0 || email == null || email.isBlank()) {
            throw new IllegalArgumentException("Required JWT claims are missing");
        }
        return new AuthenticatedUser(userId, email);
    }
}

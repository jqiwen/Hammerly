package com.hammerly.backend.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String ISSUER = "hammerly-core";
    private static final String AUDIENCE = "hammerly-web";
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final long expirationMs;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-ms}") long expirationMs) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 characters");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).withIssuer(ISSUER).withAudience(AUDIENCE).build();
        this.expirationMs = expirationMs;
    }

    public String generateToken(long userId, String email) {
        Instant now = Instant.now();
        return JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plusMillis(expirationMs)))
            .sign(algorithm);
    }

    public AuthenticatedUser verify(String token) {
        DecodedJWT decoded = verifier.verify(token);
        Long userId = decoded.getClaim("userId").asLong();
        String email = decoded.getClaim("email").asString();
        if (userId == null || email == null) {
            throw new IllegalArgumentException("Required JWT claims are missing");
        }
        return new AuthenticatedUser(userId, email);
    }
}

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
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final long expirationMs;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-ms}") long expirationMs) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).build();
        this.expirationMs = expirationMs;
    }

    public String generateToken(long userId, String email) {
        Instant now = Instant.now();
        return JWT.create()
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

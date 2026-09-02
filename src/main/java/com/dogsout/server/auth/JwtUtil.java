package com.dogsout.server.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-after}")
    private long refreshAfter;

    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(signingKey())
                .compact();
    }

    public String extractEmail(String token) {
        return claims(token).getSubject();
    }

    public Date extractIssuedAt(String token) {
        return claims(token).getIssuedAt();
    }

    /**
     * Whether a still-valid token is old enough to be worth replacing.
     *
     * <p>Sessions are renewed as they are used rather than expiring on a fixed
     * schedule, so someone who opens the app regularly stays signed in and only a
     * genuinely dormant account has to log in again.
     */
    public boolean isDueForRenewal(String token) {
        try {
            Date issuedAt = claims(token).getIssuedAt();
            return issuedAt != null && System.currentTimeMillis() - issuedAt.getTime() > refreshAfter;
        } catch (JwtException e) {
            return false;
        }
    }

    public boolean isValid(String token) {
        try {
            claims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    private Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
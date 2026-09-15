package com.retailpos.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String DEFAULT_DEV_SECRET = "PubExchangeSuperSecretKeyForJWTAuth2026EnterpriseProductionEngine!";

    @Value("${jwt.secret:PubExchangeSuperSecretKeyForJWTAuth2026EnterpriseProductionEngine!}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms:86400000}")
    private long jwtExpirationInMs;

    @Value("${jwt.refresh-expiration-ms:604800000}")
    private long jwtRefreshExpirationInMs;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @PostConstruct
    public void validateSecretConfiguration() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException("CRITICAL SECURITY FAILURE: JWT secret is not configured. Supply a secure JWT_SECRET environment variable.");
        }

        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("CRITICAL SECURITY FAILURE: JWT secret must be at least 256 bits (32 bytes) long. Current length: " + secretBytes.length + " bytes.");
        }

        boolean isProd = activeProfile != null && activeProfile.toLowerCase().contains("prod");
        if (isProd && DEFAULT_DEV_SECRET.equals(jwtSecret.trim())) {
            throw new IllegalStateException("CRITICAL SECURITY VIOLATION: Default development JWT secret cannot be used in production profile. Supply a secure, unique JWT_SECRET environment variable.");
        }

        log.info("JWT Token Provider initialized securely (Key length: {} bytes, Active profile: {})", secretBytes.length, activeProfile);
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("type", "ACCESS")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtRefreshExpirationInMs);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("type", "REFRESH")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public String getRoleFromJWT(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("role", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String getTokenTypeFromJWT(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("type", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}

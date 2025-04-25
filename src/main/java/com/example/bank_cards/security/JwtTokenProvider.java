package com.example.bank_cards.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecretString;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    private SecretKey jwtSecretKey;

    @PostConstruct
    protected void init() {
        if (!StringUtils.hasText(jwtSecretString)) {
            log.error("JWT secret key is not configured. Please set 'jwt.secret' property.");
            throw new IllegalStateException("JWT secret key ('jwt.secret') is missing or empty in configuration.");
        }

        byte[] keyBytes = jwtSecretString.getBytes(StandardCharsets.UTF_8);

        final int minKeyLengthBytes = 64;
        if (keyBytes.length < minKeyLengthBytes) {
            log.warn("WARNING: Configured JWT secret key ('jwt.secret') is shorter than the recommended {} bytes for "
                    + "HS512. " +
                    "Consider using a longer, securely generated key.", minKeyLengthBytes);
        }

        this.jwtSecretKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JwtTokenProvider initialized. JWT Expiration set to {} ms.", jwtExpirationMs);
    }


    @NonNull
    public String generateToken(@NonNull String email) {
        if (!StringUtils.hasText(email)) {
            log.error("Attempted to generate JWT for a blank phone number.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number cannot be blank for token "
                    + "generation.");
        }

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(jwtSecretKey, SignatureAlgorithm.HS512)
                .compact();
    }


    @NonNull
    public String getEmailFromToken(@NonNull String token) {
        if (!StringUtils.hasText(token)) {
            log.error("Attempted to extract email from a blank token.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cannot extract claims from a blank token.");
        }
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(jwtSecretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    ;

            String email = claims.getSubject();
            if (!StringUtils.hasText(email)) {
                log.warn("JWT subject claim (email) is missing or empty in the provided token.");
                throw new MalformedJwtException("JWT subject claim is missing or empty.");
            }
            return email;
        }
        catch (JwtException | IllegalArgumentException ex) {
            log.error("Failed to parse JWT and extract email: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token: " + ex.getMessage(), ex);
        }
    }

    public boolean validateToken(@Nullable String token) {
        if (!StringUtils.hasText(token)) {
            log.debug("Validation failed: Token string was null or empty.");
            return false;
        }
        try {
            Jwts.parserBuilder()
                    .setSigningKey(jwtSecretKey)
                    .build()
                    .parseClaimsJws(token)
            ;
            return true;
        }
        catch (SecurityException ex) {
            log.warn("Validation failed: Invalid JWT signature. Message: {}", ex.getMessage());
        }
        catch (MalformedJwtException ex) {
            log.warn("Validation failed: Invalid JWT token structure. Message: {}", ex.getMessage());
        }
        catch (ExpiredJwtException ex) {
            log.warn("Validation failed: Expired JWT token. Message: {}", ex.getMessage());
        }
        catch (UnsupportedJwtException ex) {
            log.warn("Validation failed: Unsupported JWT token type. Message: {}", ex.getMessage());
        }
        catch (IllegalArgumentException ex) {
            log.warn(
                    "Validation failed: JWT claims string was empty or invalid argument provided. Message: {}",
                    ex.getMessage()
            );
        }
        catch (JwtException ex) {
            log.error("Validation failed: Unexpected JWT exception. Message: {}", ex.getMessage(), ex);
        }
        return false;
    }
}

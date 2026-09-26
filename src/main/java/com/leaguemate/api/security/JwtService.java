package com.leaguemate.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    // HS256 richiede una chiave di almeno 256 bit.
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey signInKey;
    private final long jwtExpiration;

    public JwtService(@Value("${jwt.secret}") String secretKey,
                      @Value("${jwt.expiration}") long jwtExpiration) {
        // La chiave viene decodificata una sola volta: un secret mancante o troppo
        // corto blocca l'avvio invece di emergere alla prima richiesta.
        this.signInKey = buildKey(secretKey);
        this.jwtExpiration = jwtExpiration;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(signInKey, Jwts.SIG.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        // Un solo parse: firma e scadenza sono già verificate dal parser,
        // che lancia ExpiredJwtException su un token scaduto.
        final Claims claims = extractAllClaims(token);
        return claims.getSubject().equals(userDetails.getUsername())
                && claims.getExpiration().after(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signInKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static SecretKey buildKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("jwt.secret non configurato: impostare la variabile JWT_SECRET");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secretKey);
        } catch (DecodingException ex) {
            throw new IllegalStateException("jwt.secret deve essere una stringa Base64 valida", ex);
        }

        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret troppo corto: servono almeno " + MIN_KEY_BYTES + " byte (256 bit)");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

package com.collect.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Component
public class JwtUtils {

    @Value("${jwt.secret:crawler-platform-jwt-secret-key-must-be-long-enough-256}")
    private String secret;

    @Value("${jwt.expire:7200}")
    private long expireSeconds;

    private SecretKey getKey() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(LoginUser user) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireSeconds * 1000);
        String jti = java.util.UUID.randomUUID().toString();
        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(user.getUserId()))
                .claim("username", user.getUsername())
                .claim("nickname", user.getNickname())
                .claim("role", user.getRoleName())
                .claim("roleCode", user.getRoleCode())
                .claim("permissions", user.getPermissions() != null ? user.getPermissions() : java.util.Collections.emptySet())
                .issuedAt(now)
                .expiration(exp)
                .signWith(getKey())
                .compact();
    }

    public String getTokenId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getId();
        } catch (Exception e) {
            return null;
        }
    }

    public LoginUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        LoginUser user = new LoginUser();
        user.setUserId(Long.valueOf(claims.getSubject()));
        user.setUsername(claims.get("username", String.class));
        user.setNickname(claims.get("nickname", String.class));
        user.setRoleName(claims.get("role", String.class));
        user.setRoleCode(claims.get("roleCode", String.class));
        Object perms = claims.get("permissions");
        if (perms instanceof List<?> list) {
            user.setPermissions(list.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet()));
        }
        return user;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public static void main(String[] args) {
        System.out.println("JwtUtils ready");
    }
}

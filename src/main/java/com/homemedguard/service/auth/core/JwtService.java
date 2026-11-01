package com.homemedguard.service.auth.core;

import com.homemedguard.config.AuthProperties;
import com.homemedguard.service.auth.entity.SysUserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/** access token 是无状态的短期凭证；长期凭证走 refresh token 落库并可吊销。 */
@Service
public class JwtService {

    private final SecretKey key;
    private final AuthProperties properties;

    public JwtService(AuthProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(secretBytes(properties.getJwtSecret()));
    }

    public String issueAccessToken(SysUserEntity user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("uname", user.getUsername())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTokenTtl())))
                .signWith(key)
                .compact();
    }

    public Optional<LoginUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new LoginUser(Long.valueOf(claims.getSubject()),
                    claims.get("uname", String.class), claims.get("role", String.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static byte[] secretBytes(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.auth.jwt-secret 未配置：请设置环境变量 HMG_JWT_SECRET（base64，至少 32 字节）");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException ex) {
            decoded = secret.trim().getBytes(StandardCharsets.UTF_8);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("app.auth.jwt-secret 太短，HS256 至少需要 32 字节");
        }
        return decoded;
    }
}

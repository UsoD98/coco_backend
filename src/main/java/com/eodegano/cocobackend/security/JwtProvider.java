package com.eodegano.cocobackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    private SecretKey secretKey;

    // Bean 초기화 후 SecretKey 생성 (매 요청마다 생성하지 않도록)
    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ───────────────────────────────────────────────
    // 토큰 생성
    // ───────────────────────────────────────────────

    public String generateAccessToken(Long userId, String email, String role) {
        return buildToken(userId, email, role, accessTokenExpiry);
    }

    public String generateRefreshToken(Long userId, String email, String role) {
        return buildToken(userId, email, role, refreshTokenExpiry);
    }

    private String buildToken(Long userId, String email, String role, long expiry) {
        Date now = new Date();
        Date expiredAt = new Date(now.getTime() + expiry);

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiredAt)
                .signWith(secretKey)
                .compact();
    }

    // ───────────────────────────────────────────────
    // 토큰 파싱 & 검증
    // ───────────────────────────────────────────────

    // 유효한 토큰인지 검증 (필터에서 사용)
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("유효하지 않은 JWT 토큰: {}", e.getMessage());
        }
        return false;
    }

    // 토큰에서 Authentication 객체 추출 (SecurityContext 등록용)
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        String email = claims.getSubject();
        String role = claims.get("role", String.class);

        // "USER" → "ROLE_USER" 로 변환하여 Security 권한 등록
        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + role));

        return new UsernamePasswordAuthenticationToken(email, null, authorities);
    }

    // 토큰에서 이메일 추출
    public String getEmail(String token) {
        return parseClaims(token).getSubject();
    }

    // RefreshToken 만료시간 LocalDateTime 반환 (DB 저장용)
    public LocalDateTime getRefreshTokenExpiresAt() {
        return LocalDateTime.now().plusSeconds(refreshTokenExpiry / 1000);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

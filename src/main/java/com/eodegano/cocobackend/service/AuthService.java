package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.domain.RefreshToken;
import com.eodegano.cocobackend.domain.User;
import com.eodegano.cocobackend.dto.AuthTokenResult;
import com.eodegano.cocobackend.dto.LoginRequestDto;
import com.eodegano.cocobackend.repository.RefreshTokenRepository;
import com.eodegano.cocobackend.repository.UserRepository;
import com.eodegano.cocobackend.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    // ───────────────────────────────────────────────
    // 로그인
    // ───────────────────────────────────────────────
    @Transactional
    public AuthTokenResult login(LoginRequestDto request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (AuthenticationException e) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId(), user.getEmail(), user.getRole());

        refreshTokenRepository.findByUserAndProvider(user, "local")
                .ifPresentOrElse(
                        existing -> existing.rotate(refreshToken, jwtProvider.getRefreshTokenExpiresAt()),
                        () -> refreshTokenRepository.save(
                                RefreshToken.builder()
                                        .user(user)
                                        .token(refreshToken)
                                        .provider("local")
                                        .expiresAt(jwtProvider.getRefreshTokenExpiresAt())
                                        .build()
                        )
                );

        return new AuthTokenResult(accessToken, refreshToken);
    }

    // ───────────────────────────────────────────────
    // 로그아웃
    // ───────────────────────────────────────────────
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByToken(refreshToken);
    }

    // ───────────────────────────────────────────────
    // AccessToken 재발급 (RefreshToken 로테이션)
    // ───────────────────────────────────────────────
    @Transactional
    public AuthTokenResult reissue(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 RefreshToken입니다.");
        }

        RefreshToken savedToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "존재하지 않는 RefreshToken입니다."));

        if (savedToken.isExpired()) {
            refreshTokenRepository.delete(savedToken);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "만료된 RefreshToken입니다. 다시 로그인해 주세요.");
        }

        User user = savedToken.getUser();
        String newAccessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String newRefreshToken = jwtProvider.generateRefreshToken(user.getId(), user.getEmail(), user.getRole());

        savedToken.rotate(newRefreshToken, jwtProvider.getRefreshTokenExpiresAt());

        return new AuthTokenResult(newAccessToken, newRefreshToken);
    }
}

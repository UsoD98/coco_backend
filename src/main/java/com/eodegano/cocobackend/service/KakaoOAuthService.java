package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.client.KakaoApiClient;
import com.eodegano.cocobackend.client.KakaoApiClient.KakaoUserInfo;
import com.eodegano.cocobackend.domain.RefreshToken;
import com.eodegano.cocobackend.domain.User;
import com.eodegano.cocobackend.dto.AuthTokenResult;
import com.eodegano.cocobackend.repository.RefreshTokenRepository;
import com.eodegano.cocobackend.repository.UserRepository;
import com.eodegano.cocobackend.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoOAuthService {

    private static final String PROVIDER = "kakao";

    private final KakaoApiClient kakaoApiClient;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;

    /**
     * 카카오 AccessToken으로 자체 JWT 세션을 발급한다.
     * - 기존 카카오 유저 → 로그인 처리
     * - 신규 유저 → 자동 가입 후 로그인 처리
     */
    @Transactional
    public AuthTokenResult kakaoLogin(String kakaoAccessToken) {
        KakaoUserInfo userInfo = kakaoApiClient.getUserInfo(kakaoAccessToken);
        String providerId = String.valueOf(userInfo.getId());

        User user = userRepository.findByProviderAndProviderId(PROVIDER, providerId)
                .orElseGet(() -> registerKakaoUser(userInfo, providerId));

        return issueJwtTokens(user);
    }

    private User registerKakaoUser(KakaoUserInfo userInfo, String providerId) {
        String nickname = userInfo.getNickname();

        if (!userInfo.hasTrustedEmail()) {
            // 이메일이 없거나 카카오가 인증하지 않은 이메일 → 기존 계정 조회/연동 시도 자체를 하지 않음
            // (이메일 문자열 일치만으로 자동 연동 시 계정 탈취 가능)
            // userInfo.getEmail()은 미인증 상태여도 카카오가 준 실제 이메일을 그대로 반환하므로 쓰면 안 됨
            // (uq_email 충돌 위험) - 항상 합성 이메일로 별도 신규 계정을 생성한다.
            String syntheticEmail = "kakao_" + providerId + "@kakao.local";
            log.info("카카오 이메일 미신뢰(미제공/미인증) - 신규 계정 생성: providerId={}", providerId);
            return userRepository.save(User.ofKakao(syntheticEmail, nickname, providerId));
        }

        String email = userInfo.getEmail();
        return userRepository.findByEmail(email)
                .map(existing -> {
                    log.info("기존 로컬 계정에 카카오 연결: email={}", email);
                    existing.linkKakao(providerId);
                    return existing;
                })
                .orElseGet(() -> {
                    log.info("카카오 신규 회원 가입: providerId={}", providerId);
                    return userRepository.save(User.ofKakao(email, nickname, providerId));
                });
    }

    private AuthTokenResult issueJwtTokens(User user) {
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId(), user.getEmail(), user.getRole());

        refreshTokenRepository.findByUserAndProvider(user, PROVIDER)
                .ifPresentOrElse(
                        existing -> existing.rotate(refreshToken, jwtProvider.getRefreshTokenExpiresAt()),
                        () -> refreshTokenRepository.save(
                                RefreshToken.builder()
                                        .user(user)
                                        .token(refreshToken)
                                        .provider(PROVIDER)
                                        .expiresAt(jwtProvider.getRefreshTokenExpiresAt())
                                        .build()
                        )
                );

        return new AuthTokenResult(accessToken, refreshToken);
    }
}

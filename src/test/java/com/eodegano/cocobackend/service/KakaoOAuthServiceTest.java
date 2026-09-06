package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.client.KakaoApiClient;
import com.eodegano.cocobackend.client.KakaoApiClient.KakaoUserInfo;
import com.eodegano.cocobackend.domain.RefreshToken;
import com.eodegano.cocobackend.domain.User;
import com.eodegano.cocobackend.dto.AuthTokenResult;
import com.eodegano.cocobackend.repository.RefreshTokenRepository;
import com.eodegano.cocobackend.repository.UserRepository;
import com.eodegano.cocobackend.security.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KakaoOAuthServiceTest {

    @InjectMocks
    private KakaoOAuthService kakaoOAuthService;

    @Mock private KakaoApiClient kakaoApiClient;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtProvider jwtProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String KAKAO_ACCESS_TOKEN = "kakao.access.token";
    private static final String ACCESS_TOKEN = "mock.access.token";
    private static final String REFRESH_TOKEN = "mock.refresh.token";

    @BeforeEach
    void setUp() {
        lenientJwtStubs();
    }

    private void lenientJwtStubs() {
        lenient().when(jwtProvider.generateAccessToken(any(), any())).thenReturn(ACCESS_TOKEN);
        lenient().when(jwtProvider.generateRefreshToken(any(), any())).thenReturn(REFRESH_TOKEN);
        lenient().when(jwtProvider.getRefreshTokenExpiresAt()).thenReturn(LocalDateTime.now().plusDays(7));
        lenient().when(refreshTokenRepository.findByUserAndProvider(any(User.class), eq("kakao")))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("이미 카카오 연동된 계정 - 이메일 재검증 없이 바로 로그인")
    void kakaoLogin_existingLinkedAccount() {
        User linkedUser = User.builder().email("user@example.com").nickname("유저").role("USER").build();
        KakaoUserInfo userInfo = mock(KakaoUserInfo.class);
        given(userInfo.getId()).willReturn(555L);
        given(kakaoApiClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(userInfo);
        given(userRepository.findByProviderAndProviderId("kakao", "555"))
                .willReturn(Optional.of(linkedUser));

        AuthTokenResult result = kakaoOAuthService.kakaoLogin(KAKAO_ACCESS_TOKEN);

        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        verify(userRepository, never()).findByEmailAndDeletedAtIsNull(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("인증된 이메일 + 기존 로컬 계정 존재 - 자동 연동됨 (정상 유스케이스)")
    void kakaoLogin_trustedEmail_linksExistingLocalAccount() {
        User localUser = spy(User.builder().email("local@example.com").nickname("로컬유저").role("USER").build());
        KakaoUserInfo userInfo = mock(KakaoUserInfo.class);
        given(userInfo.getId()).willReturn(111L);
        given(userInfo.hasTrustedEmail()).willReturn(true);
        given(userInfo.getEmail()).willReturn("local@example.com");
        given(userInfo.getNickname()).willReturn("카카오닉네임");

        given(kakaoApiClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(userInfo);
        given(userRepository.findByProviderAndProviderId("kakao", "111")).willReturn(Optional.empty());
        given(userRepository.findByEmailAndDeletedAtIsNull("local@example.com"))
                .willReturn(Optional.of(localUser));

        AuthTokenResult result = kakaoOAuthService.kakaoLogin(KAKAO_ACCESS_TOKEN);

        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        verify(localUser).linkKakao("111");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("인증된 이메일 + 기존 로컬 계정 없음 - 신규 가입")
    void kakaoLogin_trustedEmail_registersNewUser() {
        KakaoUserInfo userInfo = mock(KakaoUserInfo.class);
        given(userInfo.getId()).willReturn(222L);
        given(userInfo.hasTrustedEmail()).willReturn(true);
        given(userInfo.getEmail()).willReturn("new@example.com");
        given(userInfo.getNickname()).willReturn("신규유저");

        given(kakaoApiClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(userInfo);
        given(userRepository.findByProviderAndProviderId("kakao", "222")).willReturn(Optional.empty());
        given(userRepository.findByEmailAndDeletedAtIsNull("new@example.com")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        kakaoOAuthService.kakaoLogin(KAKAO_ACCESS_TOKEN);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(captor.getValue().getProviderId()).isEqualTo("222");
    }

    @Test
    @DisplayName("보안: 미인증 이메일이 피해자 로컬 계정과 일치해도 조회/연동하지 않고 별도 계정 생성")
    void kakaoLogin_untrustedEmail_doesNotLinkExistingAccountEvenIfEmailMatches() {
        User victimAccount = spy(User.builder().email("victim@example.com").nickname("피해자").role("USER").build());
        KakaoUserInfo attackerInfo = mock(KakaoUserInfo.class);
        given(attackerInfo.getId()).willReturn(999L);
        given(attackerInfo.hasTrustedEmail()).willReturn(false);
        given(attackerInfo.getNickname()).willReturn("공격자닉네임");

        given(kakaoApiClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(attackerInfo);
        given(userRepository.findByProviderAndProviderId("kakao", "999")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        kakaoOAuthService.kakaoLogin(KAKAO_ACCESS_TOKEN);

        // 피해자 계정 조회 자체가 발생하면 안 됨
        verify(userRepository, never()).findByEmailAndDeletedAtIsNull(any());
        verify(victimAccount, never()).linkKakao(any());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getProviderId()).isEqualTo("999");
        assertThat(captor.getValue().getEmail()).isEqualTo("kakao_999@kakao.local");
    }

    @Test
    @DisplayName("이메일 미제공(동의 안 함) - 합성 이메일로 신규 가입 (기존 동작 회귀 테스트)")
    void kakaoLogin_noEmailConsent_registersWithSyntheticEmail() {
        KakaoUserInfo userInfo = mock(KakaoUserInfo.class);
        given(userInfo.getId()).willReturn(777L);
        given(userInfo.hasTrustedEmail()).willReturn(false);
        given(userInfo.getNickname()).willReturn("카카오유저");

        given(kakaoApiClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(userInfo);
        given(userRepository.findByProviderAndProviderId("kakao", "777")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        kakaoOAuthService.kakaoLogin(KAKAO_ACCESS_TOKEN);

        verify(userRepository, never()).findByEmailAndDeletedAtIsNull(any());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("kakao_777@kakao.local");
    }

    // ───────────────────────────────────────────────
    // 실패 케이스
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("실패 - 유효하지 않은/만료된 카카오 AccessToken이면 예외 전파, DB 접근 없음")
    void kakaoLogin_invalidKakaoAccessToken_propagatesExceptionWithoutTouchingDb() {
        given(kakaoApiClient.getUserInfo(KAKAO_ACCESS_TOKEN))
                .willThrow(new IllegalArgumentException("유효하지 않은 카카오 AccessToken입니다."));

        assertThatThrownBy(() -> kakaoOAuthService.kakaoLogin(KAKAO_ACCESS_TOKEN))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(userRepository, refreshTokenRepository, jwtProvider);
    }

    // ───────────────────────────────────────────────
    // End-to-End: 실제 카카오 API 응답 형태(JSON)를 그대로 역직렬화해 검증
    // (KakaoApiClient의 파싱 로직 ↔ KakaoOAuthService의 사용 방식이 실제로 맞물리는지 확인)
    // ───────────────────────────────────────────────

    private KakaoUserInfo parseKakaoResponse(String json) throws Exception {
        return objectMapper.readValue(json, KakaoUserInfo.class);
    }

    @Test
    @DisplayName("E2E 성공 - 실제 인증된 카카오 응답으로 기존 로컬 계정에 정상 연동")
    void e2e_verifiedEmail_linksExistingAccount() throws Exception {
        User localUser = spy(User.builder().email("real@example.com").nickname("로컬유저").role("USER").build());
        KakaoUserInfo realUserInfo = parseKakaoResponse("""
                {
                  "id": 1001,
                  "kakao_account": {
                    "email": "real@example.com",
                    "is_email_valid": true,
                    "is_email_verified": true,
                    "profile": { "nickname": "진짜유저" }
                  }
                }
                """);

        given(kakaoApiClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(realUserInfo);
        given(userRepository.findByProviderAndProviderId("kakao", "1001")).willReturn(Optional.empty());
        given(userRepository.findByEmailAndDeletedAtIsNull("real@example.com"))
                .willReturn(Optional.of(localUser));

        AuthTokenResult result = kakaoOAuthService.kakaoLogin(KAKAO_ACCESS_TOKEN);

        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        verify(localUser).linkKakao("1001");
    }

    @Test
    @DisplayName("E2E 공격 방어 - is_email_verified 필드가 응답에 아예 없는 실전 공격 페이로드 → 피해자 계정 미조회, 격리된 신규 계정만 생성")
    void e2e_attackPayloadMissingVerificationFields_isolatesAttacker() throws Exception {
        // 공격자가 자기 카카오 계정에 피해자 이메일만 등록하고, 인증 절차는 거치지 않은 실제 응답 형태
        User victimAccount = spy(User.builder().email("victim@example.com").nickname("피해자").role("USER").build());
        KakaoUserInfo attackPayload = parseKakaoResponse("""
                {
                  "id": 2002,
                  "kakao_account": {
                    "email": "victim@example.com",
                    "profile": { "nickname": "공격자" }
                  }
                }
                """);

        given(kakaoApiClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(attackPayload);
        given(userRepository.findByProviderAndProviderId("kakao", "2002")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        AuthTokenResult result = kakaoOAuthService.kakaoLogin(KAKAO_ACCESS_TOKEN);

        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        verify(userRepository, never()).findByEmailAndDeletedAtIsNull(any());
        verify(victimAccount, never()).linkKakao(any());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("kakao_2002@kakao.local");
        assertThat(captor.getValue().getProviderId()).isEqualTo("2002");
    }

    @Test
    @DisplayName("E2E 공격 방어 - is_email_verified: false를 명시적으로 응답받은 경우도 동일하게 격리")
    void e2e_attackPayloadExplicitlyUnverified_isolatesAttacker() throws Exception {
        User victimAccount = spy(User.builder().email("victim2@example.com").nickname("피해자2").role("USER").build());
        KakaoUserInfo attackPayload = parseKakaoResponse("""
                {
                  "id": 3003,
                  "kakao_account": {
                    "email": "victim2@example.com",
                    "is_email_valid": true,
                    "is_email_verified": false,
                    "profile": { "nickname": "공격자2" }
                  }
                }
                """);

        given(kakaoApiClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(attackPayload);
        given(userRepository.findByProviderAndProviderId("kakao", "3003")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        kakaoOAuthService.kakaoLogin(KAKAO_ACCESS_TOKEN);

        verify(userRepository, never()).findByEmailAndDeletedAtIsNull(any());
        verify(victimAccount, never()).linkKakao(any());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("kakao_3003@kakao.local");
    }
}

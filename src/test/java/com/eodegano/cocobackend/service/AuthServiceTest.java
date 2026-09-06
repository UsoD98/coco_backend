package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.domain.RefreshToken;
import com.eodegano.cocobackend.domain.User;
import com.eodegano.cocobackend.dto.AuthTokenResult;
import com.eodegano.cocobackend.dto.LoginRequestDto;
import com.eodegano.cocobackend.repository.RefreshTokenRepository;
import com.eodegano.cocobackend.repository.UserRepository;
import com.eodegano.cocobackend.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtProvider jwtProvider;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    private User mockUser;
    private static final Long USER_ID = 1L;
    private static final String EMAIL = "test@test.com";
    private static final String PASSWORD = "password123!";
    private static final String ACCESS_TOKEN = "mock.access.token";
    private static final String REFRESH_TOKEN = "mock.refresh.token";

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .nickname("테스터")
                .password("encodedPassword")
                .role("USER")
                .build();
    }

    // ───────────────────────────────────────────────
    // 로그인
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("로그인 성공 - AccessToken, RefreshToken 반환")
    void loginSuccess() {
        Authentication mockAuth = new UsernamePasswordAuthenticationToken(EMAIL, null);
        given(authenticationManager.authenticate(any())).willReturn(mockAuth);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(mockUser));
        given(jwtProvider.generateAccessToken(USER_ID, EMAIL, "USER")).willReturn(ACCESS_TOKEN);
        given(jwtProvider.generateRefreshToken(USER_ID, EMAIL, "USER")).willReturn(REFRESH_TOKEN);
        given(jwtProvider.getRefreshTokenExpiresAt()).willReturn(LocalDateTime.now().plusDays(7));
        given(refreshTokenRepository.findByUserAndProvider(mockUser, "local")).willReturn(Optional.empty());

        LoginRequestDto request = mock(LoginRequestDto.class);
        given(request.getEmail()).willReturn(EMAIL);
        given(request.getPassword()).willReturn(PASSWORD);

        AuthTokenResult result = authService.login(request);

        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("로그인 성공 - 기존 RefreshToken 있으면 rotate")
    void loginSuccessWithExistingRefreshToken() {
        Authentication mockAuth = new UsernamePasswordAuthenticationToken(EMAIL, null);
        RefreshToken existingToken = mock(RefreshToken.class);

        given(authenticationManager.authenticate(any())).willReturn(mockAuth);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(mockUser));
        given(jwtProvider.generateAccessToken(USER_ID, EMAIL, "USER")).willReturn(ACCESS_TOKEN);
        given(jwtProvider.generateRefreshToken(USER_ID, EMAIL, "USER")).willReturn(REFRESH_TOKEN);
        given(jwtProvider.getRefreshTokenExpiresAt()).willReturn(LocalDateTime.now().plusDays(7));
        given(refreshTokenRepository.findByUserAndProvider(mockUser, "local")).willReturn(Optional.of(existingToken));

        LoginRequestDto request = mock(LoginRequestDto.class);
        given(request.getEmail()).willReturn(EMAIL);
        given(request.getPassword()).willReturn(PASSWORD);

        authService.login(request);

        verify(existingToken).rotate(eq(REFRESH_TOKEN), any(LocalDateTime.class));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("로그인 실패 - 이메일/비밀번호 불일치")
    void loginFailWithBadCredentials() {
        given(authenticationManager.authenticate(any()))
                .willThrow(new BadCredentialsException("bad credentials"));

        LoginRequestDto request = mock(LoginRequestDto.class);
        given(request.getEmail()).willReturn(EMAIL);
        given(request.getPassword()).willReturn("wrongPassword");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    // ───────────────────────────────────────────────
    // 로그아웃
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("로그아웃 성공 - RefreshToken DB에서 삭제")
    void logoutSuccess() {
        authService.logout(REFRESH_TOKEN);

        verify(refreshTokenRepository).deleteByToken(REFRESH_TOKEN);
    }

    // ───────────────────────────────────────────────
    // 토큰 재발급
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("토큰 재발급 성공 - 새 AccessToken, RefreshToken 반환")
    void reissueSuccess() {
        RefreshToken savedToken = mock(RefreshToken.class);
        given(savedToken.isExpired()).willReturn(false);
        given(savedToken.getUser()).willReturn(mockUser);

        given(jwtProvider.validateToken(REFRESH_TOKEN)).willReturn(true);
        given(refreshTokenRepository.findByToken(REFRESH_TOKEN)).willReturn(Optional.of(savedToken));
        given(jwtProvider.generateAccessToken(USER_ID, EMAIL, "USER")).willReturn("new.access.token");
        given(jwtProvider.generateRefreshToken(USER_ID, EMAIL, "USER")).willReturn("new.refresh.token");
        given(jwtProvider.getRefreshTokenExpiresAt()).willReturn(LocalDateTime.now().plusDays(7));

        AuthTokenResult result = authService.reissue(REFRESH_TOKEN);

        assertThat(result.accessToken()).isEqualTo("new.access.token");
        assertThat(result.refreshToken()).isEqualTo("new.refresh.token");
        verify(savedToken).rotate(eq("new.refresh.token"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 유효하지 않은 RefreshToken → 401")
    void reissueFailWithInvalidToken() {
        given(jwtProvider.validateToken(REFRESH_TOKEN)).willReturn(false);

        assertThatThrownBy(() -> authService.reissue(REFRESH_TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("토큰 재발급 실패 - DB에 없는 RefreshToken → 401")
    void reissueFailWithNotFoundToken() {
        given(jwtProvider.validateToken(REFRESH_TOKEN)).willReturn(true);
        given(refreshTokenRepository.findByToken(REFRESH_TOKEN)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissue(REFRESH_TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 만료된 RefreshToken → 401 + DB 삭제")
    void reissueFailWithExpiredToken() {
        RefreshToken expiredToken = mock(RefreshToken.class);
        given(expiredToken.isExpired()).willReturn(true);

        given(jwtProvider.validateToken(REFRESH_TOKEN)).willReturn(true);
        given(refreshTokenRepository.findByToken(REFRESH_TOKEN)).willReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.reissue(REFRESH_TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(refreshTokenRepository).delete(expiredToken);
    }
}

package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.domain.User;
import com.eodegano.cocobackend.dto.*;
import com.eodegano.cocobackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private User mockUser;
    private static final String EMAIL = "test@test.com";
    private static final String OTHER_EMAIL = "other@test.com";
    private static final String ENCODED_PASSWORD = "encodedPassword";

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .email(EMAIL)
                .nickname("테스터")
                .password(ENCODED_PASSWORD)
                .role("USER")
                .build();
    }

    // ───────────────────────────────────────────────
    // 회원가입
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("회원가입 성공")
    void joinSuccess() {
        given(userRepository.existsByEmailAndDeletedAtIsNull(EMAIL)).willReturn(false);
        given(userRepository.findByEmailAndDeletedAtIsNotNull(EMAIL)).willReturn(Optional.empty());
        given(passwordEncoder.encode(anyString())).willReturn(ENCODED_PASSWORD);
        given(userRepository.save(any(User.class))).willReturn(mockUser);

        UserJoinRequestDto request = mock(UserJoinRequestDto.class);
        given(request.getEmail()).willReturn(EMAIL);
        given(request.getNickname()).willReturn("테스터");
        given(request.getPassword()).willReturn("password123!");

        userService.join(request);

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 실패 - 이미 사용 중인 이메일")
    void joinFailWithDuplicateEmail() {
        given(userRepository.existsByEmailAndDeletedAtIsNull(EMAIL)).willReturn(true);

        UserJoinRequestDto request = mock(UserJoinRequestDto.class);
        given(request.getEmail()).willReturn(EMAIL);

        assertThatThrownBy(() -> userService.join(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용 중인 이메일입니다.");
    }

    @Test
    @DisplayName("재가입 성공 - 탈퇴한 이력이 있는 이메일")
    void joinSuccessWithRejoin() {
        given(userRepository.existsByEmailAndDeletedAtIsNull(EMAIL)).willReturn(false);
        given(userRepository.findByEmailAndDeletedAtIsNotNull(EMAIL)).willReturn(Optional.of(mockUser));
        given(passwordEncoder.encode(anyString())).willReturn(ENCODED_PASSWORD);

        UserJoinRequestDto request = mock(UserJoinRequestDto.class);
        given(request.getEmail()).willReturn(EMAIL);
        given(request.getNickname()).willReturn("테스터");
        given(request.getPassword()).willReturn("password123!");

        userService.join(request);

        verify(userRepository, never()).save(any()); // 재가입은 save 안 함 (dirty checking)
    }

    // ───────────────────────────────────────────────
    // 비밀번호 변경
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("비밀번호 변경 성공")
    void updatePasswordSuccess() {
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(mockUser));
        given(passwordEncoder.matches("currentPassword", ENCODED_PASSWORD)).willReturn(true);
        given(passwordEncoder.matches("newPassword123!", ENCODED_PASSWORD)).willReturn(false);
        given(passwordEncoder.encode("newPassword123!")).willReturn("newEncodedPassword");

        UserUpdatePasswordRequestDto request = mock(UserUpdatePasswordRequestDto.class);
        given(request.getCurrentPassword()).willReturn("currentPassword");
        given(request.getNewPassword()).willReturn("newPassword123!");

        userService.updatePassword(1L, request, EMAIL, false);

        verify(passwordEncoder).encode("newPassword123!");
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 소셜 로그인 유저")
    void updatePasswordFailForSocialUser() {
        User socialUser = User.builder()
                .email(EMAIL)
                .nickname("카카오유저")
                .password(null) // 소셜 로그인은 password null
                .role("USER")
                .build();

        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(socialUser));

        UserUpdatePasswordRequestDto request = mock(UserUpdatePasswordRequestDto.class);

        assertThatThrownBy(() -> userService.updatePassword(1L, request, EMAIL, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("소셜 로그인 유저는 비밀번호를 변경할 수 없습니다.");
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 현재 비밀번호 불일치")
    void updatePasswordFailWithWrongCurrentPassword() {
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(mockUser));
        given(passwordEncoder.matches("wrongPassword", ENCODED_PASSWORD)).willReturn(false);

        UserUpdatePasswordRequestDto request = mock(UserUpdatePasswordRequestDto.class);
        given(request.getCurrentPassword()).willReturn("wrongPassword");

        assertThatThrownBy(() -> userService.updatePassword(1L, request, EMAIL, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("현재 비밀번호가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 새 비밀번호가 현재와 동일")
    void updatePasswordFailWithSamePassword() {
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(mockUser));
        given(passwordEncoder.matches("currentPassword", ENCODED_PASSWORD)).willReturn(true);
        given(passwordEncoder.matches("currentPassword", ENCODED_PASSWORD)).willReturn(true);

        UserUpdatePasswordRequestDto request = mock(UserUpdatePasswordRequestDto.class);
        given(request.getCurrentPassword()).willReturn("currentPassword");
        given(request.getNewPassword()).willReturn("currentPassword");

        assertThatThrownBy(() -> userService.updatePassword(1L, request, EMAIL, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
    }

    // ───────────────────────────────────────────────
    // 회원 탈퇴
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("회원 탈퇴 성공 - deletedAt 설정")
    void deleteUserSuccess() {
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(mockUser));

        userService.deleteUser(1L, EMAIL, false);

        verify(userRepository).findByIdAndDeletedAtIsNull(1L);
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 존재하지 않는 유저")
    void deleteUserFailWithNotFound() {
        given(userRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(999L, EMAIL, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 유저입니다.");
    }

    // ───────────────────────────────────────────────
    // 회원 정보 조회
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("회원 정보 조회 성공 - 본인 조회")
    void getUserSuccess() {
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(mockUser));

        UserInfoResponseDto result = userService.getUser(1L, EMAIL, false);

        assertThat(result.getEmail()).isEqualTo(EMAIL);
    }

    // ───────────────────────────────────────────────
    // 닉네임 수정
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("닉네임 수정 성공 - 본인 수정")
    void updateNicknameSuccess() {
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(mockUser));

        UserUpdateNicknameRequestDto request = mock(UserUpdateNicknameRequestDto.class);
        given(request.getNickname()).willReturn("새닉네임");

        userService.updateNickname(1L, request, EMAIL, false);

        assertThat(mockUser.getNickname()).isEqualTo("새닉네임");
    }

    // ───────────────────────────────────────────────
    // 보안: 본인 확인(IDOR) — 성공/실패/공격 시나리오
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("보안 실패 - 다른 유저 이메일로 회원 정보 조회 시도 시 403(AccessDeniedException)")
    void getUserFailWithOtherUsersEmail() {
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(mockUser));

        assertThatThrownBy(() -> userService.getUser(1L, OTHER_EMAIL, false))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("본인 정보만 조회할 수 있습니다.");
    }

    @Test
    @DisplayName("공격 방어 - 다른 유저 닉네임을 임의로 변경 시도 시 차단되고 실제 값은 변경되지 않음")
    void updateNicknameAttackAttemptBlocked() {
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(mockUser));

        UserUpdateNicknameRequestDto request = mock(UserUpdateNicknameRequestDto.class);

        assertThatThrownBy(() -> userService.updateNickname(1L, request, OTHER_EMAIL, false))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("본인 닉네임만 수정할 수 있습니다.");

        // 공격 시도가 실제로 데이터에 반영되지 않았는지 확인
        assertThat(mockUser.getNickname()).isEqualTo("테스터");
    }

    @Test
    @DisplayName("공격 방어 - 다른 유저 계정을 임의로 탈퇴시키는 시도 차단, deletedAt 변경 없음")
    void deleteUserAttackAttemptBlocked() {
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(mockUser));

        assertThatThrownBy(() -> userService.deleteUser(1L, OTHER_EMAIL, false))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("본인 계정만 탈퇴할 수 있습니다.");

        assertThat(mockUser.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("ADMIN 예외 - 다른 유저 이메일이어도 ADMIN이면 조회/수정/탈퇴 허용")
    void adminBypassesOwnershipCheck() {
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(mockUser));

        UserInfoResponseDto result = userService.getUser(1L, OTHER_EMAIL, true);

        assertThat(result.getEmail()).isEqualTo(EMAIL);
    }
}

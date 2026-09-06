package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.domain.User;
import com.eodegano.cocobackend.dto.*;
import com.eodegano.cocobackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserJoinResponseDto join(UserJoinRequestDto request) {
        // 활성 유저 이메일 중복 체크
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 비밀번호 인코딩
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 탈퇴한 이력이 있는 이메일인지 확인 (재가입)
        Optional<User> deletedUser = userRepository.findByEmailAndDeletedAtIsNotNull(request.getEmail());
        if (deletedUser.isPresent()) {
            deletedUser.get().rejoin(request.getNickname(), encodedPassword);
            return new UserJoinResponseDto(deletedUser.get());
        }

        // 신규 가입
        User user = User.builder()
                .email(request.getEmail())
                .nickname(request.getNickname())
                .password(encodedPassword)
                .build();

        return new UserJoinResponseDto(userRepository.save(user));
    }

    @Override
    public UserInfoResponseDto getUser(Long userId, String requesterEmail, boolean isAdmin) {
        User user = findActiveUser(userId);
        verifyOwnership(user, requesterEmail, isAdmin, "본인 정보만 조회할 수 있습니다.");
        return new UserInfoResponseDto(user);
    }

    @Override
    @Transactional
    public void updateNickname(Long userId, UserUpdateNicknameRequestDto request, String requesterEmail, boolean isAdmin) {
        User user = findActiveUser(userId);
        verifyOwnership(user, requesterEmail, isAdmin, "본인 닉네임만 수정할 수 있습니다.");
        user.updateNickname(request.getNickname());
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, UserUpdatePasswordRequestDto request, String requesterEmail, boolean isAdmin) {
        User user = findActiveUser(userId);
        verifyOwnership(user, requesterEmail, isAdmin, "본인 비밀번호만 변경할 수 있습니다.");

        if (user.getPassword() == null) {
            throw new IllegalArgumentException("소셜 로그인 유저는 비밀번호를 변경할 수 없습니다.");
        }

        // 현재 비밀번호 검증
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        // 새 비밀번호가 현재 비밀번호와 동일한지 검증
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    @Override
    @Transactional
    public void deleteUser(Long userId, String requesterEmail, boolean isAdmin) {
        User user = findActiveUser(userId);
        verifyOwnership(user, requesterEmail, isAdmin, "본인 계정만 탈퇴할 수 있습니다.");
        user.delete();
    }

    // 활성 유저 조회 공통 메서드 (삭제된 유저 제외)
    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
    }

    // 본인 확인 (ADMIN은 예외 허용) — JWT에서 검증된 이메일과 대상 유저 이메일을 대조
    private void verifyOwnership(User user, String requesterEmail, boolean isAdmin, String message) {
        if (!isAdmin && !user.getEmail().equals(requesterEmail)) {
            throw new AccessDeniedException(message);
        }
    }
}

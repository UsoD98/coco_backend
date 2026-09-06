package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.domain.User;
import com.eodegano.cocobackend.dto.*;
import com.eodegano.cocobackend.repository.PoiRatingRepository;
import com.eodegano.cocobackend.repository.RefreshTokenRepository;
import com.eodegano.cocobackend.repository.TourCourseUserDefinedRepository;
import com.eodegano.cocobackend.repository.UserPoiLikeRepository;
import com.eodegano.cocobackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserPoiLikeRepository userPoiLikeRepository;
    private final PoiRatingRepository poiRatingRepository;
    private final TourCourseUserDefinedRepository tourCourseUserDefinedRepository;

    @Override
    @Transactional
    public UserJoinResponseDto join(UserJoinRequestDto request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .email(request.getEmail())
                .nickname(request.getNickname())
                .password(encodedPassword)
                .build();

        return new UserJoinResponseDto(userRepository.save(user));
    }

    @Override
    public UserInfoResponseDto getUser(Long userId, String requesterEmail, boolean isAdmin) {
        User user = findUser(userId);
        verifyOwnership(user, requesterEmail, isAdmin, "본인 정보만 조회할 수 있습니다.");
        return new UserInfoResponseDto(user);
    }

    @Override
    @Transactional
    public void updateNickname(Long userId, UserUpdateNicknameRequestDto request, String requesterEmail, boolean isAdmin) {
        User user = findUser(userId);
        verifyOwnership(user, requesterEmail, isAdmin, "본인 닉네임만 수정할 수 있습니다.");
        user.updateNickname(request.getNickname());
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, UserUpdatePasswordRequestDto request, String requesterEmail, boolean isAdmin) {
        User user = findUser(userId);
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
        User user = findUser(userId);
        verifyOwnership(user, requesterEmail, isAdmin, "본인 계정만 탈퇴할 수 있습니다.");

        // FK로 user를 참조하는 데이터 먼저 정리 후 실제 DB 행 삭제 (하드 딜리트)
        refreshTokenRepository.deleteByUser(user);

        List<Long> likedContentIds = userPoiLikeRepository.findContentIdsByUserId(userId);
        if (!likedContentIds.isEmpty()) {
            poiRatingRepository.decrementLikesForContentIds(likedContentIds);
        }
        userPoiLikeRepository.deleteByUserId(userId);

        tourCourseUserDefinedRepository.unassignAllByUserId(userId);
        userRepository.delete(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
    }

    // 본인 확인 (ADMIN은 예외 허용) — JWT에서 검증된 이메일과 대상 유저 이메일을 대조
    private void verifyOwnership(User user, String requesterEmail, boolean isAdmin, String message) {
        if (!isAdmin && !user.getEmail().equals(requesterEmail)) {
            throw new AccessDeniedException(message);
        }
    }
}

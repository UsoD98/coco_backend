package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.dto.*;

public interface UserService {

    UserJoinResponseDto join(UserJoinRequestDto request);

    UserInfoResponseDto getUser(Long userId, String requesterEmail, boolean isAdmin);

    void updateNickname(Long userId, UserUpdateNicknameRequestDto request, String requesterEmail, boolean isAdmin);

    void updatePassword(Long userId, UserUpdatePasswordRequestDto request, String requesterEmail, boolean isAdmin);

    void deleteUser(Long userId, String requesterEmail, boolean isAdmin);
}

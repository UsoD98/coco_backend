package com.eodegano.cocobackend.controller;

import com.eodegano.cocobackend.dto.*;
import com.eodegano.cocobackend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Slf4j
@Validated
public class UserController {

    private final UserService userService;

    // 회원가입
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<UserJoinResponseDto>> join(@RequestBody @Valid UserJoinRequestDto request) {
        return ResponseEntity.ok(ApiResponse.ok("회원가입에 성공했습니다.", userService.join(request)));
    }

    // 회원정보 조회
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserInfoResponseDto>> getUser(
            @PathVariable Long userId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("회원 정보를 조회했습니다.",
                userService.getUser(userId, authentication.getName(), isAdmin(authentication))));
    }

    // 닉네임 수정
    @PatchMapping("/{userId}/nickname")
    public ResponseEntity<ApiResponse<Void>> updateNickname(
            @PathVariable Long userId,
            @RequestBody @Valid UserUpdateNicknameRequestDto request,
            Authentication authentication) {
        userService.updateNickname(userId, request, authentication.getName(), isAdmin(authentication));
        return ResponseEntity.ok(ApiResponse.ok("닉네임이 수정되었습니다."));
    }

    // 비밀번호 수정
    @PatchMapping("/{userId}/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @PathVariable Long userId,
            @RequestBody @Valid UserUpdatePasswordRequestDto request,
            Authentication authentication) {
        userService.updatePassword(userId, request, authentication.getName(), isAdmin(authentication));
        return ResponseEntity.ok(ApiResponse.ok("비밀번호가 변경되었습니다."));
    }

    // 회원탈퇴 (소프트 삭제)
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable Long userId,
            Authentication authentication) {
        userService.deleteUser(userId, authentication.getName(), isAdmin(authentication));
        return ResponseEntity.ok(ApiResponse.ok("회원 탈퇴가 완료되었습니다."));
    }

    // ADMIN 역할은 본인 확인 없이 다른 유저 정보에 접근 가능
    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}

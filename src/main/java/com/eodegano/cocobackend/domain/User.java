package com.eodegano.cocobackend.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.AccessLevel;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", nullable = false, length = 200)
    private String email;

    @Column(name = "nickname", nullable = false, length = 100)
    private String nickname;

    @Column(name = "provider", length = 20)
    private String provider;

    @Column(name = "provider_id", length = 200)
    private String providerId;

    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private String role = "USER";

    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "gender")
    private Byte gender;

    @Column(name = "travel_type", length = 50)
    private String travelType;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ───────────────────────────────────────────────
    // 팩토리 메서드
    // ───────────────────────────────────────────────

    public static User ofKakao(String email, String nickname, String providerId) {
        return User.builder()
                .email(email)
                .nickname(nickname)
                .provider("kakao")
                .providerId(providerId)
                .role("USER")
                .build();
    }

    // ───────────────────────────────────────────────
    // 비즈니스 메서드
    // ───────────────────────────────────────────────

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /** 로컬 계정에 카카오 providerId를 연결한다. */
    public void linkKakao(String providerId) {
        this.provider = "kakao";
        this.providerId = providerId;
    }
}

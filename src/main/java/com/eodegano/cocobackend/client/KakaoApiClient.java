package com.eodegano.cocobackend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class KakaoApiClient {

    private static final String KAKAO_USER_ME_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;

    public KakaoApiClient() {
        this.restClient = RestClient.builder().build();
    }

    /**
     * 카카오 AccessToken으로 카카오 유저 정보를 조회한다.
     * 401: 토큰 만료/위변조 → IllegalArgumentException
     */
    public KakaoUserInfo getUserInfo(String kakaoAccessToken) {
        return restClient.get()
                .uri(KAKAO_USER_ME_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    log.warn("카카오 토큰 검증 실패: status={}", response.getStatusCode());
                    throw new IllegalArgumentException("유효하지 않은 카카오 AccessToken입니다.");
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                    log.error("카카오 API 서버 오류: status={}", response.getStatusCode());
                    throw new RuntimeException("카카오 서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
                })
                .body(KakaoUserInfo.class);
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KakaoUserInfo {

        private Long id;

        @JsonProperty("kakao_account")
        private KakaoAccount kakaoAccount;

        public String getEmail() {
            if (kakaoAccount == null || kakaoAccount.getEmail() == null) {
                return "kakao_" + id + "@kakao.local";
            }
            return kakaoAccount.getEmail();
        }

        /**
         * 카카오가 이메일 소유권을 실제로 인증했는지 여부.
         * 이 값이 true일 때만 이메일 일치를 근거로 기존 로컬 계정과 자동 연동해도 안전하다.
         */
        public boolean hasTrustedEmail() {
            return kakaoAccount != null
                    && kakaoAccount.getEmail() != null
                    && Boolean.TRUE.equals(kakaoAccount.getIsEmailValid())
                    && Boolean.TRUE.equals(kakaoAccount.getIsEmailVerified());
        }

        public String getNickname() {
            if (kakaoAccount == null || kakaoAccount.getProfile() == null
                    || kakaoAccount.getProfile().getNickname() == null) {
                return "카카오유저";
            }
            return kakaoAccount.getProfile().getNickname();
        }

        @Getter
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class KakaoAccount {
            private String email;
            private Profile profile;

            @JsonProperty("is_email_valid")
            private Boolean isEmailValid;

            @JsonProperty("is_email_verified")
            private Boolean isEmailVerified;
        }

        @Getter
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Profile {
            private String nickname;
        }
    }
}

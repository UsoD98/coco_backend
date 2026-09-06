package com.eodegano.cocobackend.client;

import com.eodegano.cocobackend.client.KakaoApiClient.KakaoUserInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoApiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("hasTrustedEmail - is_email_valid/is_email_verified 모두 true면 신뢰함")
    void hasTrustedEmail_true() throws Exception {
        String json = """
                {
                  "id": 123,
                  "kakao_account": {
                    "email": "victim@example.com",
                    "is_email_valid": true,
                    "is_email_verified": true,
                    "profile": { "nickname": "김재원" }
                  }
                }
                """;

        KakaoUserInfo userInfo = objectMapper.readValue(json, KakaoUserInfo.class);

        assertThat(userInfo.hasTrustedEmail()).isTrue();
        assertThat(userInfo.getEmail()).isEqualTo("victim@example.com");
    }

    @Test
    @DisplayName("hasTrustedEmail - is_email_verified가 false면 신뢰 안 함")
    void hasTrustedEmail_falseWhenNotVerified() throws Exception {
        String json = """
                {
                  "id": 123,
                  "kakao_account": {
                    "email": "victim@example.com",
                    "is_email_valid": true,
                    "is_email_verified": false
                  }
                }
                """;

        KakaoUserInfo userInfo = objectMapper.readValue(json, KakaoUserInfo.class);

        assertThat(userInfo.hasTrustedEmail()).isFalse();
    }

    @Test
    @DisplayName("hasTrustedEmail - is_email_valid가 false면 신뢰 안 함")
    void hasTrustedEmail_falseWhenNotValid() throws Exception {
        String json = """
                {
                  "id": 123,
                  "kakao_account": {
                    "email": "victim@example.com",
                    "is_email_valid": false,
                    "is_email_verified": true
                  }
                }
                """;

        KakaoUserInfo userInfo = objectMapper.readValue(json, KakaoUserInfo.class);

        assertThat(userInfo.hasTrustedEmail()).isFalse();
    }

    @Test
    @DisplayName("hasTrustedEmail - 필드 자체가 응답에 없으면(null) 신뢰 안 함")
    void hasTrustedEmail_falseWhenFieldsMissing() throws Exception {
        String json = """
                {
                  "id": 123,
                  "kakao_account": {
                    "email": "victim@example.com"
                  }
                }
                """;

        KakaoUserInfo userInfo = objectMapper.readValue(json, KakaoUserInfo.class);

        assertThat(userInfo.hasTrustedEmail()).isFalse();
    }

    @Test
    @DisplayName("hasTrustedEmail - 이메일 동의 자체를 안 하면 신뢰 안 함, 합성 이메일 반환")
    void hasTrustedEmail_falseWhenNoEmailConsent() throws Exception {
        String json = """
                {
                  "id": 999
                }
                """;

        KakaoUserInfo userInfo = objectMapper.readValue(json, KakaoUserInfo.class);

        assertThat(userInfo.hasTrustedEmail()).isFalse();
        assertThat(userInfo.getEmail()).isEqualTo("kakao_999@kakao.local");
    }
}

package com.eodegano.cocobackend.dto;

import lombok.Builder;
import lombok.Getter;

/** AI 코스 생성 실패(HTTP 499) 응답의 data 필드 — 프론트가 원인을 구분해 처리할 수 있도록 제공 */
@Getter
@Builder
public class AiErrorDetail {
    private String errorCode;
    private boolean retryable;
    private String finishReason;
}

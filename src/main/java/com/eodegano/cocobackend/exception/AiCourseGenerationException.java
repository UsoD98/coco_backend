package com.eodegano.cocobackend.exception;

import lombok.Getter;

/**
 * AI(Groq) 코스 생성 플로우(호출·파싱·응답 검증)에서 발생하는 에러 전용 예외.
 * {@link GlobalExceptionHandler}에서 HTTP 499로 매핑되어, 일반 400/500과 구분해
 * 프론트가 "AI 생성 실패" 케이스를 별도로 처리할 수 있도록 한다.
 */
@Getter
public class AiCourseGenerationException extends RuntimeException {

    public enum ErrorCode {
        /** Groq API rate limit(429) 재시도 소진 */
        RATE_LIMITED,
        /** Groq API 호출 자체 실패 (네트워크·5xx 등, 재시도 소진) */
        API_CALL_FAILED,
        /** Groq가 choices/content 없이 빈 응답을 반환 */
        EMPTY_RESPONSE,
        /** AI 응답 content를 JSON으로 파싱하는 데 실패 (reasoning 토큰 소모로 인한 truncation 등) */
        RESPONSE_PARSE_FAILED,
        /** AI가 생성한 일정이 스키마·날짜 범위·contentId 등 검증 규칙을 위반 */
        RESPONSE_VALIDATION_FAILED,
    }

    private final ErrorCode errorCode;
    private final boolean retryable;
    private final String finishReason;

    public AiCourseGenerationException(ErrorCode errorCode, String message, boolean retryable) {
        this(errorCode, message, retryable, null, null);
    }

    public AiCourseGenerationException(ErrorCode errorCode, String message, boolean retryable, Throwable cause) {
        this(errorCode, message, retryable, null, cause);
    }

    public AiCourseGenerationException(ErrorCode errorCode, String message, boolean retryable,
                                        String finishReason, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.finishReason = finishReason;
    }
}

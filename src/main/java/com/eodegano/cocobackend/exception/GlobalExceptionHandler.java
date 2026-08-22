package com.eodegano.cocobackend.exception;

import com.eodegano.cocobackend.dto.AiErrorDetail;
import com.eodegano.cocobackend.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        log.error("Validation error: {}", ex.getMessage());

        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.of(400, msg, null));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex) {
        log.error("Missing request parameter: {}", ex.getMessage());

        String msg = "필수 파라미터 '" + ex.getParameterName() + "'가 누락되었습니다.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.of(400, msg, null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Response status error: {}", ex.getMessage());
        int status = ex.getStatusCode().value();
        return ResponseEntity.status(status)
                .body(ApiResponse.of(status, ex.getReason() != null ? ex.getReason() : "요청을 처리할 수 없습니다.", null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("Business logic error: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.of(400, ex.getMessage(), null));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoSuchElementException(NoSuchElementException ex) {
        log.error("Resource not found: {}", ex.getMessage());

        String msg = ex.getMessage() != null ? ex.getMessage() : "요청한 리소스를 찾을 수 없습니다.";
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.of(404, msg, null));
    }

    // 컨트롤러/서비스에서 직접 던진 AccessDeniedException은 DispatcherServlet 내에서
    // 여기로 먼저 해석되어, 필터 체인의 ExceptionTranslationFilter(JwtAccessDeniedHandler)까지 전파되지 않는다.
    // 그 핸들러가 반환하는 응답과 동일한 형태(403)로 맞춰준다.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.error("Access denied: {}", ex.getMessage());

        String msg = ex.getMessage() != null ? ex.getMessage() : "접근 권한이 없습니다.";
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.of(403, msg, null));
    }

    // AI(Groq) 코스 생성 플로우(호출·파싱·응답 검증) 전용 에러 — 프론트가 일반 400/500과
    // 구분해서 "AI 생성 실패" UI를 별도로 처리할 수 있도록 표준 코드가 아닌 499로 응답한다.
    @ExceptionHandler(AiCourseGenerationException.class)
    public ResponseEntity<ApiResponse<AiErrorDetail>> handleAiCourseGenerationException(AiCourseGenerationException ex) {
        log.error("AI course generation error [{}] retryable={}: {}",
                ex.getErrorCode(), ex.isRetryable(), ex.getMessage(), ex);

        AiErrorDetail detail = AiErrorDetail.builder()
                .errorCode(ex.getErrorCode().name())
                .retryable(ex.isRetryable())
                .finishReason(ex.getFinishReason())
                .build();

        return ResponseEntity.status(499)
                .body(ApiResponse.of(499, ex.getMessage(), detail));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime error: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.of(500, "서버 오류가 발생했습니다", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.of(500, "예상치 못한 오류가 발생했습니다", null));
    }
}

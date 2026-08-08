package com.eodegano.cocobackend.exception;

import com.eodegano.cocobackend.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

package com.eodegano.cocobackend.exception;

/**
 * TourAPI 호출이 재시도({@code MAX_API_RETRIES})를 모두 소진하고도 실패했을 때 던진다.
 * 정상 응답이 "0건"인 경우와 구분하기 위한 전용 예외 — {@link GlobalExceptionHandler}에서
 * 503으로 매핑되어, 클라이언트가 "결과 없음"과 "TourAPI 장애"를 구분할 수 있게 한다.
 */
public class TourApiUnavailableException extends RuntimeException {

    public TourApiUnavailableException(String message) {
        super(message);
    }

    public TourApiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

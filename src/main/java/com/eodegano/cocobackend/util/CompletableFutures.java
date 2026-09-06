package com.eodegano.cocobackend.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * {@link CompletableFuture} 관련 공용 헬퍼.
 *
 * <h2>배경 — 왜 가상 스레드 + CompletableFuture를 쓰는가</h2>
 * 이 프로젝트에서 여러 개의 TourAPI 호출(블로킹 HTTP 요청)을 "동시에" 보내야 하는
 * 곳들(예: {@code TourApiClient.areaBasedListAllByTypes}, {@code TourCourseServiceImpl.buildDetailMap})은
 * 아래 패턴을 씁니다.
 * <pre>{@code
 * ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
 * List<CompletableFuture<R>> futures = items.stream()
 *         .map(item -> CompletableFuture.supplyAsync(() -> blockingCall(item), executor))
 *         .toList();
 * List<R> results = futures.stream().map(CompletableFutures::joinUnwrapped).toList();
 * }</pre>
 * <ul>
 *   <li><b>가상 스레드(Virtual Thread, Java 21+)</b>: OS 스레드를 하나씩 점유하는 일반(플랫폼) 스레드와
 *       달리, JVM이 관리하는 아주 가벼운 스레드다. 블로킹 호출(HTTP 응답 대기 등) 중에는 OS 스레드를
 *       반납했다가 필요할 때 다시 배정받는 방식이라, 수백~수천 개를 만들어도 스레드 풀 고갈 걱정 없이
 *       "블로킹 코드를 그대로 두고" 동시에 실행할 수 있다. 리액티브(WebClient 등)로 재작성할 필요가 없다.</li>
 *   <li><b>{@code CompletableFuture.supplyAsync(task, executor)}</b>: {@code task}를 즉시 별도 스레드에서
 *       실행 시작하고, 결과를 나중에 받을 수 있는 핸들(future)을 즉시 반환한다. 그래서 여러 항목에 대해
 *       반복문으로 {@code supplyAsync}를 호출하면, 그 항목들의 호출이 "동시에" 시작된다(순차 대기 X).</li>
 *   <li><b>{@code future.join()}</b>: 해당 작업이 끝날 때까지 기다렸다가 결과를 꺼낸다. 모든 future를
 *       만들어둔 뒤에 join을 모아서 호출하면, 총 대기 시간은 "각 작업 시간의 합"이 아니라 "가장 오래
 *       걸리는 작업 하나의 시간"에 가까워진다(단, 실제 TourAPI 동시 요청 수는 별도의
 *       {@code Semaphore}로 제한되므로 그 한도 안에서만 동시에 진행된다).</li>
 * </ul>
 *
 * <h2>왜 이 헬퍼가 필요한가</h2>
 * {@code supplyAsync}에 넘긴 작업(task) 안에서 예외가 발생하면, {@code future.join()}은 그 예외를
 * 그대로 던지지 않고 {@link CompletionException}으로 한 번 감싸서 던진다(자바 API의 정해진 동작).
 * 그런데 이 프로젝트의 {@code GlobalExceptionHandler}는 {@code @ExceptionHandler(TourApiUnavailableException.class)}처럼
 * "정확한 예외 타입"으로 매칭하기 때문에, 감싸인 채로 두면 원래 던지려던 예외(예: TourAPI 장애를 뜻하는
 * {@code TourApiUnavailableException})를 못 알아보고 엉뚱하게 일반 500 에러로 처리해버린다.
 * {@link #joinUnwrapped}는 이 감싸기를 풀어서, {@code join()}을 안 쓰고 직접 호출한 것처럼 원래
 * 예외가 그대로 전파되게 해준다.
 */
public final class CompletableFutures {

    private CompletableFutures() {
    }

    /**
     * {@code future.join()}과 동일하지만, 작업 중 발생한 예외가 {@link CompletionException}에
     * 감싸여 있으면 풀어서 원인(cause) 예외를 그대로 던진다.
     */
    public static <T> T joinUnwrapped(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }
}

package com.eodegano.cocobackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * POI 후보 캐시({@code poiCandidates}, TTL 6h) 워밍.
 * 실사용자가 콜드 캐시(최초 요청 시 타입별 TourAPI 호출)를 밟지 않도록,
 * 배포 직후(startup)와 TTL 만료 직전(5시간50분 주기)에 백그라운드에서 미리 채워둔다.
 * {@link TourLiveDataService}는 Spring 빈 경유로 호출 — 같은 클래스 내부 self-invocation이
 * 아니라 프록시를 타므로 {@code @Cacheable}/{@code @CacheEvict}가 정상 적용된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PoiCacheWarmupScheduler {

    private static final long REFRESH_INTERVAL_MS = 21_000_000L; // 5h 50m — TTL(6h) 만료 전

    private final TourLiveDataService tourLiveDataService;

    @EventListener(ApplicationReadyEvent.class)
    public void warmupOnStartup() {
        Thread.ofVirtual().name("poi-cache-warmup-startup").start(this::warmup);
    }

    @Scheduled(fixedRate = REFRESH_INTERVAL_MS, initialDelay = REFRESH_INTERVAL_MS)
    public void refreshCache() {
        Thread.ofVirtual().name("poi-cache-warmup-scheduled").start(this::refresh);
    }

    private void warmup() {
        try {
            log.info("POI 후보 캐시 초기 워밍 시작");
            tourLiveDataService.getAllCandidates();
            log.info("POI 후보 캐시 초기 워밍 완료");
        } catch (Exception e) {
            log.error("POI 후보 캐시 초기 워밍 실패: {}", e.getMessage());
        }
    }

    private void refresh() {
        try {
            log.info("POI 후보 캐시 정기 워밍 시작");
            tourLiveDataService.evictCandidatesCache();
            tourLiveDataService.getAllCandidates();
            log.info("POI 후보 캐시 정기 워밍 완료");
        } catch (Exception e) {
            log.error("POI 후보 캐시 정기 워밍 실패: {}", e.getMessage());
        }
    }
}

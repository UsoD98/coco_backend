package com.eodegano.cocobackend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * TourAPI 라이브 호출 결과 캐시 (v0.5.0 — 로컬 DB 미저장 원칙 전환)
 * 두 캐시 모두 TTL 6시간, 인프로세스(Caffeine) — 재시작 시 초기화, 인스턴스 간 공유 없음
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String POI_CANDIDATES_CACHE = "poiCandidates";
    public static final String POI_DETAIL_CACHE = "poiDetail";
    public static final String POI_FULL_DETAIL_CACHE = "poiFullDetail";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                POI_CANDIDATES_CACHE, POI_DETAIL_CACHE, POI_FULL_DETAIL_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(6, TimeUnit.HOURS)
                .maximumSize(2000));
        return cacheManager;
    }
}

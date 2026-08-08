package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.config.CacheConfig;
import com.eodegano.cocobackend.dataMig.service.TourApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * TourAPI 라이브 조회 서비스 (v0.5.0 — 로컬 DB 미저장 원칙)
 * 응답은 Caffeine 캐시(TTL 6h)를 경유한다 ({@link CacheConfig}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TourLiveDataService {

    private static final int CANDIDATE_TARGET_COUNT = 2000;

    private final TourApiClient api;

    /** 경북 전체 POI 후보 리스트 (전 타입, areaBasedList2 기반) */
    @Cacheable(cacheNames = CacheConfig.POI_CANDIDATES_CACHE, key = "'all'")
    public List<PoiSummary> getAllCandidates() {
        List<JsonNode> items = api.areaBasedListAll(null, CANDIDATE_TARGET_COUNT);
        List<PoiSummary> result = new ArrayList<>();
        for (JsonNode n : items) {
            try {
                result.add(mapSummary(n));
            } catch (Exception e) {
                log.warn("POI 후보 매핑 실패: contentid={}, err={}", api.text(n, "contentid"), e.getMessage());
            }
        }
        log.info("TourAPI 라이브 조회 완료: {}건", result.size());
        return result;
    }

    /** POI 개별 상세 (detailIntro2 기반, 타입별 운영시간/비용 원천 필드 매핑) */
    @Cacheable(cacheNames = CacheConfig.POI_DETAIL_CACHE, key = "#p0")
    public PoiDetail getDetail(Long contentId, Integer contentTypeId) {
        if (contentTypeId == null) {
            return new PoiDetail(contentId, null, null, null);
        }

        JsonNode n = firstItem(api.detailIntro(contentId, contentTypeId));
        if (n == null) {
            return new PoiDetail(contentId, contentTypeId, null, null);
        }

        String operatingHours;
        Integer cost;

        switch (contentTypeId) {
            case 12 -> { // ATTRACTION
                operatingHours = stripHtml(api.text(n, "usetime"));
                cost = null;
            }
            case 14 -> { // CULTURE
                operatingHours = stripHtml(api.text(n, "usetimeculture"));
                cost = parseCost(api.text(n, "usefee"));
            }
            case 15 -> { // EVENT — 운영시간 없음, usetimefestival을 비용 원천으로 사용 (레거시 마이그레이션 로직과 동일)
                operatingHours = null;
                cost = parseCost(api.text(n, "usetimefestival"));
            }
            case 28 -> { // LEPORTS
                operatingHours = stripHtml(api.text(n, "usetimeleports"));
                cost = null;
            }
            case 38 -> { // SHOPPING
                operatingHours = stripHtml(api.text(n, "opentime"));
                cost = null;
            }
            case 39 -> { // FOOD
                operatingHours = stripHtml(api.text(n, "opentimefood"));
                cost = null;
            }
            default -> { // ACCOMMODATION(32) 등 — 운영시간/비용 없음
                operatingHours = null;
                cost = null;
            }
        }

        return new PoiDetail(contentId, contentTypeId, operatingHours, cost);
    }

    private PoiSummary mapSummary(JsonNode n) {
        return new PoiSummary(
                Long.parseLong(api.text(n, "contentid")),
                api.integer(n, "contenttypeid"),
                Optional.ofNullable(api.text(n, "title")).orElse("(제목없음)"),
                api.text(n, "firstimage"),
                parseDecimal(api.text(n, "mapx")),
                parseDecimal(api.text(n, "mapy")),
                api.text(n, "lDongSignguCd")
        );
    }

    private JsonNode firstItem(JsonNode response) {
        JsonNode items = api.extractItems(response);
        if (items == null || !items.isArray() || items.isEmpty()) return null;
        return items.get(0);
    }

    private String stripHtml(String text) {
        if (text == null || text.isBlank()) return null;
        return text.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", "")
                .trim();
    }

    private Integer parseCost(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.contains("무료")) return 0;
        String digits = raw.replaceAll(",", "").replaceAll(".*?(\\d+).*", "$1");
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

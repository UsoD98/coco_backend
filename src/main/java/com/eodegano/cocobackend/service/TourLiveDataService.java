package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.config.CacheConfig;
import com.eodegano.cocobackend.dataMig.service.TourApiClient;
import com.eodegano.cocobackend.domain.enums.PlaceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TourAPI 라이브 조회 서비스 (v0.5.0 — 로컬 DB 미저장 원칙)
 * 응답은 Caffeine 캐시(TTL 6h)를 경유한다 ({@link CacheConfig}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TourLiveDataService {

    /** 타입별 후보 캡 — 7타입 합산 실제 사용량은 대부분 이보다 훨씬 작음(소형 타입은 totalCount에서 조기 종료) */
    private static final int PER_TYPE_CANDIDATE_CAP = 3000;

    private final TourApiClient api;

    /**
     * 경북 전체 POI 후보 리스트 (전 타입, areaBasedList2 기반).
     * 타입(contentTypeId)별로 나눠 수집 — 물량이 큰 타입(숙박·음식점 등)이 소형 타입(행사·레포츠 등)을
     * 후보 풀에서 밀어내는 것을 방지한다. sync=true로 캐시 미스 시 동시 요청이 중복으로
     * TourAPI를 호출하지 않도록 막는다.
     */
    @Cacheable(cacheNames = CacheConfig.POI_CANDIDATES_CACHE, key = "'all'", sync = true)
    public List<PoiSummary> getAllCandidates() {
        List<Integer> typeIds = Arrays.stream(PlaceType.values())
                .map(PlaceType::getContentTypeId)
                .toList();

        Map<Integer, List<JsonNode>> byType = api.areaBasedListAllByTypes(typeIds, PER_TYPE_CANDIDATE_CAP);

        List<PoiSummary> result = new ArrayList<>();
        for (List<JsonNode> items : byType.values()) {
            for (JsonNode n : items) {
                try {
                    result.add(mapSummary(n));
                } catch (Exception e) {
                    log.warn("POI 후보 매핑 실패: contentid={}, err={}", api.text(n, "contentid"), e.getMessage());
                }
            }
        }
        log.info("TourAPI 라이브 조회 완료: {}건", result.size());
        return result;
    }

    /** 캐시 워밍(정기 갱신)용 — TTL 만료 전에 evict 후 {@link #getAllCandidates()}를 다시 불러 캐시를 채운다 */
    @CacheEvict(cacheNames = CacheConfig.POI_CANDIDATES_CACHE, key = "'all'")
    public void evictCandidatesCache() {
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

    /**
     * POI 상세 통합 조회 (detailCommon2 + detailInfo2 기반, GBC018)
     * contentId가 TourAPI에 존재하지 않으면 null 반환 (호출자가 404로 매핑)
     */
    @Cacheable(cacheNames = CacheConfig.POI_FULL_DETAIL_CACHE, key = "#p0")
    public PoiFullDetail getFullDetail(Long contentId) {
        JsonNode common = firstItem(api.detailCommon(contentId));
        if (common == null) {
            return null;
        }

        Integer contentTypeId = api.integer(common, "contenttypeid");
        List<PoiInfoItem> infoList = contentTypeId == null
                ? List.of()
                : fetchInfoList(contentId, contentTypeId);

        return new PoiFullDetail(
                contentId,
                contentTypeId,
                Optional.ofNullable(api.text(common, "title")).orElse("(제목없음)"),
                api.text(common, "tel"),
                stripHtml(api.text(common, "homepage")),
                stripHtml(api.text(common, "overview")),
                api.text(common, "firstimage"),
                api.text(common, "firstimage2"),
                api.text(common, "addr1"),
                api.text(common, "addr2"),
                parseDecimal(api.text(common, "mapx")),
                parseDecimal(api.text(common, "mapy")),
                infoList
        );
    }

    private List<PoiInfoItem> fetchInfoList(Long contentId, Integer contentTypeId) {
        JsonNode items = api.extractItems(api.detailInfo(contentId, contentTypeId));
        if (items == null || !items.isArray()) {
            return List.of();
        }

        List<PoiInfoItem> result = new ArrayList<>();
        for (JsonNode n : items) {
            String infoname = api.text(n, "infoname");
            String infotext = stripHtml(api.text(n, "infotext"));
            if (infoname != null || infotext != null) {
                result.add(new PoiInfoItem(infoname, infotext));
            }
        }
        return result;
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

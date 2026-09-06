package com.eodegano.cocobackend.dataMig.service;

import com.eodegano.cocobackend.exception.TourApiUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static com.eodegano.cocobackend.util.CompletableFutures.joinUnwrapped;

/**
 * 한국관광공사 TourAPI v2 호출 클라이언트
 * Base URL: https://apis.data.go.kr/B551011/KorService2
 *
 * Spring Boot 4.0 / Spring 7.0 기준
 * - RestClient (RestTemplate 대체)
 * - UriComponentsBuilder.fromUriString() (fromHttpUrl deprecated)
 * - tools.jackson.databind.JsonNode (Jackson 3.x 패키지 변경)
 */
@Slf4j
@Component
public class TourApiClient {

    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2";
    private static final String GYEONGBUK_AREA_CODE = "35";

    /** 페이지당 요청 건수 — 클수록 동일 범위를 더 적은 호출로 커버 (일일 호출 한도 1000건 절약) */
    private static final int PAGE_SIZE = 300;
    /** 동시 진행 요청 수 상한 — TourAPI에 문서화된 동시 호출 제한이 없어 보수적으로 제한 */
    private static final int MAX_CONCURRENT_REQUESTS = 4;
    private static final int MAX_API_RETRIES = 3;
    private static final long RETRY_BASE_DELAY_MS = 500;

    @Value("${tourapi.service-key}")
    private String serviceKey;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Semaphore requestThrottle = new Semaphore(MAX_CONCURRENT_REQUESTS);
    private final ExecutorService pageFetchExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * areaBasedList2 - 지역 기반 관광지 목록 조회
     */
    public JsonNode areaBasedList(Integer contentTypeId, int pageNo, int numOfRows) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(BASE_URL + "/areaBasedList2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "CocoTravel")
                .queryParam("_type", "json")
                .queryParam("areaCode", GYEONGBUK_AREA_CODE)
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", numOfRows);

        if (contentTypeId != null) {
            builder.queryParam("contentTypeId", contentTypeId);
        }

        return callApi(builder.build(true).toUri());
    }

    /**
     * detailCommon2 - 공통 정보 조회
     */
    public JsonNode detailCommon(Long contentId) {
        URI uri = UriComponentsBuilder
                .fromUriString(BASE_URL + "/detailCommon2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "CocoTravel")
                .queryParam("_type", "json")
                .queryParam("contentId", contentId)
                .build(true).toUri();

        return callApi(uri);
    }

    /**
     * detailIntro2 - 소개 정보 조회
     */
    public JsonNode detailIntro(Long contentId, int contentTypeId) {
        URI uri = UriComponentsBuilder
                .fromUriString(BASE_URL + "/detailIntro2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "CocoTravel")
                .queryParam("_type", "json")
                .queryParam("contentId", contentId)
                .queryParam("contentTypeId", contentTypeId)
                .build(true).toUri();

        return callApi(uri);
    }

    /**
     * detailInfo2 - 반복 정보 조회
     */
    public JsonNode detailInfo(Long contentId, int contentTypeId) {
        URI uri = UriComponentsBuilder
                .fromUriString(BASE_URL + "/detailInfo2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "CocoTravel")
                .queryParam("_type", "json")
                .queryParam("contentId", contentId)
                .queryParam("contentTypeId", contentTypeId)
                .build(true).toUri();

        return callApi(uri);
    }

    /**
     * areaBasedList2 전체 페이지 수집 (최대 targetCount건)
     * 1페이지로 totalCount를 먼저 확인한 뒤, 남은 페이지는 병렬로 수집한다
     * (동시성은 {@link #requestThrottle}로 상한 적용, 호출 총량은 페이지 수와 동일해 변하지 않음).
     */
    public List<JsonNode> areaBasedListAll(Integer contentTypeId, int targetCount) {
        int numOfRows = Math.min(targetCount, PAGE_SIZE);

        JsonNode firstResponse = areaBasedList(contentTypeId, 1, numOfRows);
        List<JsonNode> results = new ArrayList<>(toList(extractItems(firstResponse)));

        if (results.isEmpty()) {
            log.info("데이터 없음 (contentTypeId={})", contentTypeId);
            return results;
        }

        int totalCount = getTotalCount(firstResponse);
        int available = Math.min(totalCount, targetCount);
        int totalPages = (int) Math.ceil((double) available / numOfRows);

        if (totalPages > 1) {
            List<CompletableFuture<List<JsonNode>>> futures = new ArrayList<>();
            for (int pageNo = 2; pageNo <= totalPages; pageNo++) {
                int p = pageNo;
                futures.add(CompletableFuture.supplyAsync(
                        () -> toList(extractItems(areaBasedList(contentTypeId, p, numOfRows))),
                        pageFetchExecutor));
            }
            for (CompletableFuture<List<JsonNode>> future : futures) {
                results.addAll(joinUnwrapped(future));
            }
        }

        if (results.size() > targetCount) {
            results = results.subList(0, targetCount);
        }

        log.info("areaBasedList 수집 완료: contentTypeId={}, {}건 ({}페이지)", contentTypeId, results.size(), totalPages);
        return results;
    }

    /**
     * 콘텐츠타입별로 {@link #areaBasedListAll}을 동시에 실행해 합친다.
     * 타입 간에도 병렬로 진행하지만(가상 스레드라 중첩 join이 데드락을 유발하지 않음),
     * 실제 동시 HTTP 요청 수는 {@link #requestThrottle}로 여전히 {@link #MAX_CONCURRENT_REQUESTS}건으로 제한된다.
     */
    public Map<Integer, List<JsonNode>> areaBasedListAllByTypes(List<Integer> contentTypeIds, int perTypeTargetCount) {
        Map<Integer, CompletableFuture<List<JsonNode>>> futures = new LinkedHashMap<>();
        for (Integer typeId : contentTypeIds) {
            futures.put(typeId, CompletableFuture.supplyAsync(
                    () -> areaBasedListAll(typeId, perTypeTargetCount), pageFetchExecutor));
        }

        Map<Integer, List<JsonNode>> result = new LinkedHashMap<>();
        futures.forEach((typeId, future) -> result.put(typeId, joinUnwrapped(future)));
        return result;
    }

    private List<JsonNode> toList(JsonNode items) {
        if (items == null || !items.isArray()) return List.of();
        List<JsonNode> list = new ArrayList<>();
        items.forEach(list::add);
        return list;
    }

    /** 응답에서 item 배열 추출 */
    public JsonNode extractItems(JsonNode response) {
        try {
            JsonNode items = response
                    .path("response").path("body")
                    .path("items").path("item");
            if (items.isMissingNode() || items.isNull()) return null;
            return items;
        } catch (Exception e) {
            log.warn("items 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /** 응답에서 totalCount 추출 */
    public int getTotalCount(JsonNode response) {
        try {
            JsonNode node = response.path("response").path("body").path("totalCount");
            if (node.isMissingNode() || node.isNull()) return 0;
            return node.intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private JsonNode callApi(URI uri) {
        for (int attempt = 1; attempt <= MAX_API_RETRIES; attempt++) {
            try {
                requestThrottle.acquire();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return objectMapper.createObjectNode();
            }

            try {
                String body = restClient.get()
                        .uri(uri)
                        .retrieve()
                        .body(String.class);

                if (body == null || body.isBlank()) {
                    log.error("API 응답 비어있음: uri={}", uri);
                    return objectMapper.createObjectNode();
                }

                return objectMapper.readTree(body);

            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean retryable = status == 429 || status >= 500;
                if (retryable && attempt < MAX_API_RETRIES) {
                    log.warn("API 호출 재시도 (attempt {}/{}): uri={}, status={}", attempt, MAX_API_RETRIES, uri, status);
                    sleepQuietly(RETRY_BASE_DELAY_MS * attempt);
                    continue;
                }
                if (retryable) {
                    // 재시도 소진 — 정상 "0건" 응답과 구분해야 하므로 빈 노드 대신 전용 예외를 던진다
                    log.error("API 호출 재시도 소진: uri={}, status={}", uri, status);
                    throw new TourApiUnavailableException(
                            "TourAPI 호출이 재시도 소진 후 실패했습니다 (status=" + status + ")", e);
                }
                log.error("API 호출 오류: uri={}, status={}, error={}", uri, status, e.getMessage());
                return objectMapper.createObjectNode();

            } catch (Exception e) {
                if (attempt < MAX_API_RETRIES) {
                    log.warn("API 호출 재시도 (attempt {}/{}): uri={}, error={}", attempt, MAX_API_RETRIES, uri, e.getMessage());
                    sleepQuietly(RETRY_BASE_DELAY_MS * attempt);
                    continue;
                }
                log.error("API 호출 재시도 소진: uri={}, error={}", uri, e.getMessage());
                throw new TourApiUnavailableException(
                        "TourAPI 호출이 재시도 소진 후 실패했습니다", e);

            } finally {
                requestThrottle.release();
            }
        }
        return objectMapper.createObjectNode();
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** null/빈 문자열 안전 추출 */
    public String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isNull()) return null;
        String s = v.stringValue();
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }

    public Integer integer(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        // 숫자 노드면 바로 intValue, 문자열 노드면 파싱
        if (v.isNumber()) return v.intValue();
        String s = v.stringValue();
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    public Boolean bool(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isBoolean()) return v.booleanValue();
        String s = v.stringValue();
        if (s == null) return null;
        s = s.trim();
        return "1".equals(s) || "Y".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s);
    }
}

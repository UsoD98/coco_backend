package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.dataMig.service.TourApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * TourApiClient의 HTTP 호출 메서드(detailCommon/detailIntro/detailInfo)만 스텁하고,
 * 실제 필드 추출 헬퍼(text/integer/extractItems)는 그대로 실행해 파싱 로직을 검증한다.
 */
class TourLiveDataServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private TourApiClient api;
    private TourLiveDataService tourLiveDataService;

    @BeforeEach
    void setUp() {
        api = spy(new TourApiClient());
        tourLiveDataService = new TourLiveDataService(api);
    }

    private JsonNode json(String raw) {
        return mapper.readTree(raw);
    }

    // ───────────────────────────────────────────────
    // getFullDetail (GBC018 — detailCommon2 + detailInfo2)
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("getFullDetail 성공 - detailCommon2 + detailInfo2를 조합해 PoiFullDetail 반환")
    void getFullDetailSuccess() {
        JsonNode common = json("""
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[
                  {"contentid":"126289","contenttypeid":"12","title":"불국사","tel":"054-746-9913",
                   "homepage":"<a href=\\"https://www.bulguksa.or.kr\\" target=\\"_blank\\">https://www.bulguksa.or.kr</a>",
                   "overview":"신라 경덕왕 10년에 창건된 사찰...<br>천년 고찰",
                   "firstimage":"https://example.com/image1.jpg","firstimage2":"https://example.com/image2.jpg",
                   "addr1":"경상북도 경주시 불국로 385","addr2":"",
                   "mapx":"129.3316","mapy":"35.7903"}
                ]},"numOfRows":1,"pageNo":1,"totalCount":1}}}
                """);
        JsonNode info = json("""
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[
                  {"contentid":"126289","contenttypeid":"12","serialnum":"0","infoname":"입장료","infotext":"어른 6,000원"}
                ]},"numOfRows":1,"pageNo":1,"totalCount":1}}}
                """);
        doReturn(common).when(api).detailCommon(126289L);
        doReturn(info).when(api).detailInfo(126289L, 12);

        PoiFullDetail result = tourLiveDataService.getFullDetail(126289L);

        assertThat(result).isNotNull();
        assertThat(result.contentId()).isEqualTo(126289L);
        assertThat(result.contentTypeId()).isEqualTo(12);
        assertThat(result.title()).isEqualTo("불국사");
        assertThat(result.tel()).isEqualTo("054-746-9913");
        assertThat(result.homepage()).isEqualTo("https://www.bulguksa.or.kr");
        assertThat(result.overview()).isEqualTo("신라 경덕왕 10년에 창건된 사찰...\n천년 고찰");
        assertThat(result.firstimage()).isEqualTo("https://example.com/image1.jpg");
        assertThat(result.firstimage2()).isEqualTo("https://example.com/image2.jpg");
        assertThat(result.addr1()).isEqualTo("경상북도 경주시 불국로 385");
        assertThat(result.addr2()).isNull();
        assertThat(result.mapx()).isEqualByComparingTo(new BigDecimal("129.3316"));
        assertThat(result.mapy()).isEqualByComparingTo(new BigDecimal("35.7903"));
        assertThat(result.infoList()).hasSize(1);
        assertThat(result.infoList().get(0).infoname()).isEqualTo("입장료");
        assertThat(result.infoList().get(0).infotext()).isEqualTo("어른 6,000원");
    }

    @Test
    @DisplayName("getFullDetail - TourAPI에 존재하지 않는 contentId면 null 반환 (호출자가 404로 매핑)")
    void getFullDetailNotFound() {
        JsonNode empty = json("""
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":"","numOfRows":0,"pageNo":1,"totalCount":0}}}
                """);
        doReturn(empty).when(api).detailCommon(1L);

        PoiFullDetail result = tourLiveDataService.getFullDetail(1L);

        assertThat(result).isNull();
        verify(api, never()).detailInfo(any(), anyInt());
    }

    @Test
    @DisplayName("getFullDetail - contenttypeid가 없으면 detailInfo2 호출 없이 infoList 빈 리스트")
    void getFullDetailWithoutContentTypeId() {
        JsonNode common = json("""
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[
                  {"contentid":"1","title":"제목만있음"}
                ]},"numOfRows":1,"pageNo":1,"totalCount":1}}}
                """);
        doReturn(common).when(api).detailCommon(1L);

        PoiFullDetail result = tourLiveDataService.getFullDetail(1L);

        assertThat(result).isNotNull();
        assertThat(result.contentTypeId()).isNull();
        assertThat(result.infoList()).isEmpty();
        verify(api, never()).detailInfo(any(), anyInt());
    }

    @Test
    @DisplayName("getFullDetail - title 없으면 (제목없음) 기본값 적용")
    void getFullDetailWithoutTitle() {
        JsonNode common = json("""
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[
                  {"contentid":"1","contenttypeid":"12"}
                ]},"numOfRows":1,"pageNo":1,"totalCount":1}}}
                """);
        JsonNode emptyInfo = json("""
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":"","numOfRows":0}}}
                """);
        doReturn(common).when(api).detailCommon(1L);
        doReturn(emptyInfo).when(api).detailInfo(1L, 12);

        PoiFullDetail result = tourLiveDataService.getFullDetail(1L);

        assertThat(result.title()).isEqualTo("(제목없음)");
        assertThat(result.infoList()).isEmpty();
    }

    // ───────────────────────────────────────────────
    // getDetail (기존 detailIntro2 기반 — 운영시간/비용, 회귀 검증)
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("getDetail - 음식점(39)은 opentimefood를 운영시간으로, cost는 null")
    void getDetailForFood() {
        JsonNode intro = json("""
                {"response":{"body":{"items":{"item":[
                  {"opentimefood":"11:00~21:00<br>매주 월요일 휴무"}
                ]}}}}
                """);
        doReturn(intro).when(api).detailIntro(1L, 39);

        PoiDetail result = tourLiveDataService.getDetail(1L, 39);

        assertThat(result.operatingHours()).isEqualTo("11:00~21:00\n매주 월요일 휴무");
        assertThat(result.cost()).isNull();
    }

    @Test
    @DisplayName("getDetail - 문화시설(14)은 무료 표기를 cost=0으로 파싱")
    void getDetailForCultureFree() {
        JsonNode intro = json("""
                {"response":{"body":{"items":{"item":[
                  {"usetimeculture":"09:00~18:00","usefee":"무료"}
                ]}}}}
                """);
        doReturn(intro).when(api).detailIntro(1L, 14);

        PoiDetail result = tourLiveDataService.getDetail(1L, 14);

        assertThat(result.operatingHours()).isEqualTo("09:00~18:00");
        assertThat(result.cost()).isEqualTo(0);
    }

    @Test
    @DisplayName("getDetail - 문화시설(14) 유료 요금 문자열에서 숫자만 추출")
    void getDetailForCulturePaid() {
        JsonNode intro = json("""
                {"response":{"body":{"items":{"item":[
                  {"usetimeculture":"09:00~18:00","usefee":"성인 5,000원"}
                ]}}}}
                """);
        doReturn(intro).when(api).detailIntro(1L, 14);

        PoiDetail result = tourLiveDataService.getDetail(1L, 14);

        assertThat(result.cost()).isEqualTo(5000);
    }

    @Test
    @DisplayName("getDetail - contentTypeId가 null이면 TourAPI 호출 없이 빈 PoiDetail 반환")
    void getDetailWithNullContentTypeId() {
        PoiDetail result = tourLiveDataService.getDetail(1L, null);

        assertThat(result.operatingHours()).isNull();
        assertThat(result.cost()).isNull();
        verify(api, never()).detailIntro(any(), anyInt());
    }

    // ───────────────────────────────────────────────
    // getAllCandidates (areaBasedList2 기반 요약 매핑)
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("getAllCandidates - 콘텐츠타입별로 areaBasedList2를 호출해 PoiSummary로 매핑")
    void getAllCandidatesSuccess() {
        JsonNode emptyPage = json("""
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":"","numOfRows":0,"pageNo":1,"totalCount":0}}}
                """);
        JsonNode attractionPage = json("""
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[
                  {"contentid":"126289","contenttypeid":"12","title":"불국사","firstimage":"http://img.jpg",
                   "mapx":"129.3316","mapy":"35.7903","lDongSignguCd":"130"}
                ]},"numOfRows":1,"pageNo":1,"totalCount":1}}}
                """);
        // 타입별 분리 수집(A) — ATTRACTION(12)만 결과가 있고 나머지 6개 타입은 빈 페이지
        doReturn(emptyPage).when(api).areaBasedList(anyInt(), eq(1), eq(300));
        doReturn(attractionPage).when(api).areaBasedList(eq(12), eq(1), eq(300));

        var result = tourLiveDataService.getAllCandidates();

        assertThat(result).hasSize(1);
        PoiSummary summary = result.get(0);
        assertThat(summary.contentId()).isEqualTo(126289L);
        assertThat(summary.contentTypeId()).isEqualTo(12);
        assertThat(summary.title()).isEqualTo("불국사");
        assertThat(summary.lDongSignguCd()).isEqualTo("130");
    }
}

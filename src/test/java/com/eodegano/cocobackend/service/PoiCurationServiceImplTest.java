package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.dto.PoiCurationItemDto;
import com.eodegano.cocobackend.dto.PoiCurationResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PoiCurationServiceImplTest {

    @InjectMocks
    private PoiCurationServiceImpl poiCurationService;

    @Mock
    private TourLiveDataService tourLiveDataService;

    private PoiSummary bulguksa;
    private PoiSummary gyeongjuFood;

    @BeforeEach
    void setUp() {
        bulguksa = new PoiSummary(126289L, 12, "불국사", "http://img1.jpg",
                new BigDecimal("129.3316"), new BigDecimal("35.7903"), "130");
        gyeongjuFood = new PoiSummary(999999L, 39, "황남빵", "http://img2.jpg",
                new BigDecimal("129.2"), new BigDecimal("35.8"), "130");
    }

    // ───────────────────────────────────────────────
    // 필수 파라미터 검증
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("실패 - sigunguCode가 null이면 IllegalArgumentException")
    void getPoiListFailWithNullSigunguCode() {
        assertThatThrownBy(() -> poiCurationService.getPoiList(null, 2, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sigunguCode는 필수입니다");
    }

    @Test
    @DisplayName("실패 - sigunguCode가 빈 문자열이면 IllegalArgumentException")
    void getPoiListFailWithBlankSigunguCode() {
        assertThatThrownBy(() -> poiCurationService.getPoiList("  ", 2, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sigunguCode는 필수입니다");
    }

    @Test
    @DisplayName("실패 - peopleCount가 null이면 IllegalArgumentException")
    void getPoiListFailWithNullPeopleCount() {
        assertThatThrownBy(() -> poiCurationService.getPoiList("35130", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("peopleCount는 필수입니다");
    }

    // ───────────────────────────────────────────────
    // sigunguCode 정규화 + 필터링
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("성공 - areaCode 접두(35) 포함 5자리 sigunguCode를 3자리로 정규화해 매칭")
    void getPoiListSuccessWithAreaCodePrefixedSigunguCode() {
        given(tourLiveDataService.getAllCandidates()).willReturn(List.of(bulguksa, gyeongjuFood));

        PoiCurationResponseDto result = poiCurationService.getPoiList("35130", 2, null);

        assertThat(result.isAvailable()).isTrue();
        assertThat(result.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("성공 - 이미 3자리인 sigunguCode는 그대로 매칭")
    void getPoiListSuccessWithRawSigunguCode() {
        given(tourLiveDataService.getAllCandidates()).willReturn(List.of(bulguksa, gyeongjuFood));

        PoiCurationResponseDto result = poiCurationService.getPoiList("130", 2, null);

        assertThat(result.isAvailable()).isTrue();
        assertThat(result.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("성공 - contentTypeId 필터로 특정 유형만 반환")
    void getPoiListSuccessWithContentTypeFilter() {
        given(tourLiveDataService.getAllCandidates()).willReturn(List.of(bulguksa, gyeongjuFood));

        PoiCurationResponseDto result = poiCurationService.getPoiList("35130", 2, 39);

        assertThat(result.getItems()).hasSize(1);
        PoiCurationItemDto item = result.getItems().get(0);
        assertThat(item.getContentId()).isEqualTo(999999L);
        assertThat(item.getContentTypeId()).isEqualTo(39);
        assertThat(item.getTitle()).isEqualTo("황남빵");
        assertThat(item.getThumbnail()).isEqualTo("http://img2.jpg");
    }

    @Test
    @DisplayName("성공 - 매칭 결과 없으면 available false + 빈 배열")
    void getPoiListSuccessWithNoMatch() {
        given(tourLiveDataService.getAllCandidates()).willReturn(List.of(bulguksa));

        PoiCurationResponseDto result = poiCurationService.getPoiList("35990", 2, null);

        assertThat(result.isAvailable()).isFalse();
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    @DisplayName("성공 - avgPrice는 BOQ14 미확정으로 항상 null (음식점 포함)")
    void getPoiListAvgPriceAlwaysNull() {
        given(tourLiveDataService.getAllCandidates()).willReturn(List.of(gyeongjuFood));

        PoiCurationResponseDto result = poiCurationService.getPoiList("35130", 2, 39);

        assertThat(result.getItems()).allSatisfy(item -> assertThat(item.getAvgPrice()).isNull());
    }
}

package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.dto.PoiDetailResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/** GBC018 POI 상세 통합 조회 단위 테스트 */
@ExtendWith(MockitoExtension.class)
class PoiDetailServiceImplTest {

    @InjectMocks
    private PoiDetailServiceImpl poiDetailService;

    @Mock
    private TourLiveDataService tourLiveDataService;

    @Test
    @DisplayName("성공 - detailCommon2+detailInfo2 결과를 응답 DTO로 매핑")
    void getPoiDetailSuccess() {
        PoiFullDetail detail = new PoiFullDetail(
                126289L, 12, "불국사", "054-746-9913", "https://www.bulguksa.or.kr",
                "신라 경덕왕 10년에 창건된 사찰...",
                "https://example.com/image1.jpg", "https://example.com/image2.jpg",
                "경상북도 경주시 불국로 385", null,
                new BigDecimal("129.3316"), new BigDecimal("35.7903"),
                List.of(new PoiInfoItem("입장료", "어른 6,000원 / 청소년 4,000원"))
        );
        given(tourLiveDataService.getFullDetail(126289L)).willReturn(detail);

        PoiDetailResponseDto result = poiDetailService.getPoiDetail(126289L);

        assertThat(result.getContentId()).isEqualTo(126289L);
        assertThat(result.getContentTypeId()).isEqualTo(12);
        assertThat(result.getTitle()).isEqualTo("불국사");
        assertThat(result.getTel()).isEqualTo("054-746-9913");
        assertThat(result.getHomepage()).isEqualTo("https://www.bulguksa.or.kr");
        assertThat(result.getOverview()).isEqualTo("신라 경덕왕 10년에 창건된 사찰...");
        assertThat(result.getFirstimage()).isEqualTo("https://example.com/image1.jpg");
        assertThat(result.getFirstimage2()).isEqualTo("https://example.com/image2.jpg");
        assertThat(result.getAddr1()).isEqualTo("경상북도 경주시 불국로 385");
        assertThat(result.getAddr2()).isNull();
        assertThat(result.getMapx()).isEqualByComparingTo("129.3316");
        assertThat(result.getMapy()).isEqualByComparingTo("35.7903");
        assertThat(result.getInfoList()).hasSize(1);
        assertThat(result.getInfoList().get(0).getInfoname()).isEqualTo("입장료");
        assertThat(result.getInfoList().get(0).getInfotext()).isEqualTo("어른 6,000원 / 청소년 4,000원");
    }

    @Test
    @DisplayName("성공 - avgPrice는 BOQ14 미확정으로 음식점(contentTypeId=39)도 항상 null")
    void getPoiDetailAvgPriceAlwaysNullForFood() {
        PoiFullDetail foodDetail = new PoiFullDetail(
                1L, 39, "황남빵", null, null, null, null, null, null, null,
                null, null, List.of()
        );
        given(tourLiveDataService.getFullDetail(1L)).willReturn(foodDetail);

        PoiDetailResponseDto result = poiDetailService.getPoiDetail(1L);

        assertThat(result.getAvgPrice()).isNull();
    }

    @Test
    @DisplayName("실패 - TourAPI에 존재하지 않는 contentId → NoSuchElementException(404 매핑)")
    void getPoiDetailFailWithNotFound() {
        given(tourLiveDataService.getFullDetail(999L)).willReturn(null);

        assertThatThrownBy(() -> poiDetailService.getPoiDetail(999L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("존재하지 않는 POI입니다");
    }
}

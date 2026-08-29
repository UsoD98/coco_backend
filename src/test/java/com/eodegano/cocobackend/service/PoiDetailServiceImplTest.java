package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.domain.PoiRating;
import com.eodegano.cocobackend.domain.User;
import com.eodegano.cocobackend.dto.PoiDetailResponseDto;
import com.eodegano.cocobackend.repository.PoiRatingRepository;
import com.eodegano.cocobackend.repository.UserPoiLikeRepository;
import com.eodegano.cocobackend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

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

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPoiLikeRepository userPoiLikeRepository;

    @Mock
    private PoiRatingRepository poiRatingRepository;

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

        PoiDetailResponseDto result = poiDetailService.getPoiDetail(126289L, null);

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
        assertThat(result.isLiked()).isFalse();
        assertThat(result.getTotalLiked()).isZero();
    }

    @Test
    @DisplayName("성공 - avgPrice는 BOQ14 미확정으로 음식점(contentTypeId=39)도 항상 null")
    void getPoiDetailAvgPriceAlwaysNullForFood() {
        PoiFullDetail foodDetail = new PoiFullDetail(
                1L, 39, "황남빵", null, null, null, null, null, null, null,
                null, null, List.of()
        );
        given(tourLiveDataService.getFullDetail(1L)).willReturn(foodDetail);

        PoiDetailResponseDto result = poiDetailService.getPoiDetail(1L, null);

        assertThat(result.getAvgPrice()).isNull();
    }

    @Test
    @DisplayName("실패 - TourAPI에 존재하지 않는 contentId → NoSuchElementException(404 매핑)")
    void getPoiDetailFailWithNotFound() {
        given(tourLiveDataService.getFullDetail(999L)).willReturn(null);

        assertThatThrownBy(() -> poiDetailService.getPoiDetail(999L, null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("존재하지 않는 POI입니다");
    }

    // ───────────────────────────────────────────────
    // liked / totalLiked 필드
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("성공 - totalLiked는 poi_rating.likes 값을 그대로 반영")
    void getPoiDetailTotalLikedFromPoiRating() {
        PoiFullDetail detail = new PoiFullDetail(
                126289L, 12, "불국사", null, null, null, null, null, null, null,
                null, null, List.of()
        );
        given(tourLiveDataService.getFullDetail(126289L)).willReturn(detail);
        given(poiRatingRepository.findById(126289L))
                .willReturn(Optional.of(PoiRating.builder().contentid(126289L).likes(5).build()));

        PoiDetailResponseDto result = poiDetailService.getPoiDetail(126289L, null);

        assertThat(result.getTotalLiked()).isEqualTo(5);
        assertThat(result.isLiked()).isFalse();
    }

    @Test
    @DisplayName("성공 - poi_rating 행이 없으면 totalLiked=0")
    void getPoiDetailTotalLikedZeroWhenNoPoiRating() {
        PoiFullDetail detail = new PoiFullDetail(
                126289L, 12, "불국사", null, null, null, null, null, null, null,
                null, null, List.of()
        );
        given(tourLiveDataService.getFullDetail(126289L)).willReturn(detail);
        given(poiRatingRepository.findById(126289L)).willReturn(Optional.empty());

        PoiDetailResponseDto result = poiDetailService.getPoiDetail(126289L, null);

        assertThat(result.getTotalLiked()).isZero();
    }

    @Test
    @DisplayName("성공 - 로그인 사용자가 좋아요한 POI면 liked=true")
    void getPoiDetailLikedTrueWhenUserLiked() {
        PoiFullDetail detail = new PoiFullDetail(
                126289L, 12, "불국사", null, null, null, null, null, null, null,
                null, null, List.of()
        );
        given(tourLiveDataService.getFullDetail(126289L)).willReturn(detail);
        User user = User.builder().id(1L).email("test@test.com").nickname("tester").build();
        given(userRepository.findByEmailAndDeletedAtIsNull("test@test.com")).willReturn(Optional.of(user));
        given(userPoiLikeRepository.existsByUserIdAndContentId(1L, 126289L)).willReturn(true);
        given(poiRatingRepository.findById(126289L)).willReturn(Optional.empty());

        PoiDetailResponseDto result = poiDetailService.getPoiDetail(126289L, "test@test.com");

        assertThat(result.isLiked()).isTrue();
    }

    @Test
    @DisplayName("성공 - 유효 토큰이지만 탈퇴 등으로 사용자 미조회 시 liked=false 안전 처리")
    void getPoiDetailLikedFalseWhenUserNotFound() {
        PoiFullDetail detail = new PoiFullDetail(
                126289L, 12, "불국사", null, null, null, null, null, null, null,
                null, null, List.of()
        );
        given(tourLiveDataService.getFullDetail(126289L)).willReturn(detail);
        given(userRepository.findByEmailAndDeletedAtIsNull("ghost@test.com")).willReturn(Optional.empty());
        given(poiRatingRepository.findById(126289L)).willReturn(Optional.empty());

        PoiDetailResponseDto result = poiDetailService.getPoiDetail(126289L, "ghost@test.com");

        assertThat(result.isLiked()).isFalse();
    }

    // ───────────────────────────────────────────────
    // stars 필드
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("성공 - stars는 poi_rating.stars 값을 그대로 반영")
    void getPoiDetailStarsFromPoiRating() {
        PoiFullDetail detail = new PoiFullDetail(
                126289L, 12, "불국사", null, null, null, null, null, null, null,
                null, null, List.of()
        );
        given(tourLiveDataService.getFullDetail(126289L)).willReturn(detail);
        given(poiRatingRepository.findById(126289L))
                .willReturn(Optional.of(PoiRating.builder().contentid(126289L).stars(new BigDecimal("4.5")).likes(5).build()));

        PoiDetailResponseDto result = poiDetailService.getPoiDetail(126289L, null);

        assertThat(result.getStars()).isEqualByComparingTo("4.5");
    }

    @Test
    @DisplayName("성공 - poi_rating 행이 없으면 stars=null")
    void getPoiDetailStarsNullWhenNoPoiRating() {
        PoiFullDetail detail = new PoiFullDetail(
                126289L, 12, "불국사", null, null, null, null, null, null, null,
                null, null, List.of()
        );
        given(tourLiveDataService.getFullDetail(126289L)).willReturn(detail);
        given(poiRatingRepository.findById(126289L)).willReturn(Optional.empty());

        PoiDetailResponseDto result = poiDetailService.getPoiDetail(126289L, null);

        assertThat(result.getStars()).isNull();
    }
}

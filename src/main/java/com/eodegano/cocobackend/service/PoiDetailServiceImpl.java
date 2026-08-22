package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.domain.PoiRating;
import com.eodegano.cocobackend.dto.PoiDetailResponseDto;
import com.eodegano.cocobackend.dto.PoiInfoItemDto;
import com.eodegano.cocobackend.repository.PoiRatingRepository;
import com.eodegano.cocobackend.repository.UserPoiLikeRepository;
import com.eodegano.cocobackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/** GBC018 POI 상세 통합 조회 (GET /api/v1/poi/{contentId}) */
@Service
@RequiredArgsConstructor
public class PoiDetailServiceImpl implements PoiDetailService {

    private final TourLiveDataService tourLiveDataService;
    private final UserRepository userRepository;
    private final UserPoiLikeRepository userPoiLikeRepository;
    private final PoiRatingRepository poiRatingRepository;

    @Override
    public PoiDetailResponseDto getPoiDetail(Long contentId, String userEmail) {
        PoiFullDetail detail = tourLiveDataService.getFullDetail(contentId);
        if (detail == null) {
            throw new NoSuchElementException("존재하지 않는 POI입니다");
        }
        boolean liked = resolveLiked(contentId, userEmail);
        int totalLiked = poiRatingRepository.findById(contentId).map(PoiRating::getLikesOrZero).orElse(0);
        return toDto(detail, liked, totalLiked);
    }

    /** 로그인 사용자가 없으면(비로그인·탈퇴 등) false — 공개 조회 API이므로 예외를 던지지 않고 안전하게 처리 */
    private boolean resolveLiked(Long contentId, String userEmail) {
        if (userEmail == null) {
            return false;
        }
        return userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .map(user -> userPoiLikeRepository.existsByUserIdAndContentId(user.getId(), contentId))
                .orElse(false);
    }

    private PoiDetailResponseDto toDto(PoiFullDetail d, boolean liked, int totalLiked) {
        return PoiDetailResponseDto.builder()
                .contentId(d.contentId())
                .contentTypeId(d.contentTypeId())
                .title(d.title())
                .tel(d.tel())
                .homepage(d.homepage())
                .overview(d.overview())
                .firstimage(d.firstimage())
                .firstimage2(d.firstimage2())
                .addr1(d.addr1())
                .addr2(d.addr2())
                .mapx(d.mapx())
                .mapy(d.mapy())
                .avgPrice(null) // TODO(BOQ14): food_avg_price 근거 테이블 소실 — contentTypeId=39도 데이터 소스 확정 전까지 항상 null. docs/PRD_BACK.md BOQ14 참고
                .infoList(d.infoList().stream()
                        .map(i -> PoiInfoItemDto.builder()
                                .infoname(i.infoname())
                                .infotext(i.infotext())
                                .build())
                        .toList())
                .liked(liked)
                .totalLiked(totalLiked)
                .build();
    }
}

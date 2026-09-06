package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.domain.PoiRating;
import com.eodegano.cocobackend.dto.PoiCurationItemDto;
import com.eodegano.cocobackend.dto.PoiCurationResponseDto;
import com.eodegano.cocobackend.repository.PoiRatingRepository;
import com.eodegano.cocobackend.repository.UserPoiLikeRepository;
import com.eodegano.cocobackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** GBC017 큐레이션 POI 목록 조회 (GET /api/v1/poi) */
@Service
@RequiredArgsConstructor
public class PoiCurationServiceImpl implements PoiCurationService {

    private static final String GYEONGBUK_AREA_CODE = "35";

    private final TourLiveDataService tourLiveDataService;
    private final UserRepository userRepository;
    private final UserPoiLikeRepository userPoiLikeRepository;
    private final PoiRatingRepository poiRatingRepository;

    @Override
    public PoiCurationResponseDto getPoiList(String sigunguCode, Integer peopleCount, Integer contentTypeId, String userEmail) {
        if (sigunguCode == null || sigunguCode.isBlank()) {
            throw new IllegalArgumentException("sigunguCode는 필수입니다");
        }
        if (peopleCount == null) {
            throw new IllegalArgumentException("peopleCount는 필수입니다");
        }

        String normalizedSigunguCode = normalizeSigunguCode(sigunguCode);

        List<PoiSummary> filtered = tourLiveDataService.getAllCandidates().stream()
                .filter(p -> normalizedSigunguCode.equals(p.lDongSignguCd()))
                .filter(p -> contentTypeId == null || contentTypeId.equals(p.contentTypeId()))
                .toList();

        if (filtered.isEmpty()) {
            return PoiCurationResponseDto.builder()
                    .available(false)
                    .items(List.of())
                    .build();
        }

        List<Long> filteredContentIds = filtered.stream().map(PoiSummary::contentId).toList();
        Set<Long> likedContentIds = resolveLikedContentIds(userEmail, filteredContentIds);
        Map<Long, PoiRating> ratingsByContentId = resolveRatings(filteredContentIds);

        List<PoiCurationItemDto> items = filtered.stream()
                .map(p -> toItemDto(p, likedContentIds, ratingsByContentId))
                .collect(Collectors.toList());

        return PoiCurationResponseDto.builder()
                .available(true)
                .items(items)
                .build();
    }

    /** 로그인 사용자가 없거나(비로그인·탈퇴 등) 조회 대상이 없으면 빈 Set — liked=false로 안전 처리 */
    private Set<Long> resolveLikedContentIds(String userEmail, List<Long> contentIds) {
        if (userEmail == null || contentIds.isEmpty()) {
            return Set.of();
        }
        return userRepository.findByEmail(userEmail)
                .map(user -> (Set<Long>) new HashSet<>(userPoiLikeRepository.findContentIdsByUserIdAndContentIdIn(user.getId(), contentIds)))
                .orElseGet(Set::of);
    }

    /** 조회 대상이 없으면 빈 Map — stars=null로 안전 처리 */
    private Map<Long, PoiRating> resolveRatings(List<Long> contentIds) {
        if (contentIds.isEmpty()) {
            return Map.of();
        }
        return poiRatingRepository.findByContentidIn(contentIds).stream()
                .collect(Collectors.toMap(PoiRating::getContentid, rating -> rating));
    }

    private PoiCurationItemDto toItemDto(PoiSummary p, Set<Long> likedContentIds, Map<Long, PoiRating> ratingsByContentId) {
        PoiRating rating = ratingsByContentId.get(p.contentId());
        return PoiCurationItemDto.builder()
                .contentId(p.contentId())
                .contentTypeId(p.contentTypeId())
                .title(p.title())
                .mapx(p.mapx())
                .mapy(p.mapy())
                .thumbnail(p.firstimage())
                .avgPrice(null) // TODO(BOQ14): food_avg_price 근거 테이블 소실 — 데이터 소스 확정 전까지 항상 null. docs/PRD_BACK.md BOQ14 참고
                .liked(likedContentIds.contains(p.contentId()))
                .stars(rating == null ? null : rating.getStars())
                .build();
    }

    /** "35130"(areaCode+sigunguCode) 형식이면 areaCode(35) 접두를 제거해 lDongSignguCd(3자리)와 동일한 체계로 맞춘다 */
    private String normalizeSigunguCode(String sigunguCode) {
        if (sigunguCode.length() == 5 && sigunguCode.startsWith(GYEONGBUK_AREA_CODE)) {
            return sigunguCode.substring(2);
        }
        return sigunguCode;
    }
}

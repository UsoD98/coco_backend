package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.dto.PoiCurationItemDto;
import com.eodegano.cocobackend.dto.PoiCurationResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/** GBC017 큐레이션 POI 목록 조회 (GET /api/v1/poi) */
@Service
@RequiredArgsConstructor
public class PoiCurationServiceImpl implements PoiCurationService {

    private static final String GYEONGBUK_AREA_CODE = "35";

    private final TourLiveDataService tourLiveDataService;

    @Override
    public PoiCurationResponseDto getPoiList(String sigunguCode, Integer peopleCount, Integer contentTypeId) {
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

        List<PoiCurationItemDto> items = filtered.stream()
                .map(this::toItemDto)
                .collect(Collectors.toList());

        return PoiCurationResponseDto.builder()
                .available(true)
                .items(items)
                .build();
    }

    private PoiCurationItemDto toItemDto(PoiSummary p) {
        return PoiCurationItemDto.builder()
                .contentId(p.contentId())
                .contentTypeId(p.contentTypeId())
                .title(p.title())
                .mapx(p.mapx())
                .mapy(p.mapy())
                .thumbnail(p.firstimage())
                .avgPrice(null) // TODO(BOQ14): food_avg_price 근거 테이블 소실 — 데이터 소스 확정 전까지 항상 null. docs/PRD_BACK.md BOQ14 참고
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

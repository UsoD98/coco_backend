package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.dto.PoiDetailResponseDto;
import com.eodegano.cocobackend.dto.PoiInfoItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/** GBC018 POI 상세 통합 조회 (GET /api/v1/poi/{contentId}) */
@Service
@RequiredArgsConstructor
public class PoiDetailServiceImpl implements PoiDetailService {

    private final TourLiveDataService tourLiveDataService;

    @Override
    public PoiDetailResponseDto getPoiDetail(Long contentId) {
        PoiFullDetail detail = tourLiveDataService.getFullDetail(contentId);
        if (detail == null) {
            throw new NoSuchElementException("존재하지 않는 POI입니다");
        }
        return toDto(detail);
    }

    private PoiDetailResponseDto toDto(PoiFullDetail d) {
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
                .build();
    }
}

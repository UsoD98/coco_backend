package com.eodegano.cocobackend.service;

import java.math.BigDecimal;
import java.util.List;

/** TourAPI detailCommon2 + detailInfo2 라이브 조회 결과 통합 (v0.5.0 — 로컬 DetailCommon/DetailInfo 테이블 대체) */
public record PoiFullDetail(
        Long contentId,
        Integer contentTypeId,
        String title,
        String tel,
        String homepage,
        String overview,
        String firstimage,
        String firstimage2,
        String addr1,
        String addr2,
        BigDecimal mapx,
        BigDecimal mapy,
        List<PoiInfoItem> infoList
) {
}

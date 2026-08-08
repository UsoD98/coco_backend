package com.eodegano.cocobackend.service;

import java.math.BigDecimal;

/** TourAPI areaBasedList2 라이브 조회 결과 요약 (v0.5.0 — 로컬 tour 테이블 대체) */
public record PoiSummary(
        Long contentId,
        Integer contentTypeId,
        String title,
        String firstimage,
        BigDecimal mapx,
        BigDecimal mapy,
        String lDongSignguCd
) {
}

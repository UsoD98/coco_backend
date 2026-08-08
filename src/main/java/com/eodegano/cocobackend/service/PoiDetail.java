package com.eodegano.cocobackend.service;

/** TourAPI detailIntro2 라이브 조회 결과에서 뽑은 운영시간/비용 (v0.5.0 — 로컬 detail 테이블 대체) */
public record PoiDetail(
        Long contentId,
        Integer contentTypeId,
        String operatingHours,
        Integer cost
) {
}

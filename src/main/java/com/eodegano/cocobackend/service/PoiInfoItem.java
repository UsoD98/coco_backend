package com.eodegano.cocobackend.service;

/** TourAPI detailInfo2 라이브 조회 결과의 반복 정보 한 건 (infoname/infotext) */
public record PoiInfoItem(
        String infoname,
        String infotext
) {
}

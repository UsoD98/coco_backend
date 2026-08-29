package com.eodegano.cocobackend.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class PoiDetailResponseDto {
    private Long contentId;
    private Integer contentTypeId;
    private String title;
    private String tel;
    private String homepage;
    private String overview;
    private String firstimage;
    private String firstimage2;
    private String addr1;
    private String addr2;
    private BigDecimal mapx;
    private BigDecimal mapy;
    private Integer avgPrice;
    private List<PoiInfoItemDto> infoList;
    private boolean liked;
    private int totalLiked;
    private BigDecimal stars;
}

package com.eodegano.cocobackend.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PoiCurationItemDto {
    private Long contentId;
    private Integer contentTypeId;
    private String title;
    private BigDecimal mapx;
    private BigDecimal mapy;
    private String thumbnail;
    private Integer avgPrice;
}

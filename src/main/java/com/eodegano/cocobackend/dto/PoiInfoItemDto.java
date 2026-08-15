package com.eodegano.cocobackend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PoiInfoItemDto {
    private String infoname;
    private String infotext;
}

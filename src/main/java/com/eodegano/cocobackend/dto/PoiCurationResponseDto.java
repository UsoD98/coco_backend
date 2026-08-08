package com.eodegano.cocobackend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PoiCurationResponseDto {
    private boolean available;
    private List<PoiCurationItemDto> items;
}

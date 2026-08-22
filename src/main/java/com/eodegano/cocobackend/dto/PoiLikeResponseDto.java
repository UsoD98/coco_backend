package com.eodegano.cocobackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PoiLikeResponseDto {
    private boolean liked;
    private int totalLiked;
}

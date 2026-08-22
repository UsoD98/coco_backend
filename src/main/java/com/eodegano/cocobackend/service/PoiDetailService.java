package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.dto.PoiDetailResponseDto;

public interface PoiDetailService {
    PoiDetailResponseDto getPoiDetail(Long contentId, String userEmail);
}

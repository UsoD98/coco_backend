package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.dto.PoiCurationResponseDto;

public interface PoiCurationService {
    PoiCurationResponseDto getPoiList(String sigunguCode, Integer peopleCount, Integer contentTypeId);
}

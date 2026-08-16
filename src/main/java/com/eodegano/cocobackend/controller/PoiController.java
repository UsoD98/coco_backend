package com.eodegano.cocobackend.controller;

import com.eodegano.cocobackend.dto.ApiResponse;
import com.eodegano.cocobackend.dto.PoiCurationResponseDto;
import com.eodegano.cocobackend.dto.PoiDetailResponseDto;
import com.eodegano.cocobackend.dto.PoiLikeResponseDto;
import com.eodegano.cocobackend.service.PoiCurationService;
import com.eodegano.cocobackend.service.PoiDetailService;
import com.eodegano.cocobackend.service.PoiLikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/poi")
@RequiredArgsConstructor
@Slf4j
public class PoiController {

    private final PoiLikeService poiLikeService;
    private final PoiCurationService poiCurationService;
    private final PoiDetailService poiDetailService;

    @GetMapping
    public ResponseEntity<ApiResponse<PoiCurationResponseDto>> getPoiList(
            @RequestParam(required = true) String sigunguCode,
            @RequestParam(required = true) Integer peopleCount,
            @RequestParam(required = false) Integer contentTypeId) {
        PoiCurationResponseDto result = poiCurationService.getPoiList(sigunguCode, peopleCount, contentTypeId);
        return ResponseEntity.ok(ApiResponse.ok("POI 목록을 조회했습니다.", result));
    }

    @GetMapping("/{contentId}")
    public ResponseEntity<ApiResponse<PoiDetailResponseDto>> getPoiDetail(@PathVariable Long contentId) {
        PoiDetailResponseDto result = poiDetailService.getPoiDetail(contentId);
        return ResponseEntity.ok(ApiResponse.ok("POI 상세 정보를 조회했습니다.", result));
    }

    @PostMapping("/{contentId}/like")
    public ResponseEntity<ApiResponse<PoiLikeResponseDto>> toggleLike(
            @PathVariable Long contentId,
            Authentication authentication) {
        PoiLikeResponseDto result = poiLikeService.toggleLike(contentId, authentication.getName());
        String msg = result.isLiked() ? "좋아요가 추가되었습니다." : "좋아요가 취소되었습니다.";
        return ResponseEntity.ok(ApiResponse.ok(msg, result));
    }
}

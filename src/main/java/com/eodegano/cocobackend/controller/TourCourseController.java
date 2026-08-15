package com.eodegano.cocobackend.controller;

import com.eodegano.cocobackend.dto.ApiResponse;
import com.eodegano.cocobackend.dto.TourCourseGenerateRequestDto;
import com.eodegano.cocobackend.dto.TourCourseGenerateResponseDto;
import com.eodegano.cocobackend.dto.TourCourseListItemDto;
import com.eodegano.cocobackend.dto.TourCourseShareResponseDto;
import com.eodegano.cocobackend.dto.TourCourseTitleUpdateRequestDto;
import com.eodegano.cocobackend.dto.TourCourseUpdateRequestDto;
import com.eodegano.cocobackend.service.TourCourseService;

import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/tour-course")
@RequiredArgsConstructor
public class TourCourseController {

    private final TourCourseService tourCourseService;

    @PostMapping
    public ResponseEntity<ApiResponse<TourCourseGenerateResponseDto>> generateTourCourse(
            @RequestBody @Valid TourCourseGenerateRequestDto request,
            Authentication authentication
    ) {
        log.info("Received tour course generation request: {}", request);

        String email = (authentication != null) ? authentication.getName() : null;

        TourCourseGenerateResponseDto result = tourCourseService.generateTourCourse(request, email);

        log.info("Tour course generated successfully. CourseId: {}", result.getCourseId());
        return ResponseEntity.ok(ApiResponse.ok("여행 코스가 생성되었습니다.", result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TourCourseListItemDto>>> getCourseList(
            Authentication authentication) {
        List<TourCourseListItemDto> result = tourCourseService.getCourseList(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("코스 목록을 조회했습니다.", result));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<TourCourseShareResponseDto>> getCourseDetail(
            @PathVariable Long courseId,
            Authentication authentication) {
        TourCourseShareResponseDto result = tourCourseService.getCourseDetail(courseId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("코스 상세를 조회했습니다.", result));
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(
            @PathVariable Long courseId,
            Authentication authentication) {
        tourCourseService.deleteCourse(courseId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("코스가 삭제되었습니다."));
    }

    @GetMapping("/{courseId}/view")
    public ResponseEntity<ApiResponse<TourCourseShareResponseDto>> getCourseShareView(
            @PathVariable Long courseId) {
        TourCourseShareResponseDto result = tourCourseService.getShareView(courseId);
        return ResponseEntity.ok(ApiResponse.ok("코스 조회에 성공했습니다.", result));
    }

    @PatchMapping("/{courseId}/assign")
    public ResponseEntity<ApiResponse<Void>> assignCourse(
            @PathVariable Long courseId,
            Authentication authentication) {
        tourCourseService.assignCourse(courseId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("코스 저장 완료했습니다"));
    }

    @PatchMapping("/{courseId}/title")
    public ResponseEntity<ApiResponse<Void>> updateCourseTitle(
            @PathVariable Long courseId,
            @RequestBody @Valid TourCourseTitleUpdateRequestDto request,
            Authentication authentication
    ) {
        log.info("Updating title for courseId: {}", courseId);
        tourCourseService.updateCourseTitle(courseId, request.getTitle(), authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("코스 제목이 수정되었습니다."));
    }

    @PatchMapping("/{courseId}")
    public ResponseEntity<ApiResponse<TourCourseShareResponseDto>> updateCourse(
            @PathVariable Long courseId,
            @RequestBody @Valid TourCourseUpdateRequestDto request,
            Authentication authentication
    ) {
        log.info("Updating courseId: {}", courseId);
        TourCourseShareResponseDto result = tourCourseService.updateCourse(courseId, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("코스 수정 완료했습니다.", result));
    }
}

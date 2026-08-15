package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.dto.TourCourseGenerateRequestDto;
import com.eodegano.cocobackend.dto.TourCourseGenerateResponseDto;
import com.eodegano.cocobackend.dto.TourCourseListItemDto;
import com.eodegano.cocobackend.dto.TourCourseShareResponseDto;
import com.eodegano.cocobackend.dto.TourCourseUpdateRequestDto;

import java.util.List;

public interface TourCourseService {
    TourCourseGenerateResponseDto generateTourCourse(TourCourseGenerateRequestDto request, String email);
    void updateCourseTitle(Long courseId, String title, String userEmail);
    TourCourseShareResponseDto getShareView(Long courseId);
    void assignCourse(Long courseId, String userEmail);
    List<TourCourseListItemDto> getCourseList(String userEmail);
    TourCourseShareResponseDto getCourseDetail(Long courseId, String userEmail);
    void deleteCourse(Long courseId, String userEmail);
    TourCourseShareResponseDto updateCourse(Long courseId, TourCourseUpdateRequestDto request, String userEmail);
}

package com.eodegano.cocobackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TourCourseGenerateResponseDto {

    private Long courseId;
    private List<DailySchedule> schedule;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailySchedule {
        private LocalDate date;
        private List<PlaceInfo> places;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlaceInfo {
        private Integer seq;
        private LocalTime time;
        private String type;
        private Long contentId;
        private Integer durationMinutes;
        private String thumbnailImg;
        private String operatingHours;
        private Integer cost;
    }
}

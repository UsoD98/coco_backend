package com.eodegano.cocobackend.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
public class TourCourseShareResponseDto {

    private Long courseId;
    private String title;
    private int peopleCount;
    private LocalDate startDate;
    private LocalDate endDate;
    private String transport;
    private List<String> theme;
    private List<DailySchedule> schedule;

    @Getter
    @Builder
    public static class DailySchedule {
        private LocalDate date;
        private List<PlaceInfo> places;
    }

    @Getter
    @Builder
    public static class PlaceInfo {
        private int seq;
        private LocalTime time;
        private String type;
        private Long contentId;
        private String placeName;
        private Integer durationMinutes;
        private String thumbnailImg;
        private String operatingHours;
        private Integer cost;
        private BigDecimal mapx;
        private BigDecimal mapy;
    }
}

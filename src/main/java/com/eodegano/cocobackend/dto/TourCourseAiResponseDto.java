package com.eodegano.cocobackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TourCourseAiResponseDto {

    private List<DailyPlan> schedule;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPlan {
        private LocalDate date;
        private List<PlaceVisit> places;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlaceVisit {
        private Integer seq;
        private LocalTime time;
        private String type;
        private Long contentId;
        private Integer durationMinutes;
    }
}

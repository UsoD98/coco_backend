package com.eodegano.cocobackend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TourCourseUpdateRequestDto {

    @NotEmpty(message = "수정할 일정이 없습니다")
    @Valid
    private List<DailyScheduleUpdate> schedule;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DailyScheduleUpdate {

        @NotNull(message = "날짜는 필수입니다")
        private LocalDate date;

        @NotEmpty(message = "일정에 최소 한 개의 장소가 필요합니다")
        @Valid
        private List<PlaceUpdate> places;
    }

    // contentName·thumbnailImg·operatingHours·cost는 조회 전용 표시 필드라 저장하지 않고 무시한다
    // (TourAPI 라이브 조회로 재조립, TourCourseAiResponseDto.PlaceVisit과 동일한 전략)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlaceUpdate {

        @NotNull(message = "seq는 필수입니다")
        private Integer seq;

        @NotNull(message = "time은 필수입니다")
        private LocalTime time;

        @NotNull(message = "type은 필수입니다")
        private String type;

        @NotNull(message = "contentId는 필수입니다")
        private Long contentId;

        private Integer durationMinutes;
    }
}

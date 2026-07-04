package com.eodegano.cocobackend.dto;

import com.eodegano.cocobackend.domain.enums.TransportType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TourCourseGenerateRequestDto {

    @NotNull(message = "인원수는 필수입니다")
    @Min(value = 1, message = "인원수는 1명 이상이어야 합니다")
    @Max(value = 100, message = "인원수는 100명 이하여야 합니다")
    private Integer peopleCount;

    @NotNull(message = "시작 날짜는 필수입니다")
    @FutureOrPresent(message = "시작 날짜는 오늘 이후여야 합니다")
    private LocalDate startDate;

    @NotNull(message = "종료 날짜는 필수입니다")
    @FutureOrPresent(message = "종료 날짜는 오늘 이후여야 합니다")
    private LocalDate endDate;

    @NotNull(message = "이동수단은 필수입니다")
    private TransportType transport;

    @NotEmpty(message = "테마는 최소 1개 이상 선택해야 합니다")
    private List<String> theme;

    private List<String> sigunguCodes;

    @AssertTrue(message = "종료 날짜는 시작 날짜 이후여야 합니다")
    public boolean isValidDateRange() {
        if (startDate == null || endDate == null) {
            return true;
        }
        return !endDate.isBefore(startDate);
    }
}

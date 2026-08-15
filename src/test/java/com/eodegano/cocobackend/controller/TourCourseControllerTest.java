package com.eodegano.cocobackend.controller;

import com.eodegano.cocobackend.dto.TourCourseShareResponseDto;
import com.eodegano.cocobackend.security.JwtProvider;
import com.eodegano.cocobackend.service.TourCourseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TourCourseController 슬라이스(웹 계층) 테스트 — GBC020 코스 수정.
 * 서비스는 Mock으로 대체하고 Controller → @Valid → GlobalExceptionHandler → JSON 응답 래핑까지만 검증한다.
 * addFilters=false로 서블릿 필터 체인(JWT/Security)을 생략했으므로 인가 규칙(403) 검증은 범위 밖이다
 * (SecurityConfig 별도 검증 필요, AccessDeniedException은 서비스 단위 테스트에서 검증).
 */
@WebMvcTest(TourCourseController.class)
@AutoConfigureMockMvc(addFilters = false)
class TourCourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private TourCourseService tourCourseService;

    // @WebMvcTest는 Filter 빈(JwtAuthenticationFilter)도 컨텍스트에 올리므로 그 의존성만 목으로 채워준다.
    @MockitoBean private JwtProvider jwtProvider;

    private static final String VALID_BODY = """
            {
              "schedule": [
                {
                  "date": "2026-08-15",
                  "places": [
                    {
                      "seq": 1,
                      "time": "09:00:00",
                      "type": "ATTRACTION",
                      "contentId": 2763479,
                      "contentName": "상주 감꽃마을 서울시캠핑장",
                      "durationMinutes": 120,
                      "thumbnailImg": "http://tong.visitkorea.or.kr/cms/resource/72/2763372_image2_1.jpg",
                      "operatingHours": null,
                      "cost": 5000
                    }
                  ]
                }
              ]
            }
            """;

    @Test
    @DisplayName("PATCH /api/v1/tour-course/{courseId} - 성공 200")
    void updateCourseSuccess() throws Exception {
        TourCourseShareResponseDto response = TourCourseShareResponseDto.builder()
                .courseId(1L)
                .title("경주 힐링 코스")
                .peopleCount(2)
                .startDate(LocalDate.of(2026, 8, 15))
                .endDate(LocalDate.of(2026, 8, 16))
                .transport("CAR")
                .schedule(java.util.List.of(TourCourseShareResponseDto.DailySchedule.builder()
                        .date(LocalDate.of(2026, 8, 15))
                        .places(java.util.List.of(TourCourseShareResponseDto.PlaceInfo.builder()
                                .seq(1)
                                .time(LocalTime.of(9, 0))
                                .type("ATTRACTION")
                                .contentId(2763479L)
                                .placeName("상주 감꽃마을 서울시캠핑장")
                                .durationMinutes(120)
                                .cost(5000)
                                .build()))
                        .build()))
                .build();
        given(tourCourseService.updateCourse(eq(1L), any(), eq("test@test.com"))).willReturn(response);

        mockMvc.perform(patch("/api/v1/tour-course/1")
                        .contentType("application/json")
                        .content(VALID_BODY)
                        .principal(new UsernamePasswordAuthenticationToken("test@test.com", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.msg").value("코스 수정 완료했습니다."))
                .andExpect(jsonPath("$.data.courseId").value(1))
                .andExpect(jsonPath("$.data.schedule[0].places[0].contentId").value(2763479))
                .andExpect(jsonPath("$.data.schedule[0].places[0].placeName").value("상주 감꽃마을 서울시캠핑장"));
    }

    @Test
    @DisplayName("PATCH /api/v1/tour-course/{courseId} - schedule 누락 시 400")
    void updateCourseFailWithMissingSchedule() throws Exception {
        mockMvc.perform(patch("/api/v1/tour-course/1")
                        .contentType("application/json")
                        .content("{}")
                        .principal(new UsernamePasswordAuthenticationToken("test@test.com", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
    }

    @Test
    @DisplayName("PATCH /api/v1/tour-course/{courseId} - 존재하지 않는 코스 → 404")
    void updateCourseFailWithNotFound() throws Exception {
        given(tourCourseService.updateCourse(eq(999L), any(), eq("test@test.com")))
                .willThrow(new NoSuchElementException("존재하지 않는 코스입니다: 999"));

        mockMvc.perform(patch("/api/v1/tour-course/999")
                        .contentType("application/json")
                        .content(VALID_BODY)
                        .principal(new UsernamePasswordAuthenticationToken("test@test.com", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404"))
                .andExpect(jsonPath("$.msg").value("존재하지 않는 코스입니다: 999"));
    }

    @Test
    @DisplayName("PATCH /api/v1/tour-course/{courseId} - 날짜/타입/contentId 검증 실패 → 400")
    void updateCourseFailWithBusinessValidation() throws Exception {
        given(tourCourseService.updateCourse(eq(1L), any(), eq("test@test.com")))
                .willThrow(new IllegalArgumentException("일정 날짜가 코스 기간을 벗어났습니다: 2026-08-14"));

        mockMvc.perform(patch("/api/v1/tour-course/1")
                        .contentType("application/json")
                        .content(VALID_BODY)
                        .principal(new UsernamePasswordAuthenticationToken("test@test.com", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.msg").value("일정 날짜가 코스 기간을 벗어났습니다: 2026-08-14"));
    }
}

package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.client.GroqApiClient;
import com.eodegano.cocobackend.domain.TourCourseUserDefined;
import com.eodegano.cocobackend.domain.TourCourseUserDefinedDetail;
import com.eodegano.cocobackend.domain.User;
import com.eodegano.cocobackend.dto.TourCourseShareResponseDto;
import com.eodegano.cocobackend.dto.TourCourseUpdateRequestDto;
import com.eodegano.cocobackend.repository.PoiRatingRepository;
import com.eodegano.cocobackend.repository.TourCourseUserDefinedDetailRepository;
import com.eodegano.cocobackend.repository.TourCourseUserDefinedRepository;
import com.eodegano.cocobackend.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/** GBC020 코스 수정 단위 테스트 */
@ExtendWith(MockitoExtension.class)
class TourCourseServiceImplTest {

    @Mock private GroqApiClient groqApiClient;
    @Mock private TourLiveDataService tourLiveDataService;
    @Mock private PoiRatingRepository poiRatingRepository;
    @Mock private TourCourseUserDefinedRepository tourCourseUserDefinedRepository;
    @Mock private TourCourseUserDefinedDetailRepository tourCourseUserDefinedDetailRepository;
    @Mock private UserRepository userRepository;

    private TourCourseServiceImpl tourCourseService;

    private static final Long COURSE_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final String OWNER_EMAIL = "owner@test.com";
    private static final LocalDate START_DATE = LocalDate.of(2026, 8, 15);
    private static final LocalDate END_DATE = LocalDate.of(2026, 8, 16);

    @BeforeEach
    void setUp() {
        // ObjectMapper는 Jackson 3.x(tools.jackson) 실 인스턴스를 사용 — theme JSON 파싱만 사용되므로 목 불필요
        tourCourseService = new TourCourseServiceImpl(
                groqApiClient, tourLiveDataService, poiRatingRepository,
                tourCourseUserDefinedRepository, tourCourseUserDefinedDetailRepository,
                userRepository, new ObjectMapper());
    }

    private TourCourseUserDefined ownedCourse() {
        return TourCourseUserDefined.builder()
                .id(COURSE_ID)
                .userId(OWNER_ID)
                .peopleCount(2)
                .startDate(START_DATE)
                .endDate(END_DATE)
                .transport("CAR")
                .title("경주 힐링 코스")
                .theme("[\"자연\"]")
                .build();
    }

    private User owner() {
        return User.builder()
                .id(OWNER_ID)
                .email(OWNER_EMAIL)
                .nickname("owner")
                .role("USER")
                .build();
    }

    private TourCourseUpdateRequestDto validRequest() {
        TourCourseUpdateRequestDto.PlaceUpdate place =
                new TourCourseUpdateRequestDto.PlaceUpdate(1, LocalTime.of(9, 0), "ATTRACTION", 100L, 120, 5000);
        TourCourseUpdateRequestDto.DailyScheduleUpdate day =
                new TourCourseUpdateRequestDto.DailyScheduleUpdate(START_DATE, List.of(place));
        return new TourCourseUpdateRequestDto(List.of(day));
    }

    @Test
    @DisplayName("성공 - 기존 일정을 삭제하고 요청 내용으로 재삽입 후 재조립된 상세를 반환")
    void updateCourseSuccess() {
        TourCourseUserDefined course = ownedCourse();
        List<TourCourseUserDefinedDetail> oldDetails = List.of(TourCourseUserDefinedDetail.builder()
                .id(999L).tourCourseId(COURSE_ID).date(START_DATE).seq(1)
                .time(LocalTime.of(8, 0)).type("ATTRACTION").contentId(200L).build());
        TourCourseUserDefinedDetail newDetail = TourCourseUserDefinedDetail.builder()
                .id(1000L).tourCourseId(COURSE_ID).date(START_DATE).seq(1)
                .time(LocalTime.of(9, 0)).durationMinutes(120).type("ATTRACTION").contentId(100L).build();

        given(tourCourseUserDefinedRepository.findById(COURSE_ID)).willReturn(Optional.of(course));
        given(userRepository.findByEmailAndDeletedAtIsNull(OWNER_EMAIL)).willReturn(Optional.of(owner()));
        given(tourLiveDataService.getAllCandidates()).willReturn(List.of(
                new PoiSummary(100L, 12, "상주 감꽃마을 서울시캠핑장", "http://img.jpg",
                        new BigDecimal("129.0"), new BigDecimal("35.0"), "47130")));
        given(tourCourseUserDefinedDetailRepository.findByTourCourseId(COURSE_ID))
                .willReturn(oldDetails, List.of(newDetail));
        given(tourLiveDataService.getDetail(100L, 12))
                .willReturn(new PoiDetail(100L, 12, "상시 개방", 5000));

        TourCourseShareResponseDto result = tourCourseService.updateCourse(COURSE_ID, validRequest(), OWNER_EMAIL);

        verify(tourCourseUserDefinedDetailRepository).deleteAll(oldDetails);
        verify(tourCourseUserDefinedDetailRepository).flush();
        verify(tourCourseUserDefinedDetailRepository).saveAll(anyList());

        assertThat(result.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(result.getSchedule()).hasSize(1);
        TourCourseShareResponseDto.PlaceInfo place = result.getSchedule().get(0).getPlaces().get(0);
        assertThat(place.getSeq()).isEqualTo(1);
        assertThat(place.getTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(place.getType()).isEqualTo("ATTRACTION");
        assertThat(place.getContentId()).isEqualTo(100L);
        assertThat(place.getPlaceName()).isEqualTo("상주 감꽃마을 서울시캠핑장");
        assertThat(place.getDurationMinutes()).isEqualTo(120);
        assertThat(place.getThumbnailImg()).isEqualTo("http://img.jpg");
        assertThat(place.getOperatingHours()).isEqualTo("상시 개방");
        assertThat(place.getCost()).isEqualTo(5000);
    }

    @Test
    @DisplayName("실패 - 존재하지 않는 코스 → NoSuchElementException(404 매핑)")
    void updateCourseFailWithCourseNotFound() {
        given(tourCourseUserDefinedRepository.findById(COURSE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tourCourseService.updateCourse(COURSE_ID, validRequest(), OWNER_EMAIL))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("실패 - 존재하지 않는 사용자 → NoSuchElementException(404 매핑)")
    void updateCourseFailWithUserNotFound() {
        given(tourCourseUserDefinedRepository.findById(COURSE_ID)).willReturn(Optional.of(ownedCourse()));
        given(userRepository.findByEmailAndDeletedAtIsNull(OWNER_EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tourCourseService.updateCourse(COURSE_ID, validRequest(), OWNER_EMAIL))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("실패 - 본인 코스가 아님 → AccessDeniedException(403 매핑)")
    void updateCourseFailWithNotOwner() {
        TourCourseUserDefined othersCourse = TourCourseUserDefined.builder()
                .id(COURSE_ID).userId(999L).peopleCount(2)
                .startDate(START_DATE).endDate(END_DATE).transport("CAR").theme("[]").build();

        given(tourCourseUserDefinedRepository.findById(COURSE_ID)).willReturn(Optional.of(othersCourse));
        given(userRepository.findByEmailAndDeletedAtIsNull(OWNER_EMAIL)).willReturn(Optional.of(owner()));

        assertThatThrownBy(() -> tourCourseService.updateCourse(COURSE_ID, validRequest(), OWNER_EMAIL))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("실패 - 일정 날짜가 코스 기간을 벗어남 → IllegalArgumentException(400 매핑)")
    void updateCourseFailWithDateOutOfRange() {
        given(tourCourseUserDefinedRepository.findById(COURSE_ID)).willReturn(Optional.of(ownedCourse()));
        given(userRepository.findByEmailAndDeletedAtIsNull(OWNER_EMAIL)).willReturn(Optional.of(owner()));

        TourCourseUpdateRequestDto.PlaceUpdate place =
                new TourCourseUpdateRequestDto.PlaceUpdate(1, LocalTime.of(9, 0), "ATTRACTION", 100L, 120, 5000);
        TourCourseUpdateRequestDto.DailyScheduleUpdate day = new TourCourseUpdateRequestDto.DailyScheduleUpdate(
                START_DATE.minusDays(1), List.of(place));
        TourCourseUpdateRequestDto request = new TourCourseUpdateRequestDto(List.of(day));

        assertThatThrownBy(() -> tourCourseService.updateCourse(COURSE_ID, request, OWNER_EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("일정 날짜가 코스 기간을 벗어났습니다");
    }

    @Test
    @DisplayName("실패 - 유효하지 않은 장소 타입 → IllegalArgumentException(400 매핑)")
    void updateCourseFailWithInvalidType() {
        given(tourCourseUserDefinedRepository.findById(COURSE_ID)).willReturn(Optional.of(ownedCourse()));
        given(userRepository.findByEmailAndDeletedAtIsNull(OWNER_EMAIL)).willReturn(Optional.of(owner()));

        TourCourseUpdateRequestDto.PlaceUpdate place =
                new TourCourseUpdateRequestDto.PlaceUpdate(1, LocalTime.of(9, 0), "INVALID_TYPE", 100L, 120, 5000);
        TourCourseUpdateRequestDto.DailyScheduleUpdate day =
                new TourCourseUpdateRequestDto.DailyScheduleUpdate(START_DATE, List.of(place));
        TourCourseUpdateRequestDto request = new TourCourseUpdateRequestDto(List.of(day));

        assertThatThrownBy(() -> tourCourseService.updateCourse(COURSE_ID, request, OWNER_EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 장소 타입입니다");
    }

    @Test
    @DisplayName("실패 - TourAPI 후보에 없는 contentId → IllegalArgumentException(400 매핑)")
    void updateCourseFailWithUnknownContentId() {
        given(tourCourseUserDefinedRepository.findById(COURSE_ID)).willReturn(Optional.of(ownedCourse()));
        given(userRepository.findByEmailAndDeletedAtIsNull(OWNER_EMAIL)).willReturn(Optional.of(owner()));
        given(tourLiveDataService.getAllCandidates()).willReturn(List.of());

        assertThatThrownBy(() -> tourCourseService.updateCourse(COURSE_ID, validRequest(), OWNER_EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 장소 ID가 포함되어 있습니다");
    }
}

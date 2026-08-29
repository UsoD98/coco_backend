package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.client.GroqApiClient;
import com.eodegano.cocobackend.domain.TourCourseUserDefined;
import com.eodegano.cocobackend.domain.TourCourseUserDefinedDetail;
import com.eodegano.cocobackend.domain.User;
import com.eodegano.cocobackend.domain.enums.TransportType;
import com.eodegano.cocobackend.dto.TourCourseAiResponseDto;
import com.eodegano.cocobackend.dto.TourCourseGenerateRequestDto;
import com.eodegano.cocobackend.dto.TourCourseGenerateResponseDto;
import com.eodegano.cocobackend.dto.TourCourseShareResponseDto;
import com.eodegano.cocobackend.dto.TourCourseUpdateRequestDto;
import com.eodegano.cocobackend.exception.AiCourseGenerationException;
import com.eodegano.cocobackend.repository.PoiRatingRepository;
import com.eodegano.cocobackend.repository.TourCourseUserDefinedDetailRepository;
import com.eodegano.cocobackend.repository.TourCourseUserDefinedRepository;
import com.eodegano.cocobackend.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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

    private TourCourseGenerateRequestDto validGenerateRequest() {
        return TourCourseGenerateRequestDto.builder()
                .peopleCount(2)
                .startDate(START_DATE)
                .endDate(END_DATE)
                .transport(TransportType.CAR)
                .theme(List.of("자연"))
                .sigunguCodes(List.of("47130"))
                .build();
    }

    private PoiSummary candidate() {
        return new PoiSummary(100L, 12, "불국사", "http://img.jpg",
                new BigDecimal("129.0"), new BigDecimal("35.0"), "47130");
    }

    private TourCourseAiResponseDto aiResponse(LocalDate placeDate) {
        TourCourseAiResponseDto.PlaceVisit place =
                new TourCourseAiResponseDto.PlaceVisit(1, LocalTime.of(9, 0), "ATTRACTION", 100L, 120);
        TourCourseAiResponseDto.DailyPlan day =
                new TourCourseAiResponseDto.DailyPlan(placeDate, List.of(place));
        return new TourCourseAiResponseDto(List.of(day));
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
        assertThat(place.getMapx()).isEqualByComparingTo(new BigDecimal("129.0"));
        assertThat(place.getMapy()).isEqualByComparingTo(new BigDecimal("35.0"));
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

    @Test
    @DisplayName("성공 - AI 응답을 저장하고 설계된 응답 스펙(courseId·schedule·PlaceInfo)대로 반환")
    void generateTourCourseSuccess() {
        PoiSummary candidate = candidate();
        given(tourLiveDataService.getAllCandidates()).willReturn(List.of(candidate));
        given(tourLiveDataService.getDetail(100L, 12))
                .willReturn(new PoiDetail(100L, 12, "상시 개방", 5000));
        given(groqApiClient.generateTourCourse(anyString(), anyString()))
                .willReturn(aiResponse(START_DATE));
        given(tourCourseUserDefinedRepository.save(any())).willReturn(ownedCourse());

        TourCourseGenerateResponseDto result =
                tourCourseService.generateTourCourse(validGenerateRequest(), null);

        verify(tourCourseUserDefinedRepository).save(any());
        verify(tourCourseUserDefinedDetailRepository).saveAll(anyList());

        assertThat(result.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(result.getSchedule()).hasSize(1);
        assertThat(result.getSchedule().get(0).getDate()).isEqualTo(START_DATE);

        TourCourseGenerateResponseDto.PlaceInfo place = result.getSchedule().get(0).getPlaces().get(0);
        assertThat(place.getSeq()).isEqualTo(1);
        assertThat(place.getTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(place.getType()).isEqualTo("ATTRACTION");
        assertThat(place.getContentId()).isEqualTo(100L);
        assertThat(place.getContentName()).isEqualTo("불국사");
        assertThat(place.getDurationMinutes()).isEqualTo(120);
        assertThat(place.getThumbnailImg()).isEqualTo("http://img.jpg");
        assertThat(place.getOperatingHours()).isEqualTo("상시 개방");
        assertThat(place.getCost()).isEqualTo(5000);
    }

    @Test
    @DisplayName("실패 - Groq 호출 실패 시 AiCourseGenerationException이 감싸지지 않고 그대로 전파됨")
    void generateTourCourseFailWhenGroqThrows() {
        given(tourLiveDataService.getAllCandidates()).willReturn(List.of(candidate()));
        AiCourseGenerationException groqError = new AiCourseGenerationException(
                AiCourseGenerationException.ErrorCode.RATE_LIMITED,
                "Groq API rate limit 초과로 요청에 실패했습니다. 잠시 후 다시 시도해주세요.", true);
        given(groqApiClient.generateTourCourse(anyString(), anyString())).willThrow(groqError);

        assertThatThrownBy(() -> tourCourseService.generateTourCourse(validGenerateRequest(), null))
                .isSameAs(groqError);
    }

    @Test
    @DisplayName("실패 - AI가 요청 범위를 벗어난 날짜로 일정을 생성 → AiCourseGenerationException(RESPONSE_VALIDATION_FAILED)")
    void generateTourCourseFailWhenAiResponseInvalid() {
        given(tourLiveDataService.getAllCandidates()).willReturn(List.of(candidate()));
        given(groqApiClient.generateTourCourse(anyString(), anyString()))
                .willReturn(aiResponse(START_DATE.minusDays(1)));

        AiCourseGenerationException ex = catchThrowableOfType(
                AiCourseGenerationException.class,
                () -> tourCourseService.generateTourCourse(validGenerateRequest(), null));

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(AiCourseGenerationException.ErrorCode.RESPONSE_VALIDATION_FAILED);
        assertThat(ex.isRetryable()).isTrue();
        assertThat(ex.getMessage()).contains("일정 날짜가 요청 범위를 벗어났습니다");
    }

    @Test
    @DisplayName("성공 - CAR 이동수단 요청 시 AI 후보 데이터에 geo-group(g) 태그가 포함됨")
    void fetchPlacesDataIncludesGeoGroupTagForCar() {
        PoiSummary near = candidate(); // 129.0, 35.0
        given(tourLiveDataService.getAllCandidates()).willReturn(List.of(near));
        given(tourLiveDataService.getDetail(100L, 12))
                .willReturn(new PoiDetail(100L, 12, "상시 개방", 5000));
        given(groqApiClient.generateTourCourse(anyString(), anyString()))
                .willReturn(aiResponse(START_DATE));
        given(tourCourseUserDefinedRepository.save(any())).willReturn(ownedCourse());

        tourCourseService.generateTourCourse(validGenerateRequest(), null);

        ArgumentCaptor<String> placesDataCaptor = ArgumentCaptor.forClass(String.class);
        verify(groqApiClient).generateTourCourse(placesDataCaptor.capture(), anyString());
        assertThat(placesDataCaptor.getValue()).contains("\"g\":");
    }

    @Test
    @DisplayName("실패 - 같은 날 연속 방문지 간 실제 좌표 거리가 CAR 한계(2시간 상당)를 초과 → RESPONSE_VALIDATION_FAILED")
    void generateTourCourseFailWhenTravelDistanceExceedsLimit() {
        PoiSummary near = new PoiSummary(100L, 12, "불국사", "http://img.jpg",
                new BigDecimal("129.0"), new BigDecimal("35.0"), "47130");
        PoiSummary far = new PoiSummary(200L, 12, "먼 관광지", "http://img.jpg",
                new BigDecimal("127.0"), new BigDecimal("37.0"), "47130");
        given(tourLiveDataService.getAllCandidates()).willReturn(List.of(near, far));

        TourCourseAiResponseDto.PlaceVisit place1 =
                new TourCourseAiResponseDto.PlaceVisit(1, LocalTime.of(9, 0), "ATTRACTION", 100L, 120);
        TourCourseAiResponseDto.PlaceVisit place2 =
                new TourCourseAiResponseDto.PlaceVisit(2, LocalTime.of(12, 0), "ATTRACTION", 200L, 120);
        TourCourseAiResponseDto.DailyPlan day =
                new TourCourseAiResponseDto.DailyPlan(START_DATE, List.of(place1, place2));
        given(groqApiClient.generateTourCourse(anyString(), anyString()))
                .willReturn(new TourCourseAiResponseDto(List.of(day)));

        AiCourseGenerationException ex = catchThrowableOfType(
                AiCourseGenerationException.class,
                () -> tourCourseService.generateTourCourse(validGenerateRequest(), null));

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(AiCourseGenerationException.ErrorCode.RESPONSE_VALIDATION_FAILED);
        assertThat(ex.isRetryable()).isTrue();
        assertThat(ex.getMessage()).contains("이동거리");
    }
}

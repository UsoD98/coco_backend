package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.client.GroqApiClient;
import com.eodegano.cocobackend.domain.PoiRating;
import com.eodegano.cocobackend.domain.TourCourseUserDefined;
import com.eodegano.cocobackend.domain.TourCourseUserDefinedDetail;
import com.eodegano.cocobackend.domain.User;
import com.eodegano.cocobackend.domain.enums.PlaceType;
import com.eodegano.cocobackend.domain.enums.TransportType;
import com.eodegano.cocobackend.dto.TourCourseAiResponseDto;
import com.eodegano.cocobackend.dto.TourCourseGenerateRequestDto;
import com.eodegano.cocobackend.dto.TourCourseGenerateResponseDto;
import com.eodegano.cocobackend.dto.TourCourseListItemDto;
import com.eodegano.cocobackend.dto.TourCourseShareResponseDto;
import com.eodegano.cocobackend.dto.TourCourseUpdateRequestDto;
import com.eodegano.cocobackend.exception.AiCourseGenerationException;
import com.eodegano.cocobackend.exception.AiCourseGenerationException.ErrorCode;
import com.eodegano.cocobackend.repository.PoiRatingRepository;
import com.eodegano.cocobackend.repository.TourCourseUserDefinedDetailRepository;
import com.eodegano.cocobackend.repository.TourCourseUserDefinedRepository;
import com.eodegano.cocobackend.repository.UserRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * v0.5.0 — 로컬 tour/detail 테이블 대신 TourAPI 라이브 조회({@link TourLiveDataService}, Caffeine 캐시 TTL 6h)를 사용.
 * 별점(stars)·좋아요(likes)는 앱 자체 데이터라 {@link PoiRatingRepository}에서 별도 조회.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TourCourseServiceImpl implements TourCourseService {

    private static final int MEALS_PER_DAY = 2;
    private static final int MAX_TRIP_DAYS = 7;
    private static final double TIER_A_RATIO = 0.7;

    // 이동거리 클러스터링/검증 기준 (v0.6.8 — 도보는 애매성으로 제외, 대중교통/차만 적용)
    // CAR: 2시간 이내 이동을 목표로, 실효 평균속도(정체·국도 등 고려) 약 60km/h 가정 -> 최대 120km
    private static final double CAR_MAX_LEG_KM = 120.0;
    private static final double CAR_CLUSTER_RADIUS_KM = 60.0;
    // PUBLIC_TRANSPORT: 환승/대기 포함 실효 평균속도 약 25km/h 가정 -> 최대 50km
    private static final double TRANSIT_MAX_LEG_KM = 50.0;
    private static final double TRANSIT_CLUSTER_RADIUS_KM = 25.0;

    private static final int QUOTA_FOOD          = MEALS_PER_DAY * MAX_TRIP_DAYS;
    private static final int QUOTA_ACCOMMODATION =  4;
    private static final int QUOTA_ATTRACTION    = 12;
    private static final int QUOTA_CULTURE       =  5;
    private static final int QUOTA_LEPORTS       =  3;
    private static final int QUOTA_SHOPPING      =  2;
    private static final int QUOTA_EVENT         =  2;

    // type별 기본 예상 비용 (원)
    private static final Map<String, Integer> DEFAULT_COST_BY_TYPE = Map.of(
        "ATTRACTION", 5000,
        "FOOD",       12000,
        "CULTURE",    3000,
        "LEPORTS",    20000,
        "SHOPPING",   0,
        "EVENT",      0
    );

    private final GroqApiClient groqApiClient;
    private final TourLiveDataService tourLiveDataService;
    private final PoiRatingRepository poiRatingRepository;
    private final TourCourseUserDefinedRepository tourCourseUserDefinedRepository;
    private final TourCourseUserDefinedDetailRepository tourCourseUserDefinedDetailRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public TourCourseGenerateResponseDto generateTourCourse(TourCourseGenerateRequestDto request, String email) {
        Long userId = (email != null)
                ? userRepository.findByEmailAndDeletedAtIsNull(email)
                        .map(u -> u.getId())
                        .orElse(null)
                : null;

        log.info("Generating tour course for user: {}, request: {}", userId, request);

        String placesData = fetchPlacesData(request.getSigunguCodes(), request.getTransport());
        String userRequest = buildUserRequest(request);
        TourCourseAiResponseDto aiResponse = groqApiClient.generateTourCourse(placesData, userRequest);
        validateAiResponse(aiResponse, request.getStartDate(), request.getEndDate(), request.getTransport());
        TourCourseUserDefined savedCourse = saveTourCourse(request, userId, aiResponse);
        return buildGenerateResponse(savedCourse.getId(), aiResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TourCourseShareResponseDto getShareView(Long courseId) {
        TourCourseUserDefined course = tourCourseUserDefinedRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 코스입니다"));
        return buildCourseResponse(course);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TourCourseListItemDto> getCourseList(String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 사용자입니다"));

        return tourCourseUserDefinedRepository.findByUserId(user.getId()).stream()
                .map(course -> TourCourseListItemDto.builder()
                        .courseId(course.getId())
                        .title(course.getTitle())
                        .peopleCount(course.getPeopleCount())
                        .startDate(course.getStartDate())
                        .endDate(course.getEndDate())
                        .transport(course.getTransport())
                        .theme(parseTheme(course.getTheme()))
                        .createdAt(course.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TourCourseShareResponseDto getCourseDetail(Long courseId, String userEmail) {
        TourCourseUserDefined course = tourCourseUserDefinedRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 코스입니다"));

        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 사용자입니다"));

        if (!user.getId().equals(course.getUserId())) {
            throw new AccessDeniedException("해당 코스에 접근할 권한이 없습니다");
        }

        return buildCourseResponse(course);
    }

    @Override
    @Transactional
    public void deleteCourse(Long courseId, String userEmail) {
        TourCourseUserDefined course = tourCourseUserDefinedRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 코스입니다"));

        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 사용자입니다"));

        if (!user.getId().equals(course.getUserId())) {
            throw new AccessDeniedException("해당 코스를 삭제할 권한이 없습니다");
        }

        tourCourseUserDefinedDetailRepository.deleteAll(
                tourCourseUserDefinedDetailRepository.findByTourCourseId(courseId));
        tourCourseUserDefinedRepository.delete(course);
    }

    @Override
    @Transactional
    public void assignCourse(Long courseId, String userEmail) {
        TourCourseUserDefined course = tourCourseUserDefinedRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 코스입니다"));

        if (course.getUserId() != null) {
            throw new AccessDeniedException("이미 소유자가 있는 코스입니다");
        }

        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 사용자입니다"));

        course.assignUser(user.getId());
    }

    @Override
    @Transactional
    public void updateCourseTitle(Long courseId, String title, String userEmail) {
        TourCourseUserDefined course = tourCourseUserDefinedRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 코스입니다: " + courseId));

        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 사용자입니다"));

        if (!user.getId().equals(course.getUserId())) {
            throw new AccessDeniedException("해당 코스를 수정할 권한이 없습니다");
        }

        course.updateTitle(title);
    }

    @Override
    @Transactional
    public TourCourseShareResponseDto updateCourse(Long courseId, TourCourseUpdateRequestDto request, String userEmail) {
        TourCourseUserDefined course = tourCourseUserDefinedRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 코스입니다: " + courseId));

        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 사용자입니다"));

        if (!user.getId().equals(course.getUserId())) {
            throw new AccessDeniedException("해당 코스를 수정할 권한이 없습니다");
        }

        validateUpdateRequest(request, course.getStartDate(), course.getEndDate());

        List<TourCourseUserDefinedDetail> oldDetails =
                tourCourseUserDefinedDetailRepository.findByTourCourseId(courseId);
        tourCourseUserDefinedDetailRepository.deleteAll(oldDetails);
        tourCourseUserDefinedDetailRepository.flush();

        List<TourCourseUserDefinedDetail> newDetails = new ArrayList<>();
        for (TourCourseUpdateRequestDto.DailyScheduleUpdate day : request.getSchedule()) {
            for (TourCourseUpdateRequestDto.PlaceUpdate place : day.getPlaces()) {
                newDetails.add(TourCourseUserDefinedDetail.builder()
                        .tourCourseId(courseId)
                        .date(day.getDate())
                        .seq(place.getSeq())
                        .time(place.getTime())
                        .durationMinutes(place.getDurationMinutes())
                        .type(place.getType())
                        .contentId(place.getContentId())
                        .cost(place.getCost())
                        .build());
            }
        }
        tourCourseUserDefinedDetailRepository.saveAll(newDetails);

        return buildCourseResponse(course);
    }

    private void validateUpdateRequest(TourCourseUpdateRequestDto request, LocalDate startDate, LocalDate endDate) {
        Set<Long> contentIds = new HashSet<>();
        for (TourCourseUpdateRequestDto.DailyScheduleUpdate day : request.getSchedule()) {
            if (day.getDate().isBefore(startDate) || day.getDate().isAfter(endDate)) {
                throw new IllegalArgumentException("일정 날짜가 코스 기간을 벗어났습니다: " + day.getDate());
            }
            for (TourCourseUpdateRequestDto.PlaceUpdate place : day.getPlaces()) {
                contentIds.add(place.getContentId());
                try {
                    PlaceType.valueOf(place.getType());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("유효하지 않은 장소 타입입니다: " + place.getType());
                }
            }
        }

        Set<Long> knownContentIds = tourLiveDataService.getAllCandidates().stream()
                .map(PoiSummary::contentId)
                .collect(Collectors.toSet());

        if (!knownContentIds.containsAll(contentIds)) {
            Set<Long> unknown = new HashSet<>(contentIds);
            unknown.removeAll(knownContentIds);
            log.error("존재하지 않는 장소 ID가 포함되어 있습니다: {}", unknown);
            throw new IllegalArgumentException("존재하지 않는 장소 ID가 포함되어 있습니다");
        }
    }

    // ── 코스 응답 빌더 ─────────────────────────────────────────────────────────

    private TourCourseGenerateResponseDto buildGenerateResponse(Long courseId, TourCourseAiResponseDto aiResponse) {
        List<Long> allContentIds = aiResponse.getSchedule().stream()
                .flatMap(day -> day.getPlaces().stream())
                .map(TourCourseAiResponseDto.PlaceVisit::getContentId)
                .distinct()
                .collect(Collectors.toList());

        Map<String, List<Long>> contentIdsByType = groupAiPlacesByType(aiResponse);
        Map<Long, PoiSummary> summaryMap = buildSummaryMap(allContentIds);
        Map<Long, PoiDetail> detailMap = buildDetailMap(contentIdsByType);

        List<TourCourseGenerateResponseDto.DailySchedule> schedules = aiResponse.getSchedule().stream()
                .map(day -> {
                    List<TourCourseGenerateResponseDto.PlaceInfo> places = day.getPlaces().stream()
                            .map(place -> TourCourseGenerateResponseDto.PlaceInfo.builder()
                                    .seq(place.getSeq())
                                    .time(place.getTime())
                                    .type(place.getType())
                                    .contentId(place.getContentId())
                                    .contentName(titleOf(summaryMap, place.getContentId()))
                                    .durationMinutes(place.getDurationMinutes())
                                    .thumbnailImg(thumbnailOf(summaryMap, place.getContentId()))
                                    .operatingHours(operatingHoursOf(detailMap, place.getContentId()))
                                    .cost(resolveCost(place.getType(), detailMap.get(place.getContentId())))
                                    .build())
                            .collect(Collectors.toList());

                    return TourCourseGenerateResponseDto.DailySchedule.builder()
                            .date(day.getDate())
                            .places(places)
                            .build();
                })
                .collect(Collectors.toList());

        return TourCourseGenerateResponseDto.builder()
                .courseId(courseId)
                .schedule(schedules)
                .build();
    }

    private TourCourseShareResponseDto buildCourseResponse(TourCourseUserDefined course) {
        List<TourCourseUserDefinedDetail> details =
                tourCourseUserDefinedDetailRepository.findByTourCourseId(course.getId());

        List<Long> allContentIds = details.stream()
                .map(TourCourseUserDefinedDetail::getContentId)
                .distinct()
                .collect(Collectors.toList());

        Map<String, List<Long>> contentIdsByType = groupDetailsByType(details);
        Map<Long, PoiSummary> summaryMap = buildSummaryMap(allContentIds);
        Map<Long, PoiDetail> detailMap = buildDetailMap(contentIdsByType);

        List<TourCourseShareResponseDto.DailySchedule> schedule = details.stream()
                .collect(Collectors.groupingBy(TourCourseUserDefinedDetail::getDate))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<TourCourseShareResponseDto.PlaceInfo> places = entry.getValue().stream()
                            .sorted(Comparator.comparingInt(TourCourseUserDefinedDetail::getSeq))
                            .map(d -> TourCourseShareResponseDto.PlaceInfo.builder()
                                    .seq(d.getSeq())
                                    .time(d.getTime())
                                    .type(d.getType())
                                    .contentId(d.getContentId())
                                    .placeName(titleOf(summaryMap, d.getContentId()))
                                    .durationMinutes(d.getDurationMinutes())
                                    .thumbnailImg(thumbnailOf(summaryMap, d.getContentId()))
                                    .operatingHours(operatingHoursOf(detailMap, d.getContentId()))
                                    .cost(resolveCost(d.getType(), detailMap.get(d.getContentId()), d.getCost()))
                                    .mapx(mapxOf(summaryMap, d.getContentId()))
                                    .mapy(mapyOf(summaryMap, d.getContentId()))
                                    .build())
                            .collect(Collectors.toList());
                    return TourCourseShareResponseDto.DailySchedule.builder()
                            .date(entry.getKey())
                            .places(places)
                            .build();
                })
                .collect(Collectors.toList());

        return TourCourseShareResponseDto.builder()
                .courseId(course.getId())
                .title(course.getTitle())
                .peopleCount(course.getPeopleCount())
                .startDate(course.getStartDate())
                .endDate(course.getEndDate())
                .transport(course.getTransport())
                .theme(parseTheme(course.getTheme()))
                .schedule(schedule)
                .build();
    }

    // ── 보강 데이터 빌더 (TourAPI 라이브 조회, 캐시 경유) ────────────────────────

    private Map<Long, PoiSummary> buildSummaryMap(List<Long> contentIds) {
        Set<Long> idSet = new HashSet<>(contentIds);
        return tourLiveDataService.getAllCandidates().stream()
                .filter(p -> idSet.contains(p.contentId()))
                .collect(Collectors.toMap(PoiSummary::contentId, p -> p, (a, b) -> a));
    }

    private Map<Long, PoiDetail> buildDetailMap(Map<String, List<Long>> contentIdsByType) {
        Map<Long, PoiDetail> result = new HashMap<>();
        contentIdsByType.forEach((typeName, ids) -> {
            Integer contentTypeId = resolveContentTypeId(typeName);
            for (Long id : ids) {
                result.put(id, tourLiveDataService.getDetail(id, contentTypeId));
            }
        });
        return result;
    }

    private Integer resolveContentTypeId(String typeName) {
        try {
            return PlaceType.valueOf(typeName).getContentTypeId();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String thumbnailOf(Map<Long, PoiSummary> summaryMap, Long contentId) {
        PoiSummary s = summaryMap.get(contentId);
        if (s == null || s.firstimage() == null || s.firstimage().isBlank()) return null;
        return s.firstimage();
    }

    private String titleOf(Map<Long, PoiSummary> summaryMap, Long contentId) {
        PoiSummary s = summaryMap.get(contentId);
        return s != null ? s.title() : "";
    }

    private BigDecimal mapxOf(Map<Long, PoiSummary> summaryMap, Long contentId) {
        PoiSummary s = summaryMap.get(contentId);
        return s != null ? s.mapx() : null;
    }

    private BigDecimal mapyOf(Map<Long, PoiSummary> summaryMap, Long contentId) {
        PoiSummary s = summaryMap.get(contentId);
        return s != null ? s.mapy() : null;
    }

    private String operatingHoursOf(Map<Long, PoiDetail> detailMap, Long contentId) {
        PoiDetail d = detailMap.get(contentId);
        return d != null ? d.operatingHours() : null;
    }

    private Integer resolveCost(String type, PoiDetail detail) {
        return resolveCost(type, detail, null);
    }

    private Integer resolveCost(String type, PoiDetail detail, Integer storedCost) {
        if (storedCost != null) return storedCost;
        Integer liveCost = detail != null ? detail.cost() : null;
        if (liveCost != null) return liveCost;
        return DEFAULT_COST_BY_TYPE.get(type);
    }

    // ── 유틸 ──────────────────────────────────────────────────────────────────

    private Map<String, List<Long>> groupAiPlacesByType(TourCourseAiResponseDto aiResponse) {
        return aiResponse.getSchedule().stream()
                .flatMap(day -> day.getPlaces().stream())
                .collect(Collectors.groupingBy(
                        TourCourseAiResponseDto.PlaceVisit::getType,
                        Collectors.mapping(TourCourseAiResponseDto.PlaceVisit::getContentId, Collectors.toList())
                ));
    }

    private Map<String, List<Long>> groupDetailsByType(List<TourCourseUserDefinedDetail> details) {
        return details.stream()
                .collect(Collectors.groupingBy(
                        TourCourseUserDefinedDetail::getType,
                        Collectors.mapping(TourCourseUserDefinedDetail::getContentId, Collectors.toList())
                ));
    }

    private List<String> parseTheme(String themeJson) {
        try {
            return objectMapper.readValue(themeJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ── POI 샘플링 ────────────────────────────────────────────────────────────

    /** TourAPI 라이브 후보(PoiSummary) + poi_rating(stars/likes)를 합친 샘플링용 뷰 */
    private record RatedPoi(Long contentId, Integer contentTypeId, String title, BigDecimal stars, Integer likes,
                             BigDecimal mapx, BigDecimal mapy) {
    }

    private String fetchPlacesData(List<String> sigunguCodes, TransportType transport) {
        log.info("Fetching places data for sigunguCodes: {}", sigunguCodes);

        List<PoiSummary> allCandidates = tourLiveDataService.getAllCandidates();
        List<PoiSummary> filtered = (sigunguCodes == null || sigunguCodes.isEmpty())
                ? allCandidates
                : allCandidates.stream()
                        .filter(p -> sigunguCodes.contains(p.lDongSignguCd()))
                        .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            throw new IllegalArgumentException("해당 지역의 여행지 데이터가 없습니다");
        }

        Map<Long, PoiRating> ratingsById = poiRatingRepository
                .findByContentidIn(filtered.stream().map(PoiSummary::contentId).collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(PoiRating::getContentid, r -> r));

        List<RatedPoi> ratedPois = filtered.stream()
                .map(p -> {
                    PoiRating r = ratingsById.get(p.contentId());
                    return new RatedPoi(p.contentId(), p.contentTypeId(), p.title(),
                            r != null ? r.getStars() : null,
                            r != null ? r.getLikes() : null,
                            p.mapx(), p.mapy());
                })
                .collect(Collectors.toList());

        List<RatedPoi> selected = selectByTypeQuota(ratedPois);
        Map<Long, Integer> geoClusters = assignGeoClusters(selected, transport);
        log.info("Selected {} places for AI (from {} total), {} geo-clusters", selected.size(), ratedPois.size(), geoClusters.values().stream().distinct().count());
        return buildPlacesJson(selected, geoClusters);
    }

    // ── 이동거리 클러스터링 (좌표 기반, LLM에게는 그룹 태그만 전달) ────────────────

    /**
     * 선택된 POI들을 좌표 기준으로 지리적 그룹으로 묶는다. AI에게 원본 좌표를 주고 거리를
     * 계산시키는 대신, 이미 이동 가능 범위(2시간 이내)로 묶인 그룹 태그만 전달해
     * 토큰을 아끼고 소형 모델의 거리 추정 오류를 원천 차단한다.
     * WALK는 범위가 모호해 그룹핑 대상에서 제외(전량 미분류).
     */
    private Map<Long, Integer> assignGeoClusters(List<RatedPoi> pois, TransportType transport) {
        if (transport == TransportType.WALK) {
            return Collections.emptyMap();
        }
        double joinRadiusKm = (transport == TransportType.CAR) ? CAR_CLUSTER_RADIUS_KM : TRANSIT_CLUSTER_RADIUS_KM;

        List<double[]> centroids = new ArrayList<>(); // [lat, lon, count]
        Map<Long, Integer> clusterByContentId = new HashMap<>();

        for (RatedPoi poi : pois) {
            if (poi.mapx() == null || poi.mapy() == null) continue;
            double lat = poi.mapy().doubleValue();
            double lon = poi.mapx().doubleValue();

            int nearestIdx = -1;
            double nearestDist = Double.MAX_VALUE;
            for (int i = 0; i < centroids.size(); i++) {
                double[] c = centroids.get(i);
                double d = haversineKm(lat, lon, c[0], c[1]);
                if (d < nearestDist) {
                    nearestDist = d;
                    nearestIdx = i;
                }
            }

            if (nearestIdx >= 0 && nearestDist <= joinRadiusKm) {
                double[] c = centroids.get(nearestIdx);
                double count = c[2];
                c[0] = (c[0] * count + lat) / (count + 1);
                c[1] = (c[1] * count + lon) / (count + 1);
                c[2] = count + 1;
                clusterByContentId.put(poi.contentId(), nearestIdx);
            } else {
                centroids.add(new double[]{lat, lon, 1});
                clusterByContentId.put(poi.contentId(), centroids.size() - 1);
            }
        }

        return clusterByContentId;
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private List<RatedPoi> selectByTypeQuota(List<RatedPoi> allPois) {
        List<RatedPoi> qualifiedPois = allPois.stream()
                .filter(p -> p.stars() == null || p.stars().compareTo(BigDecimal.valueOf(1.0)) > 0)
                .collect(Collectors.toList());

        if (qualifiedPois.isEmpty()) {
            log.warn("품질 하한 적용 후 후보 POI가 없어 전체 풀로 폴백합니다.");
            qualifiedPois = new ArrayList<>(allPois);
        }

        Map<String, Integer> quotaMap = new HashMap<>();
        quotaMap.put("FOOD",          QUOTA_FOOD);
        quotaMap.put("ACCOMMODATION", QUOTA_ACCOMMODATION);
        quotaMap.put("ATTRACTION",    QUOTA_ATTRACTION);
        quotaMap.put("CULTURE",       QUOTA_CULTURE);
        quotaMap.put("LEPORTS",       QUOTA_LEPORTS);
        quotaMap.put("SHOPPING",      QUOTA_SHOPPING);
        quotaMap.put("EVENT",         QUOTA_EVENT);

        Map<String, List<RatedPoi>> byType = qualifiedPois.stream()
                .collect(Collectors.groupingBy(p -> getPlaceType(p.contentTypeId())));

        List<RatedPoi> selected = new ArrayList<>();
        int totalQuota = quotaMap.values().stream().mapToInt(Integer::intValue).sum();

        for (Map.Entry<String, Integer> entry : quotaMap.entrySet()) {
            String type = entry.getKey();
            int quota = entry.getValue();
            List<RatedPoi> pool = byType.getOrDefault(type, Collections.emptyList());

            List<RatedPoi> tierA = pool.stream()
                    .filter(p -> p.stars() != null && p.stars().compareTo(BigDecimal.valueOf(4.0)) >= 0)
                    .collect(Collectors.toList());

            List<RatedPoi> tierB = pool.stream()
                    .filter(p -> p.stars() == null || (p.stars().compareTo(BigDecimal.valueOf(1.0)) > 0 && p.stars().compareTo(BigDecimal.valueOf(4.0)) < 0))
                    .collect(Collectors.toList());

            applyOrderStrategy(tierA);
            applyOrderStrategy(tierB);

            int tierASlots = (int) Math.round(quota * TIER_A_RATIO);
            int tierBSlots = quota - tierASlots;

            List<RatedPoi> fromA = new ArrayList<>(tierA.subList(0, Math.min(tierASlots, tierA.size())));
            int aShortfall = tierASlots - fromA.size();
            List<RatedPoi> fromB = new ArrayList<>(tierB.subList(0, Math.min(tierBSlots + aShortfall, tierB.size())));

            selected.addAll(fromA);
            selected.addAll(fromB);
        }

        int deficit = totalQuota - selected.size();
        if (deficit > 0) {
            Set<Long> selectedIds = selected.stream()
                    .map(RatedPoi::contentId)
                    .collect(Collectors.toSet());
            List<RatedPoi> attractionPool = byType.getOrDefault("ATTRACTION", Collections.emptyList())
                    .stream()
                    .filter(p -> !selectedIds.contains(p.contentId()))
                    .collect(Collectors.toList());
            Collections.shuffle(attractionPool);
            selected.addAll(attractionPool.subList(0, Math.min(deficit, attractionPool.size())));
        }

        Collections.shuffle(selected);
        return selected;
    }

    private void applyOrderStrategy(List<RatedPoi> pool) {
        boolean hasLikesData = pool.stream()
                .anyMatch(p -> p.likes() != null && p.likes() > 0);
        if (hasLikesData) {
            pool.sort(Comparator.comparingInt((RatedPoi p) -> p.likes() == null ? 0 : p.likes()).reversed());
        } else {
            Collections.shuffle(pool);
        }
    }

    private String buildPlacesJson(List<RatedPoi> pois, Map<Long, Integer> geoClusters) {
        boolean hasClusters = !geoClusters.isEmpty();
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < pois.size(); i++) {
            RatedPoi poi = pois.get(i);
            json.append("{\"id\":").append(poi.contentId())
                .append(",\"t\":\"").append(getPlaceType(poi.contentTypeId()))
                .append("\",\"n\":\"").append(escapeJson(poi.title()))
                .append("\"");
            if (hasClusters) {
                Integer g = geoClusters.get(poi.contentId());
                json.append(",\"g\":").append(g != null ? g : "null");
            }
            json.append("}");
            if (i < pois.size() - 1) json.append(",");
        }
        json.append("]");
        return json.toString();
    }

    private String getPlaceType(Integer contentTypeId) {
        if (contentTypeId == null) return "ATTRACTION";
        try {
            return PlaceType.fromContentTypeId(contentTypeId).name();
        } catch (IllegalArgumentException e) {
            return "ATTRACTION";
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String buildUserRequest(TourCourseGenerateRequestDto request) {
        return String.format(
                "인원: %d명, 기간: %s ~ %s, 이동수단: %s, 테마: %s",
                request.getPeopleCount(),
                request.getStartDate(),
                request.getEndDate(),
                request.getTransport().getDescription(),
                String.join(", ", request.getTheme())
        );
    }

    private void validateAiResponse(TourCourseAiResponseDto aiResponse, LocalDate startDate, LocalDate endDate,
                                     TransportType transport) {
        if (aiResponse == null || aiResponse.getSchedule() == null || aiResponse.getSchedule().isEmpty()) {
            throw new AiCourseGenerationException(ErrorCode.RESPONSE_VALIDATION_FAILED,
                    "AI 응답이 비어있습니다", true);
        }

        Set<Long> contentIds = new HashSet<>();
        for (TourCourseAiResponseDto.DailyPlan day : aiResponse.getSchedule()) {
            if (day.getPlaces() != null) {
                for (TourCourseAiResponseDto.PlaceVisit place : day.getPlaces()) {
                    contentIds.add(place.getContentId());

                    if (day.getDate().isBefore(startDate) || day.getDate().isAfter(endDate)) {
                        throw new AiCourseGenerationException(ErrorCode.RESPONSE_VALIDATION_FAILED,
                                "AI가 생성한 일정 날짜가 요청 범위를 벗어났습니다: " + day.getDate(), true);
                    }

                    try {
                        PlaceType.valueOf(place.getType());
                    } catch (IllegalArgumentException e) {
                        throw new AiCourseGenerationException(ErrorCode.RESPONSE_VALIDATION_FAILED,
                                "AI가 유효하지 않은 장소 타입을 생성했습니다: " + place.getType(), true);
                    }
                }
            }
        }

        // v0.5.0: 로컬 DB 조회 대신, 요청 내에서 이미 확보한 TourAPI 후보 리스트(캐시)와 메모리 대조
        Set<Long> knownContentIds = tourLiveDataService.getAllCandidates().stream()
                .map(PoiSummary::contentId)
                .collect(Collectors.toSet());

        if (!knownContentIds.containsAll(contentIds)) {
            Set<Long> unknown = new HashSet<>(contentIds);
            unknown.removeAll(knownContentIds);
            log.error("존재하지 않는 장소 ID가 포함되어 있습니다: {}", unknown);
            throw new AiCourseGenerationException(ErrorCode.RESPONSE_VALIDATION_FAILED,
                    "AI가 존재하지 않는 장소 ID를 생성했습니다", true);
        }

        if (transport != TransportType.WALK) {
            validateTravelDistances(aiResponse, transport);
        }

        validateNoAccommodationOnLastDay(aiResponse, endDate);

        log.info("AI response validation successful");
    }

    /**
     * 마지막 날(체크아웃일)은 숙박 없이 귀가하는 날이므로 ACCOMMODATION이 있으면 안 된다.
     * 프롬프트(RULE 4)로만 지시할 경우 소형 모델이 "multi-day trip은 매일 끝에 숙소"라는
     * 규칙을 문자 그대로 적용해 마지막 날에도 숙소를 넣는 사례가 관측되어 사후 검증으로 차단한다.
     */
    private void validateNoAccommodationOnLastDay(TourCourseAiResponseDto aiResponse, LocalDate endDate) {
        for (TourCourseAiResponseDto.DailyPlan day : aiResponse.getSchedule()) {
            if (!endDate.equals(day.getDate()) || day.getPlaces() == null) continue;

            boolean hasAccommodation = day.getPlaces().stream()
                    .anyMatch(p -> PlaceType.ACCOMMODATION.name().equals(p.getType()));
            if (hasAccommodation) {
                log.warn("마지막 날({})에 ACCOMMODATION이 포함되어 있습니다", endDate);
                throw new AiCourseGenerationException(ErrorCode.RESPONSE_VALIDATION_FAILED,
                        "AI가 마지막 날(체크아웃일)에 숙소를 포함해 일정을 생성했습니다", true);
            }
        }
    }

    /**
     * 사후 검증 안전장치: 클러스터 태그로 사전 필터링을 해도 AI가 그룹을 무시하고
     * 배치할 수 있으므로, 실제 좌표(mapx/mapy)로 하루 내 연속 이동 구간의 거리를 계산해
     * 이동수단별 한계(2시간 상당)를 넘으면 재시도 가능한 예외로 실패시킨다.
     */
    private void validateTravelDistances(TourCourseAiResponseDto aiResponse, TransportType transport) {
        double maxLegKm = (transport == TransportType.CAR) ? CAR_MAX_LEG_KM : TRANSIT_MAX_LEG_KM;

        Map<Long, PoiSummary> coordMap = tourLiveDataService.getAllCandidates().stream()
                .collect(Collectors.toMap(PoiSummary::contentId, p -> p, (a, b) -> a));

        for (TourCourseAiResponseDto.DailyPlan day : aiResponse.getSchedule()) {
            if (day.getPlaces() == null || day.getPlaces().size() < 2) continue;

            List<TourCourseAiResponseDto.PlaceVisit> ordered = day.getPlaces().stream()
                    .sorted(Comparator.comparingInt(TourCourseAiResponseDto.PlaceVisit::getSeq))
                    .collect(Collectors.toList());

            for (int i = 0; i < ordered.size() - 1; i++) {
                PoiSummary from = coordMap.get(ordered.get(i).getContentId());
                PoiSummary to = coordMap.get(ordered.get(i + 1).getContentId());
                if (from == null || to == null || from.mapx() == null || from.mapy() == null
                        || to.mapx() == null || to.mapy() == null) {
                    continue;
                }

                double distKm = haversineKm(from.mapy().doubleValue(), from.mapx().doubleValue(),
                        to.mapy().doubleValue(), to.mapx().doubleValue());
                if (distKm > maxLegKm) {
                    log.warn("이동거리 초과: {} -> {} ({}km > {}km, transport={})",
                            ordered.get(i).getContentId(), ordered.get(i + 1).getContentId(), distKm, maxLegKm, transport);
                    throw new AiCourseGenerationException(ErrorCode.RESPONSE_VALIDATION_FAILED,
                            String.format("AI가 생성한 일정의 이동거리가 %s 기준(약 2시간)을 초과했습니다", transport), true);
                }
            }
        }
    }

    private TourCourseUserDefined saveTourCourse(TourCourseGenerateRequestDto request,
                                                  Long userId,
                                                  TourCourseAiResponseDto aiResponse) {
        try {
            String themeJson = objectMapper.writeValueAsString(request.getTheme());

            TourCourseUserDefined course = TourCourseUserDefined.builder()
                    .userId(userId)
                    .peopleCount(request.getPeopleCount())
                    .startDate(request.getStartDate())
                    .endDate(request.getEndDate())
                    .transport(request.getTransport().name())
                    .theme(themeJson)
                    .build();

            TourCourseUserDefined savedCourse = tourCourseUserDefinedRepository.save(course);

            List<TourCourseUserDefinedDetail> details = new ArrayList<>();
            for (TourCourseAiResponseDto.DailyPlan day : aiResponse.getSchedule()) {
                if (day.getPlaces() != null) {
                    for (TourCourseAiResponseDto.PlaceVisit place : day.getPlaces()) {
                        details.add(TourCourseUserDefinedDetail.builder()
                                .tourCourseId(savedCourse.getId())
                                .date(day.getDate())
                                .seq(place.getSeq())
                                .time(place.getTime())
                                .durationMinutes(place.getDurationMinutes())
                                .type(place.getType())
                                .contentId(place.getContentId())
                                .build());
                    }
                }
            }

            tourCourseUserDefinedDetailRepository.saveAll(details);
            log.info("Tour course saved successfully. ID: {}", savedCourse.getId());
            return savedCourse;

        } catch (Exception e) {
            log.error("Failed to save tour course: {}", e.getMessage());
            throw new RuntimeException("여행 코스 저장에 실패했습니다", e);
        }
    }
}

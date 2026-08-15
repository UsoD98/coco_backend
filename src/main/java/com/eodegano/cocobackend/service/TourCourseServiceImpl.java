package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.client.GroqApiClient;
import com.eodegano.cocobackend.domain.PoiRating;
import com.eodegano.cocobackend.domain.TourCourseUserDefined;
import com.eodegano.cocobackend.domain.TourCourseUserDefinedDetail;
import com.eodegano.cocobackend.domain.User;
import com.eodegano.cocobackend.domain.enums.PlaceType;
import com.eodegano.cocobackend.dto.TourCourseAiResponseDto;
import com.eodegano.cocobackend.dto.TourCourseGenerateRequestDto;
import com.eodegano.cocobackend.dto.TourCourseGenerateResponseDto;
import com.eodegano.cocobackend.dto.TourCourseListItemDto;
import com.eodegano.cocobackend.dto.TourCourseShareResponseDto;
import com.eodegano.cocobackend.dto.TourCourseUpdateRequestDto;
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

        String placesData = fetchPlacesData(request.getSigunguCodes());
        String userRequest = buildUserRequest(request);
        TourCourseAiResponseDto aiResponse = groqApiClient.generateTourCourse(placesData, userRequest);
        validateAiResponse(aiResponse, request.getStartDate(), request.getEndDate());
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
                                    .cost(resolveCost(d.getType(), detailMap.get(d.getContentId())))
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

    private String operatingHoursOf(Map<Long, PoiDetail> detailMap, Long contentId) {
        PoiDetail d = detailMap.get(contentId);
        return d != null ? d.operatingHours() : null;
    }

    private Integer resolveCost(String type, PoiDetail detail) {
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
    private record RatedPoi(Long contentId, Integer contentTypeId, String title, BigDecimal stars, Integer likes) {
    }

    private String fetchPlacesData(List<String> sigunguCodes) {
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
                            r != null ? r.getLikes() : null);
                })
                .collect(Collectors.toList());

        List<RatedPoi> selected = selectByTypeQuota(ratedPois);
        log.info("Selected {} places for AI (from {} total)", selected.size(), ratedPois.size());
        return buildPlacesJson(selected);
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

    private String buildPlacesJson(List<RatedPoi> pois) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < pois.size(); i++) {
            RatedPoi poi = pois.get(i);
            json.append("{\"id\":").append(poi.contentId())
                .append(",\"t\":\"").append(getPlaceType(poi.contentTypeId()))
                .append("\",\"n\":\"").append(escapeJson(poi.title()))
                .append("\"}");
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

    private void validateAiResponse(TourCourseAiResponseDto aiResponse, LocalDate startDate, LocalDate endDate) {
        if (aiResponse == null || aiResponse.getSchedule() == null || aiResponse.getSchedule().isEmpty()) {
            throw new IllegalArgumentException("AI 응답이 비어있습니다");
        }

        Set<Long> contentIds = new HashSet<>();
        for (TourCourseAiResponseDto.DailyPlan day : aiResponse.getSchedule()) {
            if (day.getPlaces() != null) {
                for (TourCourseAiResponseDto.PlaceVisit place : day.getPlaces()) {
                    contentIds.add(place.getContentId());

                    if (day.getDate().isBefore(startDate) || day.getDate().isAfter(endDate)) {
                        throw new IllegalArgumentException("일정 날짜가 요청 범위를 벗어났습니다: " + day.getDate());
                    }

                    try {
                        PlaceType.valueOf(place.getType());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("유효하지 않은 장소 타입입니다: " + place.getType());
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
            throw new IllegalArgumentException("존재하지 않는 장소 ID가 포함되어 있습니다");
        }

        log.info("AI response validation successful");
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

package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.client.GroqApiClient;
import com.eodegano.cocobackend.domain.*;
import com.eodegano.cocobackend.domain.enums.PlaceType;
import com.eodegano.cocobackend.dto.TourCourseAiResponseDto;
import com.eodegano.cocobackend.dto.TourCourseGenerateRequestDto;
import com.eodegano.cocobackend.dto.TourCourseGenerateResponseDto;
import com.eodegano.cocobackend.dto.TourCourseListItemDto;
import com.eodegano.cocobackend.dto.TourCourseShareResponseDto;
import com.eodegano.cocobackend.repository.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

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
    private final TourRepository tourRepository;
    private final TourCourseUserDefinedRepository tourCourseUserDefinedRepository;
    private final TourCourseUserDefinedDetailRepository tourCourseUserDefinedDetailRepository;
    private final UserRepository userRepository;
    private final AttractionRepository attractionRepository;
    private final FoodRepository foodRepository;
    private final CultureRepository cultureRepository;
    private final LeportsRepository leportsRepository;
    private final ShoppingRepository shoppingRepository;
    private final EventRepository eventRepository;
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

    // ── 코스 응답 빌더 ─────────────────────────────────────────────────────────

    private TourCourseGenerateResponseDto buildGenerateResponse(Long courseId, TourCourseAiResponseDto aiResponse) {
        List<Long> allContentIds = aiResponse.getSchedule().stream()
                .flatMap(day -> day.getPlaces().stream())
                .map(TourCourseAiResponseDto.PlaceVisit::getContentId)
                .distinct()
                .collect(Collectors.toList());

        Map<String, List<Long>> contentIdsByType = groupAiPlacesByType(aiResponse);
        List<Tour> tours = tourRepository.findByContentidIn(allContentIds);
        Map<Long, String> thumbnailMap = buildThumbnailMap(tours);
        Map<Long, String> operatingHoursMap = buildOperatingHoursMap(contentIdsByType);
        Map<Long, Integer> costMap = buildCostMap(contentIdsByType);

        List<TourCourseGenerateResponseDto.DailySchedule> schedules = aiResponse.getSchedule().stream()
                .map(day -> {
                    List<TourCourseGenerateResponseDto.PlaceInfo> places = day.getPlaces().stream()
                            .map(place -> TourCourseGenerateResponseDto.PlaceInfo.builder()
                                    .seq(place.getSeq())
                                    .time(place.getTime())
                                    .type(place.getType())
                                    .contentId(place.getContentId())
                                    .durationMinutes(place.getDurationMinutes())
                                    .thumbnailImg(thumbnailMap.get(place.getContentId()))
                                    .operatingHours(operatingHoursMap.get(place.getContentId()))
                                    .cost(costMap.get(place.getContentId()))
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
        List<Tour> tours = tourRepository.findByContentidIn(allContentIds);
        Map<Long, String> titleMap = tours.stream()
                .collect(Collectors.toMap(Tour::getContentid, Tour::getTitle));
        Map<Long, String> thumbnailMap = buildThumbnailMap(tours);
        Map<Long, String> operatingHoursMap = buildOperatingHoursMap(contentIdsByType);
        Map<Long, Integer> costMap = buildCostMap(contentIdsByType);

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
                                    .placeName(titleMap.getOrDefault(d.getContentId(), ""))
                                    .durationMinutes(d.getDurationMinutes())
                                    .thumbnailImg(thumbnailMap.get(d.getContentId()))
                                    .operatingHours(operatingHoursMap.get(d.getContentId()))
                                    .cost(costMap.get(d.getContentId()))
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

    // ── 보강 데이터 빌더 ───────────────────────────────────────────────────────

    private Map<Long, String> buildThumbnailMap(List<Tour> tours) {
        return tours.stream()
                .filter(t -> t.getFirstimage() != null && !t.getFirstimage().isBlank())
                .collect(Collectors.toMap(Tour::getContentid, Tour::getFirstimage));
    }

    private Map<Long, String> buildOperatingHoursMap(Map<String, List<Long>> contentIdsByType) {
        Map<Long, String> result = new HashMap<>();

        List<Long> attractionIds = contentIdsByType.getOrDefault("ATTRACTION", Collections.emptyList());
        if (!attractionIds.isEmpty()) {
            attractionRepository.findByContentidIn(attractionIds).forEach(a -> {
                String val = stripHtml(a.getUsetime());
                if (val != null) result.put(a.getContentid(), val);
            });
        }

        List<Long> foodIds = contentIdsByType.getOrDefault("FOOD", Collections.emptyList());
        if (!foodIds.isEmpty()) {
            foodRepository.findByContentidIn(foodIds).forEach(f -> {
                String val = stripHtml(f.getOpentimefood());
                if (val != null) result.put(f.getContentid(), val);
            });
        }

        List<Long> cultureIds = contentIdsByType.getOrDefault("CULTURE", Collections.emptyList());
        if (!cultureIds.isEmpty()) {
            cultureRepository.findByContentidIn(cultureIds).forEach(c -> {
                String val = stripHtml(c.getUsetimeculture());
                if (val != null) result.put(c.getContentid(), val);
            });
        }

        List<Long> leportsIds = contentIdsByType.getOrDefault("LEPORTS", Collections.emptyList());
        if (!leportsIds.isEmpty()) {
            leportsRepository.findByContentidIn(leportsIds).forEach(l -> {
                String val = stripHtml(l.getUsetimeleports());
                if (val != null) result.put(l.getContentid(), val);
            });
        }

        List<Long> shoppingIds = contentIdsByType.getOrDefault("SHOPPING", Collections.emptyList());
        if (!shoppingIds.isEmpty()) {
            shoppingRepository.findByContentidIn(shoppingIds).forEach(s -> {
                String val = stripHtml(s.getOpentime());
                if (val != null) result.put(s.getContentid(), val);
            });
        }

        // EVENT, ACCOMMODATION → null
        return result;
    }

    private Map<Long, Integer> buildCostMap(Map<String, List<Long>> contentIdsByType) {
        Map<Long, Integer> result = new HashMap<>();

        // 기본값 먼저 세팅
        DEFAULT_COST_BY_TYPE.forEach((type, defaultCost) ->
            contentIdsByType.getOrDefault(type, Collections.emptyList())
                .forEach(id -> result.put(id, defaultCost))
        );

        // CULTURE: DB usefee 있으면 덮어씀
        List<Long> cultureIds = contentIdsByType.getOrDefault("CULTURE", Collections.emptyList());
        if (!cultureIds.isEmpty()) {
            cultureRepository.findByContentidIn(cultureIds).forEach(c -> {
                Integer parsed = parseCostFromDb(c.getUsefee());
                if (parsed != null) result.put(c.getContentid(), parsed);
            });
        }

        // EVENT: DB usetimefestival 있으면 덮어씀
        List<Long> eventIds = contentIdsByType.getOrDefault("EVENT", Collections.emptyList());
        if (!eventIds.isEmpty()) {
            eventRepository.findByContentidIn(eventIds).forEach(e -> {
                Integer parsed = parseCostFromDb(e.getUsetimefestival());
                if (parsed != null) result.put(e.getContentid(), parsed);
            });
        }

        // ACCOMMODATION → null
        return result;
    }

    // ── 유틸 ──────────────────────────────────────────────────────────────────

    private String stripHtml(String text) {
        if (text == null || text.isBlank()) return null;
        return text.replaceAll("(?i)<br\\s*/?>", "\n")
                   .replaceAll("<[^>]+>", "")
                   .trim();
    }

    private Integer parseCostFromDb(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.contains("무료")) return 0;
        // "3,000원", "성인 5000원" 등에서 첫 번째 연속 숫자만 추출
        String digits = raw.replaceAll(",", "").replaceAll(".*?(\\d+).*", "$1");
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

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

    private String fetchPlacesData(List<String> sigunguCodes) {
        log.info("Fetching places data for sigunguCodes: {}", sigunguCodes);

        List<Tour> allTours = (sigunguCodes == null || sigunguCodes.isEmpty())
                ? tourRepository.findAll()
                : tourRepository.findByLDongSignguCdIn(sigunguCodes);

        if (allTours.isEmpty()) {
            throw new IllegalArgumentException("해당 지역의 여행지 데이터가 없습니다");
        }

        List<Tour> selected = selectByTypeQuota(allTours);
        log.info("Selected {} places for AI (from {} total)", selected.size(), allTours.size());
        return buildPlacesJson(selected);
    }

    private List<Tour> selectByTypeQuota(List<Tour> allTours) {
        List<Tour> qualifiedTours = allTours.stream()
                .filter(t -> t.getStars() == null || t.getStars().compareTo(BigDecimal.valueOf(1.0)) > 0)
                .collect(Collectors.toList());

        if (qualifiedTours.isEmpty()) {
            log.warn("품질 하한 적용 후 후보 POI가 없어 전체 풀로 폴백합니다.");
            qualifiedTours = new ArrayList<>(allTours);
        }

        Map<String, Integer> quotaMap = new HashMap<>();
        quotaMap.put("FOOD",          QUOTA_FOOD);
        quotaMap.put("ACCOMMODATION", QUOTA_ACCOMMODATION);
        quotaMap.put("ATTRACTION",    QUOTA_ATTRACTION);
        quotaMap.put("CULTURE",       QUOTA_CULTURE);
        quotaMap.put("LEPORTS",       QUOTA_LEPORTS);
        quotaMap.put("SHOPPING",      QUOTA_SHOPPING);
        quotaMap.put("EVENT",         QUOTA_EVENT);

        Map<String, List<Tour>> byType = qualifiedTours.stream()
                .collect(Collectors.groupingBy(t -> getPlaceType(t.getContenttypeid())));

        List<Tour> selected = new ArrayList<>();
        int totalQuota = quotaMap.values().stream().mapToInt(Integer::intValue).sum();

        for (Map.Entry<String, Integer> entry : quotaMap.entrySet()) {
            String type = entry.getKey();
            int quota = entry.getValue();
            List<Tour> pool = byType.getOrDefault(type, Collections.emptyList());

            List<Tour> tierA = pool.stream()
                    .filter(t -> t.getStars() != null && t.getStars().compareTo(BigDecimal.valueOf(4.0)) >= 0)
                    .collect(Collectors.toList());

            List<Tour> tierB = pool.stream()
                    .filter(t -> t.getStars() == null || (t.getStars().compareTo(BigDecimal.valueOf(1.0)) > 0 && t.getStars().compareTo(BigDecimal.valueOf(4.0)) < 0))
                    .collect(Collectors.toList());

            applyOrderStrategy(tierA);
            applyOrderStrategy(tierB);

            int tierASlots = (int) Math.round(quota * TIER_A_RATIO);
            int tierBSlots = quota - tierASlots;

            List<Tour> fromA = new ArrayList<>(tierA.subList(0, Math.min(tierASlots, tierA.size())));
            int aShortfall = tierASlots - fromA.size();
            List<Tour> fromB = new ArrayList<>(tierB.subList(0, Math.min(tierBSlots + aShortfall, tierB.size())));

            selected.addAll(fromA);
            selected.addAll(fromB);
        }

        int deficit = totalQuota - selected.size();
        if (deficit > 0) {
            Set<Long> selectedIds = selected.stream()
                    .map(Tour::getContentid)
                    .collect(Collectors.toSet());
            List<Tour> attractionPool = byType.getOrDefault("ATTRACTION", Collections.emptyList())
                    .stream()
                    .filter(t -> !selectedIds.contains(t.getContentid()))
                    .collect(Collectors.toList());
            Collections.shuffle(attractionPool);
            selected.addAll(attractionPool.subList(0, Math.min(deficit, attractionPool.size())));
        }

        Collections.shuffle(selected);
        return selected;
    }

    private void applyOrderStrategy(List<Tour> pool) {
        boolean hasLikesData = pool.stream()
                .anyMatch(t -> t.getLikes() != null && t.getLikes() > 0);
        if (hasLikesData) {
            pool.sort(Comparator.comparingInt((Tour t) -> t.getLikes() == null ? 0 : t.getLikes()).reversed());
        } else {
            Collections.shuffle(pool);
        }
    }

    private String buildPlacesJson(List<Tour> tours) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < tours.size(); i++) {
            Tour tour = tours.get(i);
            json.append("{\"id\":").append(tour.getContentid())
                .append(",\"t\":\"").append(getPlaceType(tour.getContenttypeid()))
                .append("\",\"n\":\"").append(escapeJson(tour.getTitle()))
                .append("\"}");
            if (i < tours.size() - 1) json.append(",");
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

        List<Tour> existingTours = tourRepository.findByContentidIn(new ArrayList<>(contentIds));
        if (existingTours.size() != contentIds.size()) {
            Set<Long> existingIds = existingTours.stream()
                    .map(Tour::getContentid)
                    .collect(Collectors.toSet());
            contentIds.removeAll(existingIds);
            log.error("존재하지 않는 장소 ID가 포함되어 있습니다: {}", contentIds);
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

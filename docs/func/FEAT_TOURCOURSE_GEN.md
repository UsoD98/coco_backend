## [Unreleased]

## [0.2.0] - 2026-05-30

### Added

#### Groq AI 여행 코스 생성 서비스

**핵심 기능**
- Groq AI API를 활용한 자동 여행 코스 생성 기능 구현
- 사용자 조건(인원수, 기간, 이동수단, 테마, 지역)에 따른 맞춤형 일정 생성
- 비로그인 사용자도 여행 코스 생성 가능 (userId nullable)
- AI 응답 검증 로직 (contentId DB 존재 확인, 날짜 범위, 타입 유효성)
- 재시도 로직 구현 (3회 시도, 1초 간격)

**Domain & Enums**
- `TransportType` enum - 이동수단 (CAR, PUBLIC_TRANSPORT, WALK)
- `PlaceType` enum - 장소 타입 (ATTRACTION, CULTURE, EVENT, LEPORTS, ACCOMMODATION, SHOPPING, FOOD)
    - `fromContentTypeId()` 메서드로 contentTypeId를 PlaceType으로 변환

**DTOs**
- `TourCourseGenerateRequestDto` - 여행 코스 생성 요청 DTO
    - Bean Validation 적용 (@NotNull, @Min, @Max, @FutureOrPresent, @NotEmpty)
    - 커스텀 검증: 날짜 범위 유효성 검사 (@AssertTrue)
- `TourCourseGenerateResponseDto` - 여행 코스 생성 응답 DTO
    - 중첩 클래스: DailySchedule, PlaceInfo
- `GroqApiRequestDto` - Groq API 요청용 내부 DTO
- `GroqApiResponseDto` - Groq API 응답용 내부 DTO
- `TourCourseAiResponseDto` - AI 응답 파싱용 DTO
    - 중첩 클래스: DailyPlan, PlaceVisit

**Client**
- `GroqApiClient` - Groq API 호출 클라이언트
    - llama-3.1-8b-instant 모델 사용
    - 재시도 로직 (최대 3회, 1초 대기)
    - ClassPathResource를 통한 프롬프트 템플릿 로드
    - RestClient를 사용한 HTTP 통신
    - JSON 응답 파싱 및 검증

**Service**
- `TourCourseService` - 인터페이스
- `TourCourseServiceImpl` - 구현체
    - `fetchPlacesData()`: DB에서 장소 데이터 조회 및 JSON 변환
    - `buildUserRequest()`: 사용자 요청 문자열 생성
    - `validateAiResponse()`: AI 응답 검증 (contentId, 날짜, 타입)
    - `saveTourCourse()`: DB 저장 (TourCourseUserDefined, TourCourseUserDefinedDetail)
    - 타입별 Repository에서 상세 데이터 조회 (N+1 방지)

**Controller**
- `TourCourseController` - 여행 코스 생성 API
    - `POST /api/v1/tour-course` - 여행 코스 생성 엔드포인트
    - @Valid를 통한 요청 검증
    - 비로그인 허용 (userId = null)

**Exception Handling**
- `GlobalExceptionHandler` - 전역 예외 처리
    - MethodArgumentNotValidException: 400 (Validation 실패)
    - IllegalArgumentException: 400 (비즈니스 로직 에러)
    - RuntimeException: 500 (Groq API 실패 등)
    - Exception: 500 (기타 예외)
- `ErrorResponse` - 구조화된 에러 응답 DTO
    - 필드: timestamp, status, error, message, details

**Repository**
- `TourCourseUserDefinedDetailRepository` - 신규 생성
    - `findByTourCourseId()` 메서드 추가
- 기존 Repository에 메서드 추가:
    - `TourRepository`: `findByLDongSignguCd()`, `findByContentidIn()`
    - `AttractionRepository`: `findByContentidIn()`
    - `FoodRepository`: `findByContentidIn()`
    - `CultureRepository`: `findByContentidIn()`
    - `EventRepository`: `findByContentidIn()`
    - `LeportsRepository`: `findByContentidIn()`
    - `ShoppingRepository`: `findByContentidIn()`
    - `AccommodationRepository`: `findByContentidIn()`
    - `DetailCommonRepository`: `findByContentidIn()`
    - `DetailInfoRepository`: `findByContentidIn()`

**Resources**
- `prompts/system-prompt.txt` - AI 시스템 프롬프트
    - AI 역할 정의 및 규칙 명시
    - 응답 형식 (JSON only)
    - 이동수단별 제약사항
    - 운영시간, 식사시간, 숙박 배치 규칙
- `prompts/daily-schedule-template.txt` - 일정 계획 가이드라인
    - 시간대별 활동 추천
    - 거리 및 이동시간 계산 방법
    - 일정 구성 모범 사례

**Configuration**
- `application.yaml`
    - `groq.api-key: ${GROQ_API_KEY}` 추가
- `.env`
    - `GROQ_API_KEY` 환경 변수 추가
- `SecurityConfig`
    - `/api/v1/tour-course/**` 경로 permitAll 설정 (비로그인 허용)
- `build.gradle`
    - Jackson 의존성 추가
        - `com.fasterxml.jackson.core:jackson-databind`
        - `com.fasterxml.jackson.datatype:jackson-datatype-jsr310`

### Technical Details

**API Endpoint**
```
POST /api/v1/tour-course
Content-Type: application/json

Request Body:
{
  "peopleCount": 2,
  "startDate": "2026-06-01",
  "endDate": "2026-06-03",
  "transport": "CAR",
  "theme": ["자연", "맛집"],
  "sigunguCodes": ["35011", "35130"]
}

Response (200 OK):
{
  "courseId": 123,
  "schedule": [
    {
      "date": "2026-06-01",
      "places": [
        {
          "seq": 1,
          "time": "09:00:00",
          "type": "ATTRACTION",
          "contentId": 126508
        }
      ]
    }
  ]
}
```

**Performance**
- 예상 응답 시간: 3~7초 (Groq AI 처리 포함)
- 재시도 로직으로 안정성 확보
- N+1 문제 방지를 위한 IN 절 쿼리 사용

**Data Flow**
1. 사용자 요청 → Controller (Validation)
2. Service → DB 조회 (sigunguCodes IN 쿼리 기준, 빈 배열이면 전체)
3. JSON 변환 (장소 데이터: contentId, type, title, 좌표, 운영시간 등)
4. Groq API 호출 (System Prompt + User Request)
5. AI 응답 검증 (contentId 존재, 날짜 범위, 타입)
6. DB 저장 (TourCourseUserDefined, TourCourseUserDefinedDetail)
7. 응답 반환

**Validation**
- Request Validation: Bean Validation (@NotNull, @Min, @Max, @FutureOrPresent, @NotEmpty, @AssertTrue)
- AI Response Validation: contentId DB 존재 확인, 날짜 범위 검증, PlaceType enum 검증

### Files Created (27 files)
- `src/main/java/com/eodegano/cocobackend/domain/enums/TransportType.java`
- `src/main/java/com/eodegano/cocobackend/domain/enums/PlaceType.java`
- `src/main/java/com/eodegano/cocobackend/dto/TourCourseGenerateRequestDto.java`
- `src/main/java/com/eodegano/cocobackend/dto/TourCourseGenerateResponseDto.java`
- `src/main/java/com/eodegano/cocobackend/dto/GroqApiRequestDto.java`
- `src/main/java/com/eodegano/cocobackend/dto/GroqApiResponseDto.java`
- `src/main/java/com/eodegano/cocobackend/dto/TourCourseAiResponseDto.java`
- `src/main/java/com/eodegano/cocobackend/client/GroqApiClient.java`
- `src/main/java/com/eodegano/cocobackend/service/TourCourseService.java`
- `src/main/java/com/eodegano/cocobackend/service/TourCourseServiceImpl.java`
- `src/main/java/com/eodegano/cocobackend/controller/TourCourseController.java`
- `src/main/java/com/eodegano/cocobackend/exception/GlobalExceptionHandler.java`
- `src/main/java/com/eodegano/cocobackend/exception/ErrorResponse.java`
- `src/main/java/com/eodegano/cocobackend/repository/TourCourseUserDefinedDetailRepository.java`
- `src/main/resources/prompts/system-prompt.txt`
- `src/main/resources/prompts/daily-schedule-template.txt`

### Files Modified (12 files)
- `src/main/java/com/eodegano/cocobackend/repository/TourRepository.java`
- `src/main/java/com/eodegano/cocobackend/repository/AttractionRepository.java`
- `src/main/java/com/eodegano/cocobackend/repository/FoodRepository.java`
- `src/main/java/com/eodegano/cocobackend/repository/CultureRepository.java`
- `src/main/java/com/eodegano/cocobackend/repository/EventRepository.java`
- `src/main/java/com/eodegano/cocobackend/repository/LeportsRepository.java`
- `src/main/java/com/eodegano/cocobackend/repository/ShoppingRepository.java`
- `src/main/java/com/eodegano/cocobackend/repository/AccommodationRepository.java`
- `src/main/java/com/eodegano/cocobackend/repository/DetailCommonRepository.java`
- `src/main/java/com/eodegano/cocobackend/repository/DetailInfoRepository.java`
- `src/main/java/com/eodegano/cocobackend/config/SecurityConfig.java`
- `src/main/resources/application.yaml`
- `build.gradle`
- `.env`

### Build
- ✅ Gradle build successful
- ✅ All classes compiled without errors
- ✅ JAR file generated: `cocoBackend-0.0.1-SNAPSHOT.jar` (64MB)

### Known Limitations
- 예산 계산 기능 미구현 (데이터 부족)
- 교통비 계산 미구현 (2단계 기능)
- 코스 저장/공유 API 미구현 (로그인 필요 기능)
- 캐싱 미적용 (향후 시군구별 데이터 캐싱 고려)
- 비동기 처리 미구현 (향후 CompletableFuture 고려)

---
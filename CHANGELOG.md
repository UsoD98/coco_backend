# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.1] - 2026-08-08

### Added

#### `GET /api/v1/poi` — 큐레이션 POI 목록 조회 (GBC017)

지역(`sigunguCode`)·콘텐츠 유형(`contentTypeId`) 필터로 TourAPI 라이브 조회 결과(`TourLiveDataService.getAllCandidates()`, 캐시 경유)를 필터링해 반환한다.

- `sigunguCode`(필수): FE가 사용하는 5자리 코드(`"35130"` = areaCode 35 + sigunguCode)를 `lDongSignguCd`(3자리)와 매칭하기 위해 `35` 접두를 제거해 정규화 (`PoiCurationServiceImpl.normalizeSigunguCode`)
- `contentTypeId`(선택): `PoiSummary.contentTypeId` 기준 필터
- 매칭 결과가 없으면 `available: false` + `items: []` 반환
- `PoiCurationService`/`PoiCurationServiceImpl` 신규, `PoiController`에 `@GetMapping` 추가
- `PoiCurationResponseDto`(`available`, `items`), `PoiCurationItemDto`(`contentId`·`contentTypeId`·`title`·`mapx`·`mapy`·`thumbnail`·`avgPrice`) 신규

**보류 (스펙 대비 축소 구현)**
- `peopleCount`: 필수 파라미터로만 수신, 필터링에는 미사용 — 숙박 인원별 분류(BU2)는 2026-08-08 기획 결정으로 스코프 아웃 확정 (`docs/FEATURES_BACK.md` BU2)
- `theme`: 파라미터 자체 미수신 — 매핑 설계 없음, 추후 결정 필요 (`docs/PRD_BACK.md` BOQ14)
- `avgPrice`: 항상 `null` 반환 — 근거 테이블(`food_avg_price`) 소실로 데이터 소스 미정, BU1/BOQ14 확정 후 `contentTypeId=39`(음식점) 조인 로직 추가 예정

### Fixed

#### `GlobalExceptionHandler` — 필수 쿼리 파라미터 누락 시 400 응답

`GET /api/v1/poi`의 `sigunguCode`/`peopleCount`처럼 `@RequestParam(required = true)`인 파라미터가 아예 누락되면 Spring이 던지는 `MissingServletRequestParameterException`을 기존에는 전용 핸들러가 없어 최종 `Exception` 핸들러가 잡아 500으로 응답하고 있었다. `MissingServletRequestParameterException` 전용 핸들러를 추가해 400 + `"필수 파라미터 'xxx'가 누락되었습니다."` 메시지로 응답하도록 수정.

### Files Created (4 files)

- `dto/PoiCurationResponseDto.java`
- `dto/PoiCurationItemDto.java`
- `service/PoiCurationService.java`
- `service/PoiCurationServiceImpl.java`

### Files Changed (2 files)

- `controller/PoiController.java` — `GET /api/v1/poi` 엔드포인트 추가
- `exception/GlobalExceptionHandler.java` — `MissingServletRequestParameterException` 핸들러 추가

## [0.5.0] - 2026-08-08

### 배경

공모전 의무사항에 따라 한국관광공사 TourAPI 데이터를 로컬 DB에 적재해 사용하는 방식이 금지되어, 관광 정보 조회를 요청 시점 라이브 호출로 전환했다. 응답 속도·TourAPI 일일 호출한도를 보호하기 위해 Caffeine 인메모리 캐시(TTL 6시간)를 도입했다.

### Added

#### `TourLiveDataService` — TourAPI 라이브 조회 + 캐시 계층

- `getAllCandidates()`: `TourApiClient.areaBasedListAll`로 경북 전체 POI를 라이브 조회, `poiCandidates` 캐시(TTL 6h, 단일 엔트리)에 보관. 지역(시군구) 필터링은 캐시된 결과를 애플리케이션 메모리에서 처리
- `getDetail(contentId, contentTypeId)`: `TourApiClient.detailIntro`로 타입별 운영시간·비용 원천 필드(usetime/usetimeculture·usefee/usetimefestival/usetimeleports/opentime/opentimefood)를 추출, `poiDetail` 캐시(TTL 6h, contentId 키)에 보관
- `PoiSummary`/`PoiDetail` 레코드 신규 (`service` 패키지)

#### `PoiRating` 엔티티 — 별점·좋아요 전용 테이블 (`poi_rating`)

- `contentId`(PK)·`stars`·`likes` 컬럼만 보유. TourAPI가 제공하지 않는 앱 자체 데이터만 분리 보존
- 좋아요 액션 시 on-demand 생성 (`PoiRating.createWithLike`), TourAPI 라이브 존재 재검증 없음 — 프론트가 이미 API로 확인한 contentId만 전달한다고 신뢰
- `PoiRatingRepository`: `findByContentidIn`, 원자적 `incrementLikes`/`decrementLikes` (기존 `TourRepository`와 동일 패턴)

#### `CacheConfig` — Caffeine 캐시 설정

- `poiCandidates`, `poiDetail` 2개 캐시, 둘 다 `expireAfterWrite(6h)` / `maximumSize(2000)`
- Redis 대신 Caffeine 채택: 별도 프로세스 없이 기존 JVM 힙 안에서 동작 — 1GB 메모리 프리티어 서버에서 별도 인프라 오버헤드 회피
- `build.gradle`에 `spring-boot-starter-cache`, `com.github.ben-manes.caffeine:caffeine` 추가

### Removed

#### 관광 정보 로컬 DB 저장 폐지 — 엔티티·Repository 14종 삭제

`Tour`, `DetailCommon`, `DetailInfo`, `Attraction`, `Culture`, `Event`, `TourCourse`, `TourCourseDetailInfo`, `Leports`, `Accommodation`, `AccommodationDetailInfo`, `Shopping`, `Food`, `FoodAvgPrice` 및 각 Repository. 전부 `contentid`를 평범한 컬럼으로만 매칭하는 1:1 컴패니언 테이블이라 JPA 연관관계 없이 안전하게 제거 가능했음.

#### `DataMigrationService` / `DataMigrationController` 삭제

TourAPI → 로컬 DB 일괄 적재 배치 자체가 공모전 규정 위반이라 전체 삭제. `TourApiClient`(RestClient 래퍼)는 유지하고 `TourLiveDataService`의 라이브 조회 기반으로 재사용.

#### `SecurityConfig`에서 `/api/admin/migration/**` permitAll 규칙 제거

컨트롤러 자체가 사라졌으므로 규칙도 함께 정리.

### Changed

#### `TourCourseServiceImpl` — 로컬 DB 스캔 → 라이브 조회+캐시 기반 재작성

- `fetchPlacesData`: `tourRepository.findByLDongSignguCdIn` → `TourLiveDataService.getAllCandidates()` + 시군구 메모리 필터링
- 별점/좋아요 티어 샘플링(`selectByTypeQuota`): `Tour` 엔티티 → `PoiRatingRepository`로 조회한 값을 합친 `RatedPoi` 레코드 기준으로 동일 알고리즘 유지 (Tier A/B 70/30 분할·likes 정렬 로직 변경 없음)
- AI 응답 contentId 검증(`validateAiResponse`): `tourRepository.findByContentidIn` → 요청 내에서 이미 확보한 라이브 후보 리스트(캐시)와 메모리 대조로 변경, 추가 DB/API 호출 없음
- 코스 응답 보강(`buildGenerateResponse`/`buildCourseResponse`): 썸네일·운영시간·비용·장소명을 `Tour`/type별 detail 테이블 재조회 → `TourLiveDataService`의 캐시된 `PoiSummary`/`PoiDetail`로 조립

#### `PoiLikeServiceImpl` — `PoiRating` 기반 재작성

- `TourRepository` 의존 제거, `PoiRatingRepository`로 좋아요 카운트 증감
- "존재하지 않는 POI → 404" 검증 제거 (근거였던 로컬 `Tour` 테이블 소실)

### DB 마이그레이션 (2026-08-08 수행)

- 기존 14개 테이블(`tour` 포함) 전체를 `mysqldump`로 백업 후 DROP (FK 의존 순서 준수)
- `poi_rating` 테이블 신설, 기존 `tour.stars` 285개 초기값 이관 완료 (likes는 전부 0이라 이관 대상 없음)
- `mst_sigungu`/`mst_theme`(기준정보), `user`/`refresh_token`/`tour_course_user_defined`/`tour_course_user_defined_detail`/`user_poi_like`는 변경 없이 유지

### Verified

- `./gradlew test` 전체 통과
- 실 DB 연결 기동 → Hibernate `ddl-auto: validate` 스키마 검증 통과
- `POST /api/v1/tour-course` 실제 호출로 TourAPI 라이브 조회 → Groq AI 생성 → 검증 → 저장까지 200 응답 확인 (썸네일·운영시간·비용 필드 정상)
- `GET /{courseId}/view` 공개 조회 정상 (캐시된 장소명 포함)

### Known Gaps

- `BU1`(음식점 평균 객단가)·`BU2`(숙박 인원별 분류): 근거였던 `food_avg_price`·`accommodation_detail_info` 테이블 소실로 재설계 필요 — `docs/PRD_BACK.md` BOQ14로 추적

### Files Changed (44 files)

- `build.gradle`
- `docs/CLAUDE.md`, `docs/PRD.md`, `docs/PRD_BACK.md`, `docs/FEATURES_BACK.md`
- `config/CacheConfig.java` (신규), `config/SecurityConfig.java`
- `domain/PoiRating.java` (신규), `repository/PoiRatingRepository.java` (신규)
- `service/TourLiveDataService.java`, `service/PoiSummary.java`, `service/PoiDetail.java` (신규)
- `service/TourCourseServiceImpl.java`, `service/PoiLikeServiceImpl.java`
- `domain/`·`repository/` 14종 삭제 (Tour/Attraction/Culture/Event/TourCourse/TourCourseDetailInfo/Leports/Accommodation/AccommodationDetailInfo/Shopping/Food/FoodAvgPrice/DetailCommon/DetailInfo)
- `dataMig/service/DataMigrationService.java`, `dataMig/controller/DataMigrationController.java` 삭제

---

## [0.4.2] - 2026-07-04

### Changed

#### AI 코스 생성 요청 `sigunguCode` → `sigunguCodes` (배열) 변경 (CO1)

사용자가 여러 시군구를 대상으로 코스를 생성할 수 있도록 단일 문자열 파라미터를 배열로 변경했다.

- **`TourCourseGenerateRequestDto`**: `String sigunguCode` → `List<String> sigunguCodes`
- **`TourRepository`**: `findByLDongSignguCdIn(List<String>)` 메서드 추가 (JPQL `IN` 쿼리)
- **`TourCourseServiceImpl.fetchPlacesData`**: 복수 코드 수신 시 `IN` 쿼리로 여러 지역 POI를 통합 조회, `null`/빈 배열이면 전체 지역 폴백 유지

**요청 예시 (변경 전)**
```json
{ "sigunguCode": "35011" }
```
**요청 예시 (변경 후)**
```json
{ "sigunguCodes": ["35011", "35130"] }
```

### Files Changed (3 files)

- `dto/TourCourseGenerateRequestDto.java`
- `repository/TourRepository.java`
- `service/TourCourseServiceImpl.java`

---

## [0.4.1] - 2026-07-04

### Added

#### 시군구·테마 마스터 테이블 추가 (`mst_sigungu`, `mst_theme`)

경북 권역 시군구 코드와 여행 테마 코드를 관리하는 마스터 테이블 2개를 추가했다.

**`mst_sigungu`**
- 컬럼: `sigunguCode VARCHAR(20) PK`, `sigunguName VARCHAR(20)`
- `tour.lDongSignguCd`와 동일한 코드 체계 사용
- 초기 데이터: 경북 23개 시군구 (포항시 남구·북구, 경주시, 김천시, 안동시 등)

**`mst_theme`**
- 컬럼: `themeCode VARCHAR(20) PK`, `themeName VARCHAR(20)`
- 초기 데이터: 4개 테마 (001 어드벤처, 002 휴식, 003 문화, 004 음식)

**`domain/MstSigungu.java`**, **`domain/MstTheme.java`** (신규)
- JPA 엔티티 (`@Entity`, `@Table`) 등록 — `ddl-auto: validate` 대응

### DDL

`src/main/resources/sql/init_mst_tables.sql` 실행 (CREATE TABLE + INSERT)

### Files Created (3 files)

- `src/main/resources/sql/init_mst_tables.sql`
- `src/main/java/com/eodegano/cocobackend/domain/MstSigungu.java`
- `src/main/java/com/eodegano/cocobackend/domain/MstTheme.java`

---

## [0.4.0] - 2026-07-04

### Added

#### 여행 코스 응답에 장소별 상세 필드 4개 추가 (CO1·CO4·SH2)

코스 생성(`POST /api/v1/tour-course`), 코스 상세 조회(`GET /api/v1/tour-course/{courseId}`), 공개 코스 뷰(`GET /api/v1/tour-course/{courseId}/view`) 응답의 `PlaceInfo` 객체에 4개 필드를 추가했다.

| 필드 | 타입 | 출처 | 설명 |
| --- | --- | --- | --- |
| `durationMinutes` | Integer | Groq AI 추정 → DB 저장 | 해당 장소 관광 예상 소요시간(분) |
| `thumbnailImg` | String | `tour.firstimage` join | 장소 대표 썸네일 이미지 URL |
| `operatingHours` | String | type별 소개정보 테이블 | 운영시간 원문 텍스트 (없으면 null) |
| `cost` | Integer | DB 우선 → type 기본값 fallback | 예상 비용(원). ACCOMMODATION은 null |

**`durationMinutes` 상세**
- Groq 시스템 프롬프트 Rule 8 추가: 장소 type별 소요시간 추정 기준 명시 (ATTRACTION 60-180분, FOOD 45-90분, CULTURE 60-120분, LEPORTS 120-240분, SHOPPING 30-60분, EVENT 60-120분, ACCOMMODATION 0분).
- 응답 포맷 예시에 `durationMinutes` 필드 추가 및 "Always include durationMinutes for every place entry" 명시.
- AI가 추정한 값을 `tour_course_user_defined_detail.duration_minutes` 컬럼에 저장.
- CO4·SH2 조회 시 DB에서 그대로 반환.

**`operatingHours` 상세**
- type별 소개정보 테이블의 운영시간 컬럼에서 조회 후 HTML 태그(`<br>` → `\n`, 기타 태그 제거) 정규화.
- 컬럼 매핑: ATTRACTION → `attraction.usetime`, FOOD → `food.opentimefood`, CULTURE → `culture.usetimeculture`, LEPORTS → `leports.usetimeleports`, SHOPPING → `shopping.opentime`.
- EVENT·ACCOMMODATION은 신뢰할 수 있는 운영시간 컬럼 없음 → null 반환.
- DB 값 없거나 공백이면 null 반환.

**`cost` 상세**
- DB 우선 조회: CULTURE → `culture.usefee`, EVENT → `event.usetimefestival`에서 파싱. `"무료"` 포함 시 0, 숫자 추출 가능 시 해당 값 사용.
- DB 없거나 파싱 불가 시 type 기본값으로 fallback: ATTRACTION 5,000원 / FOOD 12,000원 / CULTURE 3,000원 / LEPORTS 20,000원 / SHOPPING 0원 / EVENT 0원.
- ACCOMMODATION은 null (별도 예산 처리 예정).

**`TourCourseServiceImpl` 리팩토링**
- type별 repository 6개 의존성 추가: `AttractionRepository`, `FoodRepository`, `CultureRepository`, `LeportsRepository`, `ShoppingRepository`, `EventRepository`.
- `buildGenerateResponse()` (기존 `buildResponse()` 리네임): AI 응답 → 보강 데이터 포함 응답 빌드.
- `buildCourseResponse()`: `tourRepository.findByContentidIn()` 단일 조회로 titleMap·thumbnailMap 동시 구성 (중복 쿼리 제거).
- 헬퍼 메서드 추가: `buildThumbnailMap()`, `buildOperatingHoursMap()`, `buildCostMap()`, `groupAiPlacesByType()`, `groupDetailsByType()`, `stripHtml()`, `parseCostFromDb()`.

### DDL

```sql
ALTER TABLE tour_course_user_defined_detail
    ADD COLUMN duration_minutes INT NULL AFTER time;
```

### Files Modified (7 files)

- `src/main/resources/prompts/system-prompt.txt`
- `src/main/java/com/eodegano/cocobackend/dto/TourCourseAiResponseDto.java`
- `src/main/java/com/eodegano/cocobackend/domain/TourCourseUserDefinedDetail.java`
- `src/main/java/com/eodegano/cocobackend/dto/TourCourseGenerateResponseDto.java`
- `src/main/java/com/eodegano/cocobackend/dto/TourCourseShareResponseDto.java`
- `src/main/java/com/eodegano/cocobackend/service/TourCourseServiceImpl.java`
- `docs/FEATURES_BACK.md`
- `docs/PRD_BACK.md`

---

## [0.3.1] - 2026-06-27

### Fixed

#### `GroqApiClient` — Rate Limit(429) 재시도 로직 개선

- 기존: 모든 에러를 `Exception` 단일 catch로 처리, 1초 대기 후 재시도 → Rate Limit 상황에서 재시도가 모두 실패함.
- 변경: `HttpClientErrorException` 별도 catch로 HTTP 상태코드 구분.
  - **429 (Rate Limit)**: Groq 응답 헤더의 `retry-after` 값(초)을 ms로 변환해 대기. 헤더 없으면 기본 **20초** 대기 후 재시도.
  - **그 외 4xx**: 기존대로 1초 대기. 로그에 HTTP 상태코드 포함.
  - **네트워크·기타 예외**: 기존대로 1초 대기.
- `RATE_LIMIT_DELAY_MS = 20_000` 상수 추가.
- `sleepQuietly()` 헬퍼 메서드로 sleep 중복 코드 통합.
- 최대 재시도(3회) 소진 시 429는 "rate limit 초과, 잠시 후 다시 시도" 메시지로 구분 응답.

### Files Modified (1 file)

- `src/main/java/com/eodegano/cocobackend/client/GroqApiClient.java`

---

## [0.3.0] - 2026-06-27

### Added

#### `tour.stars` 초기값 285개 일괄 적재 (DA4)

- 네이버 지도·다이닝코드·구글 평점 웹 검색 기반으로 경북 CoCo DB 전체 POI 285개에 `stars` 초기값 적재.
- 수집 우선순위: 네이버 지도 → 카카오맵 → 구글 맵 → 다이닝코드·일반 크롤링.
- 실제 검색으로 확인한 주요 값 예시: 경주밀면 본점 4.7 / 고도커피 4.8 / 블리스커피 4.6 / 경주다방 4.5 / 고향밀면 4.6 / 교동쌈밥 3.9 / 구서울갈비 4.7 / 석굴암 4.8 / 동궁과월지 4.7.
- 여행코스(`contenttypeid=25`) 15개는 별점 대상 제외, `stars` null 유지.
- 이로써 Tier A(`stars >= 4.0`) 슬롯이 실제 데이터로 채워져 CO1 Tier 샘플링이 실질적으로 동작함.

### Changed

#### `Tour.java` — `stars` 컬럼 타입 변경 (`Integer` → `Double`)

- JPA 엔티티 필드: `private Integer stars` → `private Double stars`.
- DB DDL: `ALTER TABLE tour MODIFY COLUMN stars DECIMAL(3,1)` (INT → DECIMAL(3,1)).
- 네이버 평점이 소수(4.2, 3.8 등)이므로 정밀도 보존을 위해 변경. 이후 CO6-1 복합 스코어 공식에도 소수 데이터 활용 가능.

#### `TourCourseServiceImpl` — Tier 조건 double 리터럴로 명시

- `getStars()` 반환 타입이 `Double`로 변경됨에 따라 int 리터럴 비교를 double 리터럴로 명시.
  - Hard exclusion: `> 1` → `> 1.0`
  - Tier A: `>= 4` → `>= 4.0`
  - Tier B: `>= 2 && <= 3` → `> 1.0 && < 4.0` (소수 구간 누락 방지)

### Docs

- `FEATURES_BACK.md` — DA1 업서트 설계 추가: 월별 마이그레이션 시 `stars`·`likes` 보존을 위한 MySQL `ON DUPLICATE KEY UPDATE ... stars=COALESCE(stars, VALUES(stars))` 패턴 기록 (DA2 구현 시 선결 과제).
- `FEATURES_BACK.md` — DA4 구현 상태 `🔧` → `✅` 업데이트 (stars 285개 적재 완료).
- `PRD_BACK.md` — B-F1에 업서트 원칙 한 줄 추가 (FEATURES_BACK.md DA1 설계 링크).

### Files Modified (4 files)

- `src/main/java/com/eodegano/cocobackend/domain/Tour.java`
- `src/main/java/com/eodegano/cocobackend/service/TourCourseServiceImpl.java`
- `docs/FEATURES_BACK.md`
- `docs/PRD_BACK.md`

### DDL

```sql
ALTER TABLE tour MODIFY COLUMN stars DECIMAL(3,1);
```

---

## [0.2.7] - 2026-06-27

### Fixed

#### `SecurityConfig` — `/api/v1/user/**` PATCH·DELETE 인증 누락 보완

- 기존: `PATCH /api/v1/user/{userId}/nickname`, `PATCH /api/v1/user/{userId}/password`, `DELETE /api/v1/user/{userId}` 가 `anyRequest().permitAll()`로 떨어져 인증 없이 호출 가능한 상태
- 변경: 아래 두 규칙을 `/api/v1/user/join` permitAll 바로 아래에 추가
  ```java
  .requestMatchers(HttpMethod.PATCH, "/api/v1/user/**").hasAnyRole("USER", "ADMIN")
  .requestMatchers(HttpMethod.DELETE, "/api/v1/user/**").hasAnyRole("USER", "ADMIN")
  ```

#### `TourCourseController.generateTourCourse` — 로그인 사용자 userId 귀속 버그 수정

- 기존: `Long userId = null` 하드코딩 → 로그인 상태로 코스 생성 시에도 userId가 항상 null로 저장
- 변경: `authentication`에서 email 추출 → `userRepository.findByEmailAndDeletedAtIsNull(email)`으로 userId 조회 후 저장

### Changed

#### `TourCourseService` 인터페이스 / `TourCourseServiceImpl`

- `generateTourCourse` 시그니처 변경: `Long userId` → `String email` (nullable)
- `TourCourseServiceImpl`에서 email → userId 변환 로직 내부 처리 (email null 시 userId=null 유지)

### Docs

- `FEATURES_BACK.md` / `PRD_BACK.md` 버전 0.2.7 업데이트
  - AU4 카카오 OAuth 콜백 구현 완료 반영 (`❌` → `✅`)
  - INF4 CORS 구현 완료 반영 (`❌` → `✅`, `SecurityConfig.corsConfigurationSource()`)
  - 미구현 목록에서 AU4·CORS 제거 및 우선순위 재정렬
  - BOQ6 카카오 OAuth 처리 방식 확정 처리

---

## [0.2.6] - 2026-06-20

### Added

#### Tier 기반 확률적 POI 샘플링 (`TourCourseServiceImpl.selectByTypeQuota`)

- **Hard exclusion**: `stars ≤ 1` POI 제거 (품질 하한 보장). 후보 전체 소진 시 전체 풀로 폴백.
- **Tier A** (`stars ≥ 4`): 유형별 할당량의 70% 슬롯 우선 배정.
- **Tier B** (`stars 2-3` 또는 `null`): 나머지 30% + Tier A 부족분 보충. Tier B 부족분은 ATTRACTION 타입으로 채움.
- **Cold-start 보호**: `stars = null` → Tier B 편입 (제외 없음).
- `applyOrderStrategy()`: likes 데이터 존재 시 Tier 내 likes DESC 정렬, 없으면 shuffle.
- `TIER_A_RATIO = 0.7` 상수 추가.

#### `Tour` 엔티티 확장 (`domain/Tour.java`)

- `stars INT` 컬럼 추가 — Tier 샘플링 기준값.
- `likes INT` 컬럼 추가 — 정렬 보조 신호.
- `getLikesOrZero()` 헬퍼 메서드 추가 (null-safe).

#### POI 좋아요 토글 API (`POST /api/v1/poi/{contentId}/like`)

**`domain/UserPoiLike.java`** (신규)
- `user_poi_like` 중계 테이블 엔티티 — composite PK (`@IdClass`): `user_id` + `content_id`.
- `of(Long userId, Long contentId)` 팩토리 메서드.
- `@PrePersist`로 `created_at` 자동 설정.
- FK 제약 없음 — 정합성은 월 1회 배치로 관리.

**`repository/UserPoiLikeRepository.java`** (신규)
- `findByUserIdAndContentId()` — 좋아요 존재 여부 조회.
- `existsByUserIdAndContentId()` — 존재 확인 전용.

**`service/PoiLikeService.java` / `PoiLikeServiceImpl.java`** (신규)
- `toggleLike(Long contentId, String userEmail)`: 중계 테이블 확인 → 존재하면 삭제+decrement / 없으면 저장+increment.
- 최종 `likes` 카운트는 UPDATE 후 재조회로 반환.

**`controller/PoiController.java`** (신규)
- `POST /api/v1/poi/{contentId}/like` — 인증 필수.
- 응답 메시지: 추가 시 `"좋아요가 추가되었습니다."` / 취소 시 `"좋아요가 취소되었습니다."`.

**`dto/PoiLikeResponseDto.java`** (신규)
- 필드: `liked (boolean)`, `likes (int)`.

**`repository/TourRepository.java`** — atomic JPQL UPDATE 추가
- `incrementLikes()`: `COALESCE(likes, 0) + 1` (null-safe).
- `decrementLikes()`: `CASE WHEN ... > 0 THEN ... - 1 ELSE 0 END` (음수 방지).

#### 코스 소유권 이전 (`PATCH /api/v1/tour-course/{courseId}/assign`)

- 비로그인 생성 코스(userId=null)에 로그인 사용자 ID 귀속 (`TourCourseUserDefined.assignUser()`).
- 이미 소유자 있으면 `AccessDeniedException` → 403.

#### 코스 목록 조회 (`GET /api/v1/tour-course`)

**`dto/TourCourseListItemDto.java`** (신규)
- 필드: `courseId`, `title`, `peopleCount`, `startDate`, `endDate`, `transport`, `List<String> theme`, `createdAt`.

- 로그인 사용자의 전체 저장 코스 목록 반환. 코스 없으면 빈 배열.

#### 코스 상세 조회 (`GET /api/v1/tour-course/{courseId}`)

**`dto/TourCourseShareResponseDto.java`** (신규)
- 중첩 클래스: `DailySchedule` (date, places), `PlaceInfo` (seq, time, type, contentId, placeName).
- `TourRepository.findByContentidIn()`으로 contentId → placeName 배치 조회.

- 소유자 인증 필수 (`user.getId().equals(course.getUserId())`). userId=null 코스는 403.

#### 코스 삭제 (`DELETE /api/v1/tour-course/{courseId}`)

- 소유자 인증 후 `TourCourseUserDefinedDetail` 먼저 삭제 → `TourCourseUserDefined` 삭제 (FK 순서 보장).

#### 공개 코스 뷰 (`GET /api/v1/tour-course/{courseId}/view`)

- 인증 불필요 (`permitAll`) — 카카오 공유 링크 수신자용 읽기 전용.
- `TourCourseShareResponseDto` 동일 반환 (CO4와 응답 포맷 공유).
- BOQ11 확정: `share_snapshot` 테이블·`share_token` 컬럼 미추가. FE 카카오 SDK가 courseId 기반 딥링크 생성.

#### `SecurityConfig` — 신규 엔드포인트 인증 규칙 추가

```java
.requestMatchers(HttpMethod.GET, "/api/v1/tour-course").hasAnyRole("USER", "ADMIN")
.requestMatchers(HttpMethod.GET, "/api/v1/tour-course/*/view").permitAll()   // 공개 뷰 먼저
.requestMatchers(HttpMethod.GET, "/api/v1/tour-course/*").hasAnyRole("USER", "ADMIN")
.requestMatchers(HttpMethod.DELETE, "/api/v1/tour-course/*").hasAnyRole("USER", "ADMIN")
.requestMatchers(HttpMethod.PATCH, "/api/v1/tour-course/*/assign").hasAnyRole("USER", "ADMIN")
.requestMatchers(HttpMethod.POST, "/api/v1/poi/*/like").hasAnyRole("USER", "ADMIN")
```

### Fixed

#### `TourRepository` — JPA L1 캐시 스테일 likes 카운트 수정

- `@Modifying` JPQL UPDATE 실행 후 같은 트랜잭션 내 `findById` 재호출 시 L1 캐시(EntityManager)에서 UPDATE 이전 값이 반환되던 문제.
- `incrementLikes` / `decrementLikes` 모두 `@Modifying(clearAutomatically = true)` 추가.

### Changed

#### `TourCourseServiceImpl` — ObjectMapper 리팩토링

- `new ObjectMapper()` 매 호출 생성 제거 → `private final ObjectMapper objectMapper` 필드 주입 (`@RequiredArgsConstructor`).
- Jackson 3.x (`tools.jackson`) 패키지로 임포트 교체.
- `parseTheme()`: raw `List.class` → `new TypeReference<List<String>>(){}` 타입 안전 역직렬화.
- `saveTourCourse()`: 로컬 `new ObjectMapper()` 제거 후 주입 필드 사용.

#### `TourCourseServiceImpl` — 내부 메서드 추출 (중복 제거)

- `buildCourseResponse(TourCourseUserDefined)`: CO4 상세 조회와 SH2 공개 뷰에서 동일하게 사용하는 응답 빌드 로직 공통 추출.
- `parseTheme(String themeJson)`: 테마 JSON → `List<String>` 변환 로직 공통 추출.

### Files Created (9 files)

- `src/main/java/com/eodegano/cocobackend/controller/PoiController.java`
- `src/main/java/com/eodegano/cocobackend/domain/UserPoiLike.java`
- `src/main/java/com/eodegano/cocobackend/dto/PoiLikeResponseDto.java`
- `src/main/java/com/eodegano/cocobackend/dto/TourCourseListItemDto.java`
- `src/main/java/com/eodegano/cocobackend/dto/TourCourseShareResponseDto.java`
- `src/main/java/com/eodegano/cocobackend/repository/UserPoiLikeRepository.java`
- `src/main/java/com/eodegano/cocobackend/service/PoiLikeService.java`
- `src/main/java/com/eodegano/cocobackend/service/PoiLikeServiceImpl.java`

### Files Modified (8 files)

- `src/main/java/com/eodegano/cocobackend/config/SecurityConfig.java`
- `src/main/java/com/eodegano/cocobackend/controller/TourCourseController.java`
- `src/main/java/com/eodegano/cocobackend/domain/Tour.java`
- `src/main/java/com/eodegano/cocobackend/repository/TourRepository.java`
- `src/main/java/com/eodegano/cocobackend/service/TourCourseService.java`
- `src/main/java/com/eodegano/cocobackend/service/TourCourseServiceImpl.java`
- `docs/FEATURES_BACK.md`
- `docs/PRD_BACK.md`

### Known Limitations (업데이트)

- ~~사용자 코스 목록·상세·삭제 API 미구현~~ → **해결됨 (CO3/CO4/CO5)**
- ~~코스 소유권 이전 미구현~~ → **해결됨 (CO2)**
- ~~공유 스냅샷 조회 미구현~~ → **해결됨 (SH2, courseId 기반 공개 뷰)**
- `tour.stars` 데이터 없음 — AI 검색 기반 수동 입력 예정 (null → Tier B 편입으로 Cold-start 보호)
- `tour.likes` 수집 파이프라인 구축 완료 — 데이터 축적 중
- POI 큐레이션 전용 API 미구현 (PO2)
- 교통비 추정 API 미구현 (BU3)

---

## [0.2.5] - 2026-06-20

### Added

#### 공통 API 응답 포맷 표준화 (`ApiResponse<T>`)

**`dto/ApiResponse.java`** (신규)
- 모든 엔드포인트가 반환하는 제네릭 래퍼 DTO
- 필드: `code` (String), `msg` (String), `data` (T)
- 팩토리 메서드:
  - `ApiResponse.ok(msg, data)` — 200 성공, 데이터 포함
  - `ApiResponse.ok(msg)` — 200 성공, 데이터 없음 (`data: null`)
  - `ApiResponse.of(status, msg, data)` — 커스텀 상태 코드

**응답 예시**
```json
// 성공 (데이터 있음)
{ "code": "200", "msg": "로그인에 성공했습니다.", "data": { "accessToken": "..." } }

// 성공 (데이터 없음)
{ "code": "200", "msg": "닉네임이 수정되었습니다.", "data": null }

// 유효성 검증 실패 — data에 필드별 오류 맵
{ "code": "400", "msg": "입력값 검증에 실패했습니다", "data": { "email": "이메일 형식이 아닙니다" } }

// 인증 오류
{ "code": "401", "msg": "인증이 필요합니다.", "data": null }
```

### Changed

#### `exception/GlobalExceptionHandler` — `ApiResponse` 반환으로 교체

- 모든 핸들러 반환 타입 `ResponseEntity<ErrorResponse>` → `ResponseEntity<ApiResponse<?>>`
- `MethodArgumentNotValidException`: validation 필드 오류 맵 → `data`에 포함
- `ResponseStatusException` 핸들러 신규 추가 — `ex.getStatusCode()`·`ex.getReason()` 기반 응답 (기존엔 `RuntimeException`으로 500 처리되던 문제 해결)
- `IllegalArgumentException`, `NoSuchElementException`, `RuntimeException`, `Exception` 핸들러 모두 `ApiResponse` 포맷으로 교체

#### `security/JwtAuthenticationEntryPoint` — 401 응답 포맷 통일

- 기존: `Map.of("status", "401", "message", "...")` 직접 직렬화
- 변경: `ApiResponse.of(401, "인증이 필요합니다.", null)` 직렬화

#### `security/JwtAccessDeniedHandler` — 403 응답 포맷 통일

- 기존: `Map.of("status", "403", "message", "...")` 직접 직렬화
- 변경: `ApiResponse.of(403, "접근 권한이 없습니다.", null)` 직렬화

#### `controller/AuthController`

- 모든 반환 타입 `ResponseEntity<LoginResponseDto>` → `ResponseEntity<ApiResponse<LoginResponseDto>>`
- `logout`: `ResponseEntity<Void>` (204 No Content) → `ResponseEntity<ApiResponse<Void>>` (200) — body 포맷 충돌로 상태 코드 변경
- 각 엔드포인트 성공 메시지: 로그인 `"로그인에 성공했습니다."` / 로그아웃 `"로그아웃되었습니다."` / 재발급 `"토큰이 재발급되었습니다."` / 카카오 `"카카오 로그인에 성공했습니다."`

#### `controller/UserController`

- 모든 반환 타입 `ApiResponse` 래핑
- 데이터 없는 응답(닉네임 수정·비밀번호 변경·회원 탈퇴)은 `data: null` + 성공 메시지 반환

#### `controller/TourCourseController`

- `generateTourCourse`: `ResponseEntity<TourCourseGenerateResponseDto>` → `ResponseEntity<ApiResponse<TourCourseGenerateResponseDto>>`
- `updateCourseTitle`: `ResponseEntity<Void>` → `ResponseEntity<ApiResponse<Void>>`

#### `dataMig/controller/DataMigrationController`

- 3개 엔드포인트 모두 `Map<String, Object>` 직접 반환 → `ApiResponse<Map<String, Object>>` 래핑

#### `dataMig/controller/TestController`

- `String` 직접 반환 → `ApiResponse<String>` 래핑

### Removed

- `exception/ErrorResponse.java` — `ApiResponse<T>`로 완전 대체, 삭제

### Fixed

#### `GlobalExceptionHandler` — 내부 정보 노출 차단

- **`handleResponseStatusException`**: `getReason()` null 시 `ex.getMessage()` 반환 → `"요청을 처리할 수 없습니다."` 고정 문구로 교체
  - 기존: Spring 내부에서 reason 없이 던지면 `"401 UNAUTHORIZED"` 형태의 HTTP 내부 포맷 문자열이 클라이언트에 노출됨
- **`handleNoSuchElementException`**: `ex.getMessage()` null 가드 추가 → null 시 `"요청한 리소스를 찾을 수 없습니다."` 고정 문구 반환
  - 기존: `Optional.get()` 등 메시지 없이 던지는 경우 `null` 또는 Java 내부 메시지 노출 가능

#### `GlobalExceptionHandler` — validation 오류 응답 개선

- 기존: 필드명을 키로 하는 `Map<String, String>`을 `data`에 포함 + msg에 toString() 덧붙임
- 변경: `getDefaultMessage()` 값만 `", "` 로 join해 `msg`에 반환, `data: null`
- 필드명(내부 구현 정보) 미노출 — FE·클라이언트에 불필요한 정보 제거
- 반환 타입 `ApiResponse<Map<String, String>>` → `ApiResponse<Void>` 단순화
- `HashMap`, `Map` import 제거, `Collectors` import 추가

---

## [0.2.4] - 2026-06-06

### Added

#### AccessToken(body) + RefreshToken(HttpOnly Cookie) 분리 저장 방식 적용

**토큰 저장 전략 변경**
- AccessToken → 응답 바디, RefreshToken → `Set-Cookie: HttpOnly; SameSite=Lax` 헤더
- 적용 엔드포인트: 로그인 · 카카오 OAuth 콜백 · 토큰 재발급

**보안 설계 근거**
```
localStorage / sessionStorage → XSS로 스크립트가 토큰 탈취 가능
HttpOnly Cookie (RefreshToken) → JS 접근 불가 → XSS 차단
SameSite=Lax               → 외부 사이트 POST에 쿠키 미전송 → CSRF 차단
CORS allowCredentials=true
  + allowedOrigins 명시      → 브라우저 레벨 Origin 검증 (별도 Origin 헤더 검증 코드 불필요)
```

**쿠키 속성**
- `HttpOnly` — JS `document.cookie` 접근 차단
- `SameSite=Lax` — 외부 사이트 POST 요청에 쿠키 전송 차단 (CSRF 방어)
- `Secure` — 환경변수 `COOKIE_SECURE`(기본 `false`, 프로덕션 `true`)
- `Path=/api/v1/auth` — 재발급·로그아웃 엔드포인트에만 쿠키 전송
- `Max-Age=604800` — RefreshToken 만료(7일)와 동일

**`dto/AuthTokenResult.java`** (신규)
- 서비스 레이어 내부 전달용 record `(String accessToken, String refreshToken)`
- Controller에서 accessToken은 바디, refreshToken은 쿠키로 분리 처리

#### CORS 설정 추가 (`SecurityConfig`)

- `CorsConfigurationSource` 빈 등록
- `allowCredentials(true)` — 쿠키 포함 요청 허용
- `allowedOrigins` — 환경변수 `FRONT_ORIGIN`(기본 `http://localhost:3000`) 로 관리 (콤마로 다수 지정 가능)
- `allowedMethods` — GET / POST / PUT / PATCH / DELETE / OPTIONS
- `SecurityFilterChain`에 `.cors()` 명시 적용

### Changed

#### `AuthController` — 쿠키 기반 RefreshToken 처리

- **로그인** (`POST /login`): `AuthTokenResult` 수신 → refreshToken을 `Set-Cookie`로 세팅, 바디엔 accessToken만 반환
- **로그아웃** (`POST /logout`): `@CookieValue(required = false)`로 추출 → 쿠키 없으면 이미 로그아웃된 상태로 간주하고 쿠키만 클리어 후 204
- **토큰 재발급** (`POST /reissue`): 쿠키 없으면 `401 UNAUTHORIZED("RefreshToken 쿠키가 없습니다. 다시 로그인해 주세요.")`, 있으면 재발급 후 새 쿠키 세팅
- **카카오 콜백** (`POST /oauth/kakao/callback`): 로그인과 동일 방식
- 쿠키 set / clear 헬퍼 메서드 `setRefreshTokenCookie()` · `clearRefreshTokenCookie()` 추가

#### `AuthService` — 시그니처 변경

- `login()` 반환 타입: `LoginResponseDto` → `AuthTokenResult`
- `reissue()` 파라미터: `TokenReissueRequestDto` → `String refreshToken` / 반환 타입: `LoginResponseDto` → `AuthTokenResult`

#### `KakaoOAuthService` — 반환 타입 변경

- `kakaoLogin()` · `issueJwtTokens()` 반환 타입: `LoginResponseDto` → `AuthTokenResult`

#### `LoginResponseDto` — refreshToken 필드 제거

- `refreshToken` 필드 삭제, `accessToken`만 직렬화

#### `application.yaml` — CORS · Cookie 설정 추가

```yaml
cors:
  allowed-origins: ${FRONT_ORIGIN:http://localhost:3000}
cookie:
  secure: ${COOKIE_SECURE:false}
```

### Removed

- `dto/TokenReissueRequestDto.java` — 쿠키 방식 전환으로 불필요, 삭제

### Known Limitations (업데이트)

- `Secure=false` 기본값 → 로컬 개발(HTTP) 환경 대응. 프로덕션 배포 시 `COOKIE_SECURE=true` 환경변수 필수
- React FE에서 쿠키 포함 요청을 위해 `axios.defaults.withCredentials = true` 또는 `fetch credentials: 'include'` 설정 필요

---

## [0.2.3] - 2026-06-06

### Added

#### 카카오 OAuth 연동 — 토큰 검증 및 세션 발급 (`POST /api/v1/auth/oauth/kakao/callback`)

**엔드포인트**
- `POST /api/v1/auth/oauth/kakao/callback` 신규 추가
  - FE에서 카카오 SDK로 발급한 AccessToken을 받아 백엔드 자체 JWT 세션 발급
  - 인증 불필요 (기존 `/api/v1/auth/**` permitAll 규칙 그대로 적용)

**인증 처리 흐름**
```
FE  → 카카오 SDK로 직접 OAuth 처리 → kakaoAccessToken 취득
FE  → POST /api/v1/auth/oauth/kakao/callback { kakaoAccessToken }
BE  → GET https://kapi.kakao.com/v2/user/me  (Authorization: Bearer {kakaoAccessToken})
    → 카카오 유저 DB에서 id / email / nickname 응답
    → 신규면 자동 가입 / 기존이면 로그인
    → 자체 AccessToken + RefreshToken 발급 후 반환
```

- `application.yaml`에 `kakao.client-id` · `kakao.client-secret` · `kakao.redirect-uri` 등 카카오 앱 키 설정 **없음** — BE는 카카오 OAuth 인가 코드 교환 과정(`/oauth/token`)에 관여하지 않음
- `KakaoApiClient`는 FE가 전달한 AccessToken을 카카오 유저 API에 Bearer로 붙여 호출하는 것이 전부

**Request / Response**
```
POST /api/v1/auth/oauth/kakao/callback
{ "kakaoAccessToken": "..." }

Response 200 OK:
{ "accessToken": "...", "refreshToken": "..." }
```

**KakaoOAuthCallbackRequestDto** (`dto/`)
- `kakaoAccessToken` 필드, `@NotBlank` 검증

**KakaoApiClient** (`client/`)
- `RestClient`로 `https://kapi.kakao.com/v2/user/me` 호출
- `Authorization: Bearer {kakaoAccessToken}` 헤더 전송
- 4xx → `IllegalArgumentException("유효하지 않은 카카오 AccessToken입니다.")` 변환
- 5xx → `RuntimeException` 변환
- 중첩 정적 클래스 `KakaoUserInfo`, `KakaoAccount`, `Profile` 으로 응답 파싱
  - 이메일 미동의 시 `kakao_{id}@kakao.local` 가상 이메일 생성 (DB `email NOT NULL` 제약 대응)
  - 닉네임 누락 시 기본값 `"카카오유저"` 반환

**KakaoOAuthService** (`service/`)
- `kakaoLogin(String kakaoAccessToken)` — 카카오 로그인 통합 진입점
- `provider=kakao`, `providerId=kakaoId`로 기존 유저 조회
  - 없으면: `User.ofKakao()` 팩토리로 신규 가입
  - 동일 이메일 로컬 계정 존재 시: `user.linkKakao(providerId)` 로 카카오 연결
- 자체 AccessToken(15분) + RefreshToken(7일) 발급
- `RefreshToken` 저장 시 `provider="kakao"` 사용 (기존 로컬 토큰과 분리)

### Changed

#### User 엔티티 — 카카오 OAuth 지원 메서드 추가

- `User.ofKakao(String email, String nickname, String providerId)` 팩토리 메서드 신규 추가
  - `provider="kakao"`, `role="USER"`, `password=null`
- `user.linkKakao(String providerId)` 비즈니스 메서드 신규 추가
  - 기존 로컬 계정에 카카오 providerId를 연결할 때 사용

#### AuthController — 카카오 콜백 엔드포인트 추가

- `KakaoOAuthService` 의존성 추가 (생성자 주입)
- `POST /api/v1/auth/oauth/kakao/callback` 핸들러 메서드 추가

### Known Limitations (업데이트)

- 카카오 이메일 미동의 계정은 가상 이메일(`kakao_{id}@kakao.local`)로 가입되며, 이메일 기반 계정 찾기·비밀번호 변경 불가
- 카카오 계정과 로컬 계정을 동일 이메일로 연결 시 로컬 계정의 `provider` 필드가 `"kakao"`로 덮어씌워짐 — 추후 다중 provider 지원이 필요하면 별도 `UserProvider` 연결 테이블 도입 필요

---

## [0.2.2] - 2026-06-06

### Added

#### 코스 제목 수정 기능 (`PATCH /api/v1/tour-course/{courseId}/title`)

- `tour_course_user_defined` 테이블에 `title VARCHAR(255) NULL` 컬럼 추가 (DDL ALTER)
- `TourCourseUserDefined` 엔티티에 `title` 필드 및 `updateTitle(String title)` 비즈니스 메서드 추가
- `TourCourseTitleUpdateRequestDto` 신규 생성
  - `@NotBlank` — 빈 제목 거부
  - `@Size(max = 255)` — DB 컬럼 길이 일치
- `TourCourseService` 인터페이스에 `updateCourseTitle(Long courseId, String title, String userEmail)` 추가
- `TourCourseServiceImpl` 구현
  - `UserRepository`로 이메일 → 사용자 조회 (Soft Delete 제외)
  - 소유권 불일치 시 `AccessDeniedException` 발생 → `JwtAccessDeniedHandler`가 403 처리
  - 코스·사용자 미존재 시 `NoSuchElementException` 발생 → `GlobalExceptionHandler`가 404 처리
- `TourCourseController`에 `PATCH /{courseId}/title` 엔드포인트 추가
  - `Authentication.getName()`으로 JWT subject(이메일) 추출

### Changed

#### SecurityConfig — PATCH 제목 수정 엔드포인트 인증 규칙 추가

- `HttpMethod.PATCH, "/api/v1/tour-course/*/title"` → `hasAnyRole("USER", "ADMIN")` 규칙 추가
- 기존 `/api/v1/tour-course/**` permitAll 와일드카드보다 앞에 배치해 우선 적용

#### GlobalExceptionHandler — `NoSuchElementException` 404 핸들러 추가

- `NoSuchElementException` → HTTP 404 응답 처리 (`RuntimeException` 핸들러 앞에 등록)
- 코스·사용자 미존재 케이스에 명시적 404 반환

#### 설계 문서 업데이트 (`docs/`)

- BOQ10(`tour_course_user_defined.title`) 해결 처리: `FEATURES_BACK.md` CO1/CO3 갭 경고 제거, 스키마 결정 표에서 BOQ10 행 삭제
- `FEATURES_BACK.md` — CO7(코스 제목 수정) 기능 블록 신규 추가
- `PRD_BACK.md` — B-F4 title 갭 경고 → 구현 완료 메모로 교체, 도메인 모델 note 수정, API 테이블에 `PATCH /{courseId}/title` 행 추가, BOQ10 ✅ 확정으로 변경
- `PRD.md` — OQ14 🔶 → ✅, API 계약 테이블 및 인증 게이팅 정책에 제목 수정 행 추가

### Known Limitations (업데이트)

- ~~`tour_course_user_defined`에 `title` 컬럼 없음 (OQ14)~~ → **해결됨**
- `tour.stars`·`tour.likes` 컬럼 존재하나 실제 데이터 없음 (수집 방법 미결, OQ16)
- `tour_course_user_defined`에 `share_token` 컬럼 없음 (OQ13)
- `tour_course_user_defined_detail`에 POI별 예산 오버라이드 컬럼 없음 (OQ15)

---

## [0.2.1] - 2026-06-06

### Changed

#### TourCourseServiceImpl — POI 샘플링 로직 리팩토링

- 기존: `DetailCommon`, `DetailInfo`, 타입별 Repository(Attraction/Food/Culture 등) 10개를 contentId IN 절로 각각 조회 후 JSON 조합
- 변경: `Tour` 테이블 단일 조회 후 유형별 할당량(`QUOTA_*`) 기반 샘플링으로 교체
  - `MEALS_PER_DAY`·`MAX_TRIP_DAYS` 상수 도입 (식사 횟수 2회/일, 최대 7일 기준)
  - 유형별 할당량: FOOD 14, ATTRACTION 12, CULTURE 5, LEPORTS 3, ACCOMMODATION 4, SHOPPING 2, EVENT 2
  - `selectByTypeQuota()`: 유형별로 할당량만큼 무작위 선택, 부족 시 ATTRACTION으로 보충
  - `buildPlacesJson()`: `id`·`t`(type)·`n`(name) 3개 필드 경량 JSON으로 단순화 (좌표·운영시간 등 제거)
- 불필요해진 의존성 제거: `DetailCommonRepository`, `DetailInfoRepository`, `AttractionRepository`, `FoodRepository`, `CultureRepository`, `EventRepository`, `LeportsRepository`, `ShoppingRepository`, `AccommodationRepository` (9개 Repository 주입 제거)

#### TourRepository — 쿼리 방식 명시

- `findByLDongSignguCd()`: Spring Data 파생 쿼리 → `@Query` + `@Param` 명시적 JPQL 방식으로 변경 (컬럼명 규칙 불일치 예방)

#### 프롬프트 수정 (`system-prompt.txt`)

- RULES 항목 번호 재정렬 (좌표 기반 거리 계산 규칙 제거)
- contentId 참조 표현 명확화: "id values from the provided data" → 응답의 `contentId`와 입력 데이터의 `id` 필드 매핑 관계 명시
- 운영시간 미제공 시 기본 추정값 명시 (관광지 09:00–18:00, 음식점 11:00–21:00)

### Added

#### DB 스키마 변경 (DDL v3)

- `tour` 테이블에 추천 알고리즘용 컬럼 추가:
  - `stars DECIMAL(6,4)` — 여행지 별점
  - `likes INT` — 추천 개수
- 해당 컬럼은 향후 Groq AI 의존 제거 후 별점·추천수 기반 순수 알고리즘 코스 추천(Phase 3)의 핵심 데이터 원천으로 활용 예정

#### 설계 문서 신규 작성 (`docs/`)

- `docs/PRD_BACK.md` — 백엔드 기준 제품 기획서 (v0.1 → v0.2)
  - 기술 스택, 아키텍처, 핵심 기능 축(B-F1~B-F5), 도메인 모델, API 엔드포인트 현황, BOQ 포함
  - 코스 생성 3단계 진화 로드맵 정의 (Groq AI → stars·likes 가중치 → 순수 알고리즘)
  - DDL v2 스키마 갭 식별 및 BOQ9~BOQ12 추가 (예산 오버라이드·title·share_token·stars 데이터)
- `docs/FEATURES_BACK.md` — 백엔드 기능 분해도 (v0.1 → v0.2)
  - 도메인별 기능 블록: INF/AU/US/PO/CO/BU/SH/DA
  - `CO6` 신규: 별점·추천수 기반 알고리즘 코스 추천 (Phase 2/3 목표 기능)
  - `BU4` 신규: POI별 예산 오버라이드 저장 (스키마 갭 추적)
  - `DA4` 신규: `tour.stars`·`tour.likes` 데이터 수집 파이프라인
  - `BU1` 수정: `food_avg_price` 조인 키를 `contentId` → `lclsSystm3`(소분류코드)로 정정
  - `SH1` 수정: `share_token` 컬럼 제거 갭 명시 (BOQ11)
  - MVP/Post-MVP 우선순위 표 및 스키마 결정 선결 과제 표 추가
- `docs/PRD.md` — 통합 제품 기획서 (v0.1 → v0.2)
  - FE·BE 양측 통합 비전, 핵심 기능(F1~F3), 화면 인벤토리, API 계약 요약 통합
  - `§12 코스 생성 진화 로드맵` 신규 섹션: Phase 1(Groq AI) → Phase 2(가중치 보조) → Phase 3(순수 알고리즘) 3단계 정의, Phase 3 스코어링 공식 예시 포함
  - OQ13~OQ16 신규 미결 항목 (공유 스키마·title·예산 오버라이드·stars 데이터 수집)
  - API 계약 요약 테이블에 구현 상태 컬럼 추가

### Known Limitations (누적)

- `tour.stars`·`tour.likes` 컬럼 존재하나 실제 데이터 없음 (수집 방법 미결, OQ16)
- `tour_course_user_defined`에 `title`·`share_token` 컬럼 없음 (OQ13, OQ14)
- `tour_course_user_defined_detail`에 POI별 예산 오버라이드 컬럼 없음 (OQ15)
- POI 큐레이션 전용 API 미구현
- 코스 목록·상세·삭제 API 미구현
- 공유 스냅샷 API 미구현

---

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
  "sigunguCode": "35011"
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
2. Service → DB 조회 (sigunguCode 기준 또는 전체)
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

## [0.1.0] - 2026-05-16

### Added
- JWT 기반 인증/인가 시스템 구현
- 사용자 회원가입/로그인 기능
- 스프링 시큐리티 7 적용
- 비밀번호 암호화 (BCrypt)
- 한국관광공사 TourAPI 연동
- MariaDB 데이터베이스 설정

### Initial Release
- 프로젝트 초기 설정
- Spring Boot 4.0.6
- Java 25
- Gradle 9.4.1

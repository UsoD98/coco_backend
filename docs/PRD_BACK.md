# 경북 CoCo — 제품 기획서 (PRD, 백엔드 기준)

> 출처: 『2026 관광데이터 활용 공모전』 백엔드 repo 기준 명세.
> 본 문서는 프론트엔드 PRD([PRD_FRONT.md](PRD_FRONT.md))가 요구하는 데이터·액션을 **백엔드 관점**으로 재서술한다.
> 대상 범위: REST API 서버, DB 스키마, 외부 API 연동(한국관광공사 TourAPI, Groq AI), 인증/인가.
> 버전 0.5.0 · 기준일 2026-08-08.

> **⚠️ v0.5.0 아키텍처 전환 (공모전 의무사항)**: 관광 정보를 로컬 DB에 적재해 사용하는 것이 규정 위반으로 확인되어, TourAPI를 요청 시점에 라이브 호출하는 구조로 전면 전환했다. `tour`·`attraction`·`culture`·`event`·`tour_course`·`leports`·`accommodation`·`shopping`·`food`·`detail_common`·`detail_info` 및 하위 상세 테이블(`accommodation_detail_info`·`tour_course_detail_info`·`food_avg_price`)을 DB에서 제거했다. 응답 속도·TourAPI 일일 호출한도 보호를 위해 Caffeine 인메모리 캐시(TTL 6시간)를 도입했다. 사용자 참여로 쌓이는 별점(`stars`)·좋아요(`likes`)는 TourAPI가 제공하지 않는 앱 자체 데이터이므로 신규 `poi_rating` 테이블(`contentId` PK)로 분리 보존했다 (기존 `tour.stars` 285개 초기값 이관 완료).

## 1. 한 줄 정의

**경북 소규모 여행자(1~4인)를 위한 맞춤형 코스·예산 API 서버.**

TourAPI를 요청 시점에 라이브 호출(Caffeine 캐시 경유)해 관광 정보를 조회하고, Groq AI로 여행 코스를 생성하며, 프론트엔드가 소비하는 REST API를 제공한다.
최종 목표는 AI 의존 없이 **별점(`stars`)·추천수(`likes`) 기반 알고리즘으로 여행자 조건에 맞는 코스를 자동 추천**하는 서비스로 진화하는 것이다.

---

## 2. 배경 및 백엔드 책임 범위

프론트엔드는 화면 전용이며, 아래 모든 책임은 백엔드에 귀속된다.

| 책임 영역 | 내용 |
| --- | --- |
| **관광 정보 조회** | 한국관광공사 TourAPI **라이브 호출** (로컬 DB 미저장, Caffeine 캐시 TTL 6h 경유) |
| **비용 저장** | POI별 실제 비용은 FE가 입력·산정한 값을 그대로 받아 `tour_course_user_defined_detail.cost`에 저장만 담당 (서버 측 평균 객단가 산출·추정 로직 없음 — 2026-08-16 BU1 취소) |
| **AI 코스 생성 (현재)** | Groq LLM API를 활용한 일정별 코스 자동 생성 |
| **알고리즘 추천 (목표)** | `stars`·`likes` 기반 POI 스코어링으로 LLM 없이 여행자 조건 맞춤 코스 추천 |
| **인증/인가** | JWT Stateless 인증, 카카오 OAuth 연동 |
| **코스 영속** | 사용자 정의 코스 저장·조회·삭제, 공유 스냅샷 생성 |
| **POI 큐레이션** | 지역·인원버킷·테마 기반 필터링된 POI 목록 응답 |

---

## 3. 기술 스택 및 아키텍처

### 기술 스택

| 구분 | 기술 |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.0 |
| Build | Gradle |
| DB | MariaDB (JPA/Hibernate + MyBatis 혼용) — 관광 정보 미저장, 유저/코스/평점 데이터만 |
| 캐시 | Caffeine (인프로세스, TTL 6h) — TourAPI 응답 캐싱 전용 |
| 인증 | JWT (Stateless), 카카오 OAuth (예정) |
| AI | Groq API (LLaMA 계열 모델) |
| 외부 API | 한국관광공사 TourAPI v2 (라이브 호출) |

### 계층 구조

```
Controller (REST API, /api/v1/**)
    ↓
Service (비즈니스 로직, 트랜잭션)
    ↓
Repository (JPA) / Mapper (MyBatis)
    ↓
MariaDB
```

### 공통 응답 포맷

모든 엔드포인트는 `ApiResponse<T>` 래퍼를 통해 아래 세 필드를 반환한다.

```json
{
  "code": "200",
  "msg": "처리 결과 메시지",
  "data": { }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `code` | String | HTTP 상태 코드 문자열 (예: `"200"`, `"400"`, `"401"`) |
| `msg` | String | 처리 결과 메시지 (성공/실패 모두 포함) |
| `data` | T (Generic) | 응답 데이터. 데이터 없는 경우 `null` |

- 성공 시 data: 기존 응답 DTO 그대로 / 실패 시 data: 항상 `null`
- validation 오류(`@Valid`)는 필드명 미노출 — `getDefaultMessage()` 값만 `", "` 로 join해 `msg`에 반환
- `ResponseStatusException` reason null 시 / `NoSuchElementException` 메시지 null 시 — 내부 Java 메시지 대신 고정 한국어 문구 반환
- Security 레이어 401/403도 동일 포맷 반환
- `logout` 등 이전 204 No Content 응답은 body 포맷 충돌로 인해 200으로 변경

### 패키지 구조

```
com.eodegano.cocobackend/
├── config/          - Spring Security, Bean 설정
├── controller/      - REST 엔드포인트
├── service/         - 비즈니스 로직
├── domain/          - JPA 엔티티
│   └── enums/       - PlaceType, TransportType
├── dto/             - 요청/응답 DTO
├── repository/      - JPA Repository
├── security/        - JWT 필터/Provider/핸들러
├── client/          - 외부 API 클라이언트 (Groq, TourAPI)
├── cache/           - Caffeine 캐시 설정 (지역 POI 후보 리스트·POI 상세, TTL 6h)
└── dataMig/         - TourAPI 라이브 연동 클라이언트 (`TourApiClient`). v0.5.0부터 DB 적재 배치는 제거되고 라이브 조회 기반으로만 재사용
```

---

## 4. 핵심 기능 축

### B-F1. TourAPI 라이브 조회 및 큐레이션 API

- **v0.5.0부터 로컬 DB 적재 폐지**: 공모전 규정상 관광 정보를 로컬에 저장할 수 없어, 경북(areaCode=35) + 시군구 코드 기반 조회를 매 요청 TourAPI 라이브 호출(`TourApiClient.areaBasedList2`)로 수행한다.
- 라이브 호출 결과는 Caffeine 캐시(지역 후보 리스트, TTL 6h)를 경유해 응답 속도와 TourAPI 일일 호출한도를 보호한다.
- **2026-08-16(v0.5.8) 개선**: 후보 리스트 수집을 7개 콘텐츠타입별로 분리(타입당 최대 3000건, 페이지 크기 100→300)해 물량이 큰 타입이 소형 타입을 밀어내는 편향을 줄이고, 페이지·타입을 가상 스레드로 병렬 수집해 콜드스타트 지연을 단축했다. 동시 HTTP 요청은 `Semaphore(4)`로 제한하고 429/5xx는 재시도(최대 3회) — 일일 호출 한도 1000건 기준 후보 캐시 워밍에 최대 280건/일을 쓰고 나머지는 상세조회용으로 남긴다. 배포 직후·TTL 만료 전에는 `PoiCacheWarmupScheduler`가 백그라운드로 캐시를 미리 채운다.
- POI 개별 상세(`detailCommon2`/`detailIntro2`/`detailInfo2`)도 `contentId` 키로 별도 Caffeine 캐시(TTL 6h)에 담아 코스 생성·조회·공개 view가 공통 재사용한다.
- 큐레이션 조회 API: 지역(sigunguCode)·인원 버킷(1/2/3-4)·테마·콘텐츠 유형을 파라미터로 받아 필터링된 POI 목록 반환 (좌표·썸네일·예산 기본값 포함). 필터링·정렬은 라이브 조회 결과를 애플리케이션 메모리에서 처리.
- 별점(`stars`)·좋아요(`likes`)는 TourAPI에 없는 앱 자체 데이터이므로 `poi_rating` 테이블(`contentId` PK)에서 별도 관리 — 좋아요/평점 액션 시 on-demand 행 생성, TourAPI 존재 여부 재검증 없음.
- **폐지된 설계**: 월별 마이그레이션 배치(`DataMigrationController`/`DataMigrationService`), `ON DUPLICATE KEY UPDATE` 업서트 패턴 — 로컬 적재 자체가 없으므로 더 이상 불필요.

### B-F2. 여행 코스 자동 생성 — 현재 및 목표 진화

**현재 구현 (v0.5.0 — TourAPI 라이브 조회 전환)**
- `POST /api/v1/tour-course` — 인원·기간·이동수단·테마·시군구(복수, `sigunguCodes: []`) 입력을 받아 Groq LLM으로 일정별(Day N) 코스 자동 생성.
- TourAPI 라이브 조회(캐시 경유) 결과에서 지역 POI를 유형별 할당량으로 샘플링 → AI 프롬프트 컨텍스트로 전달. (v0.4.2까지는 로컬 `tour` 테이블 스캔이었으나 v0.5.0에서 라이브 호출로 대체)
- AI 응답 contentId 검증은 요청 내에서 이미 확보한 후보 리스트(캐시)와 메모리 대조 방식으로 수행 — 추가 DB/API 호출 없음.
- **v0.2.6 샘플링 개선**: Hard exclusion(stars ≤ 1) → Tier A(stars ≥ 4, 70%) / Tier B(stars 2-3·null, 30%) 확률적 Tier 샘플링. likes 있으면 Tier 내 DESC 정렬, 없으면 shuffle. Cold-start(null) → Tier B 편입.
- **v0.3.1 Rate Limit 개선**: Groq API 429 응답 시 `retry-after` 헤더 기반 대기(기본 20초)로 재시도. 기타 에러와 명시적으로 분리.
- **v0.6.0 reasoning 모델 대응**: v0.5.12에서 전환한 `openai/gpt-oss-20b`가 reasoning 모델이라, 추론(CoT) 토큰이 `max_tokens` 예산을 다 소진하면 최종 답변(`content`)이 빈 문자열로 반환되어 JSON 파싱이 실패하는 문제 발생. `reasoning_effort: "low"`(코스 생성은 정해진 스키마에 후보를 배치하는 단순 작업이라 깊은 추론 불필요)·`reasoning_format: "parsed"`(reasoning을 content와 분리, `<think>` 텍스트로 인한 파싱 오염 방지)를 요청에 추가. 응답의 `finish_reason`·`usage`(prompt/completion/total tokens)를 캡처해 매 호출 성공 시 로그로 남기고, `finish_reason=length`(잘림) 시 별도 경고 로그 + 파싱 실패 시에도 해당 정보를 에러 로그에 포함해 향후 원인 진단이 가능하도록 개선.
- **v0.6.1 AI 에러 전용 응답(HTTP 499)**: Groq 호출·파싱·응답 검증 단계에서 발생하는 모든 에러를 `AiCourseGenerationException`으로 통일하고 `GlobalExceptionHandler`가 이를 **499**로 매핑 — 일반 400/500과 구분되는 "AI 생성 실패" 케이스로 프론트가 별도 처리 가능. 응답 `data`에 `errorCode`·`retryable`·`finishReason`을 포함해 원인·재시도 가능 여부까지 전달.
- **v0.4.0 장소별 상세 필드 추가**: 응답 `PlaceInfo`에 `durationMinutes`(AI 추정·DB 저장)·`thumbnailImg`·`operatingHours`·`cost` 4개 필드 추가. CO4·SH2도 동일 필드 반환.
- AI 응답 검증(날짜 범위, contentId 실존, PlaceType 유효성) 후 `TourCourseUserDefined` + `TourCourseUserDefinedDetail` 저장.
- 비로그인(userId=null) 허용으로 생성 후 저장까지 동작.

**v1.0 목표 (공모전 출시) — 2026-08-16 확정**
- CO1(Groq AI 코스 생성)을 그대로 유지한다. stars/likes 기반 Tier 샘플링(v0.2.6)까지는 v1에 포함되지만, Groq 자체를 대체하는 순수 알고리즘 전환은 v1 범위에서 제외한다.
- 사유: 현재 Groq 기반 추천 품질이 충분히 좋다고 판단 — 무리하게 알고리즘으로 전환하기보다 출시 후 더 나은 설계가 확보되면 그때 붙인다.

**중기 목표 (v2.0+ 고도화 — 보류, v1 이후 재검토)**
- Tier 샘플링에서 나아가 Groq 완전 제거.
- `poi_rating.stars`·`poi_rating.likes` 스코어링 결과로 직접 Day별 일정 조합.
- TourAPI 라이브 조회(캐시 경유) 결과의 지역코드(`LDONGSIGNGUCD`) + `contenttypeid` + 스코어 기반 정렬 규칙 엔진으로 구현.

**최종 목표 (v3.0+ 고도화 — 보류, v1 이후 재검토)**
- Groq API 호출 없이 여행자가 입력한 조건(인원 버킷·테마·이동수단·기간·시군구)과 POI의 `stars`·`likes`를 결합한 스코어링 알고리즘으로 Day별 최적 코스 자동 생성.
- 사용자 `travel_type`(개인 선호 여행 타입)을 추가 가중치로 반영.
- 외부 LLM 의존 제거 → 응답 속도 향상·비용 절감·예측 가능성 확보.

### B-F3. POI별 비용 저장 (2026-08-16 재설계)

- 서버 측 평균 객단가 산출·교통비 추정 로직은 두지 않는다. 음식점 평균가(BU1)·교통비 추정(BU3) 모두 취소.
- FE가 산정한 POI별 실제 비용을 그대로 받아 `tour_course_user_defined_detail.cost INT NULL`에 저장 (BOQ9 확정, 컬럼 추가 완료).
- 코스 생성 직후(AI 응답)에는 값이 없어 TourAPI 라이브 `usefee` → type별 기본값 순으로 폴백 표시하고, `PUT /{courseId}` 갱신 시 FE가 넘긴 `cost`가 저장되어 이후 조회에서 우선 반환된다.

### B-F4. 코스 저장·조회·삭제

- 사용자 정의 코스: `TourCourseUserDefined` (헤더: 인원·기간·이동수단·테마) + `TourCourseUserDefinedDetail` (일정 상세: 날짜·순서·시간·contentId·타입).
- 비로그인 시 userId=null로 임시 저장 → 로그인 후 `PATCH /{courseId}/assign`으로 소유권 이전(`assignUser()`). (✅ 구현 완료)
- 코스 목록 조회: `GET /` — 사용자 ID로 전체 코스 목록 반환. (✅ 구현 완료)
- 코스 상세 조회: `GET /{courseId}` — 소유자 인증 후 헤더+일정 상세 반환. (✅ 구현 완료)
- 코스 삭제: `DELETE /{courseId}` — 소유자 인증 후 상세→헤더 순 삭제. (✅ 구현 완료)
- 코스 제목 수정: `PATCH /{courseId}/title` — 소유권 확인 후 `updateTitle()` 호출. (✅ 구현 완료)
- 코스 일정 수정: `PATCH /{courseId}` — 소유권 확인 후 일정 상세 전량 교체(삭제 후 재삽입), CO1과 동일한 날짜·contentId·type 검증. (✅ 구현 완료)
- 공개 뷰: `GET /{courseId}/view` — 인증 없이 courseId로 공개 조회. 카카오 공유 수신자용. (✅ 구현 완료, BOQ11 확정)

### B-F5. 인증/인가 (JWT + 카카오 OAuth)

- 로컬 JWT: 로그인 → AccessToken(15분) + RefreshToken(7일) 발급. RefreshToken DB 저장, 로테이션 방식 갱신.
- 카카오 OAuth: FE에서 카카오 로그인 후 백엔드로 토큰 전달 → 검증·세션 발급. (`KakaoOAuthService` + `KakaoApiClient` 구현 완료)
- 인증 게이팅: 탐색·AI 코스 생성은 게스트 허용, 코스 저장·조회·삭제는 인증 필요.

---

## 5. 화면 ↔ 백엔드 API 매핑

프론트엔드 화면(S*)별로 백엔드가 제공해야 할 API를 정의한다.

| FE 화면 | 필요 API (백엔드 책임) | 구현 상태 |
| --- | --- | --- |
| S1 메인/검색 | ~~시군구 목록, 데이터 보유 시군구 플래그~~(취소), 테마 목록 | 미구현 (상수 또는 정적 응답) |
| S2 플래너 | POI 큐레이션 목록, 기본 추천 코스, POI 상세, 교통비 추정 | POI는 AI 생성 시 포함 / 큐레이션 전용 API 미구현 |
| S2 플래너 저장 | 코스 저장·수정·삭제 (로그인 사용자) | 구현 완료 (생성·일정 수정·삭제·소유권 이전) |
| S2a POI 상세 드로어 | contentId 기반 POI 상세 통합 조회 | 구현 완료 |
| S2b 로그인 모달 | 카카오 OAuth 콜백, 세션/토큰 발급 | 로컬·카카오 JWT 모두 구현 완료 |
| S3 공유 뷰어 | 공유 ID로 코스+예산 스냅샷 조회, 공유 생성 | 공개 뷰(`/{courseId}/view`) 구현 / 예산 스냅샷 미구현 |
| S4 컬렉션 | 사용자 코스 목록·상세 조회, 삭제 | 구현 완료 (코스 목록·상세·삭제·제목 수정) |
| S5/S6 인증 | 로그인, 회원가입, 로그아웃, 토큰 갱신 | 로컬·카카오 로그인 모두 구현 완료 |

---

## 6. 도메인 모델 (주요 엔티티)

### 사용자·인증

| 엔티티 | 테이블 | 역할 |
| --- | --- | --- |
| `User` | `user` | 회원 (로컬·OAuth 지원 예정), Soft Delete |
| `RefreshToken` | `refresh_token` | JWT RefreshToken, User 1:N, 로테이션 |

### 관광 정보 — v0.5.0 라이브 API 전환

⚠️ **아래 엔티티/테이블은 v0.5.0에서 전부 제거됨** (공모전 규정 — 관광 정보 로컬 DB 미저장): `Tour`(`tour`), `Attraction`(`attraction`), `Culture`(`culture`), `Event`(`event`), `Leports`(`leports`), `Accommodation`(`accommodation`), `AccommodationDetailInfo`(`accommodation_detail_info`), `Shopping`(`shopping`), `Food`(`food`), `FoodAvgPrice`(`food_avg_price`), `DetailCommon`(`detail_common`), `DetailInfo`(`detail_info`), `TourCourse`(`tour_course`), `TourCourseDetailInfo`(`tour_course_detail_info`).

대체 구조: `TourApiClient`로 TourAPI를 라이브 호출(`areaBasedList2`/`detailCommon2`/`detailIntro2`/`detailInfo2`) → Caffeine 캐시(TTL 6h, 지역 후보 리스트/POI 상세 2종) → 애플리케이션 메모리에서 유형 분류·필터링. `contenttypeid`(12/14/15/28/32/38/39) 기준 유형 구분은 `PlaceType` enum 로직 그대로 유지.

| 엔티티 | 테이블 | 역할 |
| --- | --- | --- |
| `PoiRating` | `poi_rating` | **신규(v0.5.0)** — `contentId` PK, `stars`(BigDecimal), `likes`(Integer). TourAPI가 제공하지 않는 앱 자체 데이터만 보존. 좋아요/평점 액션 시 on-demand 생성 (기존 `tour.stars`·`tour.likes` 대체, 285개 stars 초기값 이관 완료) |
| `MstSigungu` | `mst_sigungu` | 경북 시군구 코드·이름 기준정보 (레포지토리·서비스 미연결, 기준정보 목적 유지) |
| `MstTheme` | `mst_theme` | 여행 테마 코드·이름 기준정보 (레포지토리·서비스 미연결, 기준정보 목적 유지) |

### 사용자 정의 코스

| 엔티티 | 테이블 | 역할 |
| --- | --- | --- |
| `TourCourseUserDefined` | `tour_course_user_defined` | 사용자 생성 코스 헤더 (userId·인원·기간·이동수단·테마JSON·title). ⚠️ total_budget·share_token 컬럼 없음 (BOQ11 확정에 따라 추가 계획 없음) |
| `TourCourseUserDefinedDetail` | `tour_course_user_defined_detail` | 코스 일정 상세 (날짜·순서·시간·`duration_minutes`·type·contentId·`cost`). `cost INT NULL` 2026-08-16 추가 완료 (BOQ9) |

> **스키마 설계 이력**: DDL v1에서 `title`, `total_budget`, `per_budget`, `course_data`(JSON), `share_token`, `is_public`이 있던 `tour_course_user_defined`가 2026-05-30 drop 후 재설계되어 현재 구조로 변경됨. BOQ9~BOQ11 모두 확정 완료.

---

## 7. 현재 구현 상태 요약

### 구현 완료

- JWT 기반 로컬 인증/인가 (로그인·로그아웃·토큰 갱신·회원 CRUD)
- **카카오 OAuth 연동** (`POST /api/v1/auth/oauth/kakao/callback`) — FE 전달 카카오 AccessToken 검증, 신규 가입·기존 계정 연결·자체 JWT 발급 (`KakaoOAuthService` + `KakaoApiClient`)
- **TourAPI 라이브 연동 + Caffeine 캐시 (v0.5.0)** — `TourApiClient` 라이브 호출, 지역 후보 리스트·POI 상세 각각 TTL 6h 캐시. 로컬 DB 적재(`DataMigrationController`)는 폐지
- **후보 수집 방식 개선 + 캐시 워밍 (v0.5.8, 2026-08-16)** — 콘텐츠타입별 분리 수집·페이지 확대(100→300)·병렬 수집·재시도로 추천 범위 확장 및 콜드스타트 지연 단축. `PoiCacheWarmupScheduler`로 배포 직후·TTL 만료 전 백그라운드 워밍
- Groq AI 여행 코스 생성 (`POST /api/v1/tour-course`) — 비로그인 허용, userId=null
- `TourCourseUserDefined` + `TourCourseUserDefinedDetail` 저장 (`duration_minutes` 포함)
- **Tier 기반 확률적 POI 샘플링** — stars Hard exclusion + Tier A/B 분할 + likes 보조 정렬 (`TourCourseServiceImpl.selectByTypeQuota`)
- **장소별 상세 필드** — `durationMinutes`(AI 추정)·`thumbnailImg`·`operatingHours`·`cost` 4개 필드를 CO1·CO4·SH2 응답에 포함
- `poi_rating.stars`·`poi_rating.likes` 컬럼 (v0.5.0부터 `tour` 테이블에서 분리), `user_poi_like` 중계 테이블로 likes 수집 파이프라인 구축
- **POI 좋아요 토글 API** (`POST /api/v1/poi/{contentId}/like`) — `user_poi_like` 중복 방지, 원자적 JPQL increment/decrement
- `tour_course_user_defined.title VARCHAR(255)` 컬럼 + 코스 제목 수정 API (`PATCH /{courseId}/title`)
- **코스 소유권 이전** (`PATCH /{courseId}/assign`) — userId=null 코스에 로그인 사용자 귀속
- **코스 목록 조회** (`GET /api/v1/tour-course`) — 로그인 사용자 전체 코스 목록
- **코스 상세 조회** (`GET /api/v1/tour-course/{courseId}`) — 소유자 인증 + 일정 상세 반환
- **코스 삭제** (`DELETE /api/v1/tour-course/{courseId}`) — 소유자 인증 + detail→course 순 삭제
- **공개 코스 뷰** (`GET /api/v1/tour-course/{courseId}/view`) — 인증 불필요, 카카오 공유 수신자용
- **공통 응답 포맷 표준화** — `ApiResponse<T>` 래퍼 도입, GlobalExceptionHandler·Security 핸들러 통일 (`INF1`)

### 미구현

1. ~~시군구 목록·데이터 보유 여부 응답 API~~ (취소)
2. ~~교통비 추정 계산 로직 및 API~~ (취소 — FE 전담)
3. ~~예산 메타데이터(평균 객단가) API~~ (취소 — 사용자 직접 입력으로 대체, `tour_course_user_defined_detail.cost` 저장은 구현 완료)
4. TourAPI 데이터 월 1회 주기 수집 배치 스케줄링

> ✅ v0.5.3: POI 큐레이션 전용 조회 API(PO2), POI 상세 통합 조회 API(PO3) 구현 완료.

---

## 8. API 엔드포인트 현황

### 인증 (`/api/v1/auth`)

| 메서드 | 경로 | 설명 | 구현 |
| --- | --- | --- | --- |
| POST | `/login` | 로그인 (AccessToken + RefreshToken) | ✅ |
| POST | `/logout` | 로그아웃 (RefreshToken 삭제) | ✅ |
| POST | `/reissue` | 토큰 갱신 (RefreshToken 로테이션) | ✅ |
| POST | `/oauth/kakao/callback` | 카카오 OAuth 콜백 처리 | ✅ |

### 회원 (`/api/v1/user`)

| 메서드 | 경로 | 설명 | 구현 |
| --- | --- | --- | --- |
| POST | `/join` | 회원가입 | ✅ |
| GET | `/{userId}` | 회원 정보 조회 | ✅ |
| PATCH | `/{userId}/nickname` | 닉네임 수정 | ✅ |
| PATCH | `/{userId}/password` | 비밀번호 변경 | ✅ |
| DELETE | `/{userId}` | 회원 탈퇴 (Soft Delete) | ✅ |

### 여행 코스 (`/api/v1/tour-course`)

| 메서드 | 경로 | 설명 | 구현 |
| --- | --- | --- | --- |
| POST | `/` | AI 코스 생성 (Groq 연동) | ✅ |
| GET | `/` | 내 코스 목록 조회 (인증 필요) | ✅ |
| GET | `/{courseId}` | 코스 상세 조회 (인증·소유자) | ✅ |
| DELETE | `/{courseId}` | 코스 삭제 (인증·소유자) | ✅ |
| GET | `/{courseId}/view` | 공개 코스 뷰 (인증 불필요) | ✅ |
| PATCH | `/{courseId}/title` | 코스 제목 수정 (인증·소유자) | ✅ |
| PATCH | `/{courseId}` | 코스 일정 수정 (인증·소유자) | ✅ |
| PATCH | `/{courseId}/assign` | 코스 소유권 이전 (인증 필요) | ✅ |

### POI (`/api/v1/poi`)

| 메서드 | 경로 | 설명 | 구현 |
| --- | --- | --- | --- |
| GET | `/` | 큐레이션 POI 목록 (지역·유형 필터만 구현, 인원버킷·테마·avgPrice는 보류 — BOQ14) | 🔧 |
| GET | `/{contentId}` | POI 상세 통합 조회 | ✅ |
| POST | `/{contentId}/like` | POI 좋아요 토글 (인증 필요) | ✅ |

### 관리자 (`/api/admin`)

| 메서드 | 경로 | 설명 | 구현 |
| --- | --- | --- | --- |
| POST | `/migration/**` | ~~TourAPI 데이터 수집·적재~~ — **v0.5.0에서 컨트롤러 삭제** (로컬 DB 미저장 원칙) | ❌ (제거됨) |

---

## 9. 가정 및 미결 (Open Questions)

> 프론트엔드 PRD의 OQ와 연계하여 백엔드 관점에서 추가로 필요한 결정 사항을 기술한다.

- **BOQ1. 인원 버킷 정의** — FE 기준(`1→1`, `2→2`, `≥3→'3-4'`)을 백엔드 쿼리 파라미터로 어떻게 매핑할지 확정 필요. 현재 `peopleCount` 그대로 수신.
- **BOQ2. 교통비 추정 알고리즘** — ✅ **취소 (2026-08-16)**: 이동 관련 비용 계산은 BE 책임에서 제외, FE가 전담.
- **BOQ3. 평균 객단가 데이터 출처** — ✅ **취소 (2026-08-16)**: 대체 데이터 소스를 찾지 않고 BU1 기능 자체를 취소. 사용자가 실제 비용을 직접 입력하는 방식(BOQ9/BU4)으로 대체.
- **BOQ4. 비로그인 코스 소유권 이전 타이밍** — 로그인 모달 성공 직후 `PATCH /api/v1/tour-course/{courseId}/assign` 방식 vs. FE 세션토큰 전달 방식 결정 필요.
- **BOQ5. 공유 링크 만료 정책** — 스냅샷 TTL(무제한 vs. N일) 및 삭제 정책 확정 필요.
- **BOQ6. 카카오 OAuth 처리 방식** — ✅ **확정·구현 완료 (v0.2.7)**: FE에서 발급된 카카오 AccessToken을 `POST /api/v1/auth/oauth/kakao/callback`으로 전달 → `KakaoApiClient`로 카카오 사용자 정보 검증 → 자체 JWT 발급. 기존 로컬 계정과 이메일 일치 시 카카오 연결, 신규 사용자는 자동 가입.
- **BOQ7. 추천 코스 생성 주체** — 기본 추천 코스를 Groq AI가 생성하는지(현재 방식), `stars`·`likes` 기반 알고리즘으로 전환하는지, 또는 병행하는지 확정 필요. (FE PRD OQ9)
- **BOQ8. 데이터 커버리지 범위** (취소 — PO4 스코프아웃에 따라 논의 불필요)
- **BOQ9. POI별 비용 저장** — ✅ **확정·구현 완료 (2026-08-16)**: `tour_course_user_defined_detail.cost INT NULL` 컬럼 추가 (오버라이드 개념이 아니라 FE 입력값을 그대로 저장). `PUT /{courseId}`(`PlaceUpdate.cost`)로 저장, 조회 시 저장값 우선 반환.
- **BOQ10. 코스 제목(title) 저장** — ✅ **확정·구현 완료**: `tour_course_user_defined.title VARCHAR(255) NULL` 컬럼 추가(DDL ALTER). 코스 제목 수정 API(`PATCH /{courseId}/title`) 구현 완료.
- **BOQ11. 공유 기능 스키마** — ✅ **확정 (v0.2.6)**: `share_snapshot` 테이블·`share_token` 컬럼 미추가. FE가 카카오 SDK로 courseId 기반 딥링크를 생성하고, 수신자는 `GET /{courseId}/view` 공개 API로 조회하는 방식으로 결정.
- **BOQ12. `stars`·`likes` 데이터 수집 방법** — 부분 확정: `likes`는 PO5 좋아요 토글 API(v0.2.6)로 앱 내 수집. `stars`는 AI 검색 기반 수동 입력 예정 (크롤링 방법 미확정). v0.5.0부터 저장 위치는 `poi_rating` 테이블.
- **BOQ13. Caffeine 캐시 TTL** — ✅ **확정 (v0.5.0)**: 지역 POI 후보 리스트·POI 개별 상세 모두 TTL 6시간. 소규모 트래픽 특성상 신선도·호출량 절감 사이 타협점으로 결정. Redis 대신 Caffeine 채택 이유: 1GB 메모리 프리티어 서버에서 별도 프로세스 오버헤드를 피하기 위함.
- **BOQ14. 예산 메타데이터 근거 테이블 소실** — ✅ **확정 (2026-08-16)**: `food_avg_price`(BU1 음식점 평균 객단가) 테이블이 로컬 DB 미저장 원칙에 따라 제거된 뒤, 대체 데이터 소스를 찾지 않고 BU1 기능 자체를 취소하기로 결정. `GET /api/v1/poi`의 `avgPrice`는 계속 항상 `null` 반환 (의도된 최종 동작, TODO 아님). **BU2(숙박 인원별 분류)는 2026-08-08 기획 결정으로 스코프 아웃 확정** — `peopleCount`는 필수 입력 파라미터로만 받고 필터링에는 사용하지 않음.
  - **TODO**: `theme`(테마 필터링) 파라미터는 데이터 소스·매핑 설계가 없어 현재 미구현 상태로 보류됨 (컨트롤러에서 파라미터 자체를 받지 않음). 설계 확정 후 추가 필요.
- **BOQ15. DB·외부 API(TourAPI) 연동 통합 테스트 인프라** — 🔶 **v0.5.3 신규, TODO(당장 착수 안 함)**: 현재 테스트는 Mockito 단위 테스트(`PoiCurationServiceImplTest`·`PoiDetailServiceImplTest`·`PoiLikeServiceImplTest`·`TourLiveDataServiceTest`)와 `@WebMvcTest` 슬라이스 테스트(`PoiControllerTest`, 서비스 계층은 Mock)까지만 구성되어 있고, 실제 MariaDB 쓰기(`PoiRating`/`UserPoiLike` insert·update)와 실제 TourAPI 응답 계약을 검증하는 테스트는 없음. 도입 시 필요한 것: (1) Testcontainers 기반 MariaDB 통합 테스트 환경, (2) `TourApiClient`가 `RestClient.create()`를 필드에서 직접 생성해 현재는 가로챌 수 없으므로 `RestClient.Builder` 주입으로 리팩터링 후 `MockRestServiceServer`/WireMock으로 스텁. 필요성은 확인됐으나 우선순위가 낮아 보류 — 착수 시점에 재검토.

---

## 참고

- 기능 분해도: [FEATURES_BACK.md](FEATURES_BACK.md)
- 프론트엔드 PRD: [PRD_FRONT.md](PRD_FRONT.md)
- 개발 가이드: [CLAUDE.md](CLAUDE.md)
- 기능 상세 문서: [func/FEAT_TOURCOURSE_GEN.md](func/FEAT_TOURCOURSE_GEN.md)

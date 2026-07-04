# 경북 CoCo — 제품 기획서 (PRD, 백엔드 기준)

> 출처: 『2026 관광데이터 활용 공모전』 백엔드 repo 기준 명세.
> 본 문서는 프론트엔드 PRD([PRD_FRONT.md](PRD_FRONT.md))가 요구하는 데이터·액션을 **백엔드 관점**으로 재서술한다.
> 대상 범위: REST API 서버, DB 스키마, 외부 API 연동(한국관광공사 TourAPI, Groq AI), 인증/인가.
> 버전 0.4.2 · 기준일 2026-07-04.

---

## 1. 한 줄 정의

**경북 소규모 여행자(1~4인)를 위한 맞춤형 코스·예산 API 서버.**

TourAPI 데이터를 수집·가공해 DB에 적재하고, Groq AI로 여행 코스를 생성하며, 프론트엔드가 소비하는 REST API를 제공한다.
최종 목표는 AI 의존 없이 **별점(`stars`)·추천수(`likes`) 기반 알고리즘으로 여행자 조건에 맞는 코스를 자동 추천**하는 서비스로 진화하는 것이다.

---

## 2. 배경 및 백엔드 책임 범위

프론트엔드는 화면 전용이며, 아래 모든 책임은 백엔드에 귀속된다.

| 책임 영역 | 내용 |
| --- | --- |
| **데이터 수집·적재** | 한국관광공사 TourAPI → 자체 DB (월 1회 주기 수집) |
| **인원·예산 메타데이터** | POI별 평균 객단가(식비·숙박비·입장료) 산출 및 제공 |
| **AI 코스 생성 (현재)** | Groq LLM API를 활용한 일정별 코스 자동 생성 |
| **알고리즘 추천 (목표)** | `stars`·`likes` 기반 POI 스코어링으로 LLM 없이 여행자 조건 맞춤 코스 추천 |
| **교통비 추정** | DB 좌표(MapX/MapY) 기반 이동거리 계산 → 유류비/대중교통비 추정 |
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
| DB | MariaDB (JPA/Hibernate + MyBatis 혼용) |
| 인증 | JWT (Stateless), 카카오 OAuth (예정) |
| AI | Groq API (LLaMA 계열 모델) |
| 외부 API | 한국관광공사 TourAPI v2 |

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
└── dataMig/         - TourAPI 데이터 마이그레이션
```

---

## 4. 핵심 기능 축

### B-F1. TourAPI 데이터 수집 및 큐레이션 API

- 경북(areaCode=35) + 시군구 코드 기반으로 관광지(12)·문화시설(14)·숙박(32)·음식점(39) 데이터를 수집해 DB 적재.
- 수집 주기: 월 1회 배치 또는 관리자 트리거 (`/api/admin/migration/**`).
- 큐레이션 조회 API: 지역(sigunguCode)·인원 버킷(1/2/3-4)·테마·콘텐츠 유형을 파라미터로 받아 필터링된 POI 목록 반환 (좌표·썸네일·예산 기본값 포함).
- **현재 구현**: `Tour` 엔티티에 전체 POI 통합 저장, `lDongSignguCd`로 지역 필터, `contenttypeid`로 유형 분류.
- **업서트 설계 원칙 (DA2 구현 시 적용)**: 월별 마이그레이션 실행 시 수동 입력된 `stars`·`likes` 값이 초기화되지 않도록 MySQL `ON DUPLICATE KEY UPDATE ... stars=COALESCE(stars, VALUES(stars))` 패턴 사용. TourAPI가 제공하지 않는 앱 내부 누적 데이터(`stars`·`likes`)는 마이그레이션 대상에서 제외. 상세 설계: [FEATURES_BACK.md DA1 업서트 설계](FEATURES_BACK.md).

### B-F2. 여행 코스 자동 생성 — 현재 및 목표 진화

**현재 구현 (v0.4.2 — 시군구 복수 선택)**
- `POST /api/v1/tour-course` — 인원·기간·이동수단·테마·시군구(복수, `sigunguCodes: []`) 입력을 받아 Groq LLM으로 일정별(Day N) 코스 자동 생성.
- DB에서 지역 POI를 유형별 할당량으로 샘플링 → AI 프롬프트 컨텍스트로 전달.
- **v0.2.6 샘플링 개선**: Hard exclusion(stars ≤ 1) → Tier A(stars ≥ 4, 70%) / Tier B(stars 2-3·null, 30%) 확률적 Tier 샘플링. likes 있으면 Tier 내 DESC 정렬, 없으면 shuffle. Cold-start(null) → Tier B 편입.
- **v0.3.1 Rate Limit 개선**: Groq API 429 응답 시 `retry-after` 헤더 기반 대기(기본 20초)로 재시도. 기타 에러와 명시적으로 분리.
- **v0.4.0 장소별 상세 필드 추가**: 응답 `PlaceInfo`에 `durationMinutes`(AI 추정·DB 저장)·`thumbnailImg`·`operatingHours`·`cost` 4개 필드 추가. CO4·SH2도 동일 필드 반환.
- AI 응답 검증(날짜 범위, contentId 실존, PlaceType 유효성) 후 `TourCourseUserDefined` + `TourCourseUserDefinedDetail` 저장.
- 비로그인(userId=null) 허용으로 생성 후 저장까지 동작.

**중기 목표 (v1.0+ — 순수 알고리즘 추천)**
- Tier 샘플링에서 나아가 Groq 완전 제거.
- `tour.stars`·`tour.likes` 스코어링 결과로 직접 Day별 일정 조합.
- `tour.lDongSignguCd` + `contenttypeid` + 스코어 기반 정렬 규칙 엔진으로 구현.

**최종 목표 (v1.0+ — 순수 알고리즘 추천)**
- Groq API 호출 없이 여행자가 입력한 조건(인원 버킷·테마·이동수단·기간·시군구)과 POI의 `stars`·`likes`를 결합한 스코어링 알고리즘으로 Day별 최적 코스 자동 생성.
- 사용자 `travel_type`(개인 선호 여행 타입)을 추가 가중치로 반영.
- 외부 LLM 의존 제거 → 응답 속도 향상·비용 절감·예측 가능성 확보.

### B-F3. 예산 메타데이터 제공

- 음식점: `food_avg_price.avg_price`를 `food.lclsSystm3 = food_avg_price.lclsSystm3` 소분류코드로 조인해 평균 식비 근사치 제공. (contentId 기준 아님)
- 숙박: `AccommodationDetailInfo`의 `roombasecount`·`roommaxcount`·비수기/성수기 요금으로 인원 버킷별 적합도 분류 및 1박 예상 비용 제공.
- 입장료·교통비: POI `DetailInfo` + 좌표 기반 이동거리 계산으로 추정값 제공.
- FE는 이 값을 기본값으로 표시하고 사용자가 인라인 수정 가능.
- ⚠️ **현재 스키마 갭**: `tour_course_user_defined_detail`에 POI별 예산 오버라이드 컬럼이 없어 사용자 수정값을 저장할 수 없음. `budget_override INT` 컬럼 추가 검토 필요. (BOQ9)

### B-F4. 코스 저장·조회·삭제

- 사용자 정의 코스: `TourCourseUserDefined` (헤더: 인원·기간·이동수단·테마) + `TourCourseUserDefinedDetail` (일정 상세: 날짜·순서·시간·contentId·타입).
- 비로그인 시 userId=null로 임시 저장 → 로그인 후 `PATCH /{courseId}/assign`으로 소유권 이전(`assignUser()`). (✅ 구현 완료)
- 코스 목록 조회: `GET /` — 사용자 ID로 전체 코스 목록 반환. (✅ 구현 완료)
- 코스 상세 조회: `GET /{courseId}` — 소유자 인증 후 헤더+일정 상세 반환. (✅ 구현 완료)
- 코스 삭제: `DELETE /{courseId}` — 소유자 인증 후 상세→헤더 순 삭제. (✅ 구현 완료)
- 코스 제목 수정: `PATCH /{courseId}/title` — 소유권 확인 후 `updateTitle()` 호출. (✅ 구현 완료)
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
| S1 메인/검색 | 시군구 목록, 테마 목록, 데이터 보유 시군구 플래그 | 미구현 (상수 또는 정적 응답) |
| S2 플래너 | POI 큐레이션 목록, 기본 추천 코스, POI 상세, 교통비 추정 | POI는 AI 생성 시 포함 / 큐레이션 전용 API 미구현 |
| S2 플래너 저장 | 코스 저장·수정·삭제 (로그인 사용자) | 생성은 구현 / 저장 소유권 분리 미구현 |
| S2a POI 상세 드로어 | contentId 기반 POI 상세 통합 조회 | 미구현 |
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

### TourAPI 데이터

| 엔티티 | 테이블 | 역할 |
| --- | --- | --- |
| `Tour` | `tour` | 전체 POI 통합 (contenttypeid로 유형 구분). `stars`·`likes` 컬럼으로 추천 알고리즘 기반 |
| `Attraction` | `attraction` | 관광지 소개정보 (contentTypeId=12) |
| `Culture` | `culture` | 문화시설 소개정보 (14) |
| `Event` | `event` | 축제/공연 소개정보 (15) |
| `Leports` | `leports` | 레포츠 소개정보 (28) |
| `Accommodation` | `accommodation` | 숙박 소개정보 (32) |
| `AccommodationDetailInfo` | `accommodation_detail_info` | 숙박 객실별 인원·요금 정보 |
| `Shopping` | `shopping` | 쇼핑 소개정보 (38) |
| `Food` | `food` | 음식점 소개정보 (39) |
| `FoodAvgPrice` | `food_avg_price` | 음식점 소분류(`lclsSystm3`) 기준 평균 객단가 |
| `DetailCommon` | `detail_common` | POI 공통 상세(설명·이미지·홈페이지) |
| `DetailInfo` | `detail_info` | POI 상세정보(요금·운영시간 등) |
| `TourCourse` | `tour_course` | TourAPI 기준 코스 정보 |
| `TourCourseDetailInfo` | `tour_course_detail_info` | TourAPI 코스 상세 |

### 사용자 정의 코스

| 엔티티 | 테이블 | 역할 |
| --- | --- | --- |
| `TourCourseUserDefined` | `tour_course_user_defined` | 사용자 생성 코스 헤더 (userId·인원·기간·이동수단·테마JSON·title). ⚠️ total_budget·share_token 컬럼 없음 |
| `TourCourseUserDefinedDetail` | `tour_course_user_defined_detail` | 코스 일정 상세 (날짜·순서·시간·`duration_minutes`·type·contentId). ⚠️ POI별 예산 오버라이드 컬럼 없음 |

> **스키마 설계 이력**: DDL v1에서 `title`, `total_budget`, `per_budget`, `course_data`(JSON), `share_token`, `is_public`이 있던 `tour_course_user_defined`가 2026-05-30 drop 후 재설계되어 현재 구조로 변경됨. 공유·예산 저장 기능 구현 전 BOQ9~BOQ11 결정 필요.

---

## 7. 현재 구현 상태 요약

### 구현 완료

- JWT 기반 로컬 인증/인가 (로그인·로그아웃·토큰 갱신·회원 CRUD)
- **카카오 OAuth 연동** (`POST /api/v1/auth/oauth/kakao/callback`) — FE 전달 카카오 AccessToken 검증, 신규 가입·기존 계정 연결·자체 JWT 발급 (`KakaoOAuthService` + `KakaoApiClient`)
- TourAPI 데이터 수집·DB 적재 (`DataMigrationController`)
- Groq AI 여행 코스 생성 (`POST /api/v1/tour-course`) — 비로그인 허용, userId=null
- `TourCourseUserDefined` + `TourCourseUserDefinedDetail` 저장 (`duration_minutes` 포함)
- **Tier 기반 확률적 POI 샘플링** — stars Hard exclusion + Tier A/B 분할 + likes 보조 정렬 (`TourCourseServiceImpl.selectByTypeQuota`)
- **장소별 상세 필드** — `durationMinutes`(AI 추정)·`thumbnailImg`·`operatingHours`·`cost` 4개 필드를 CO1·CO4·SH2 응답에 포함
- `tour.stars`·`tour.likes` 컬럼 추가, `user_poi_like` 중계 테이블로 likes 수집 파이프라인 구축
- **POI 좋아요 토글 API** (`POST /api/v1/poi/{contentId}/like`) — `user_poi_like` 중복 방지, 원자적 JPQL increment/decrement
- `tour_course_user_defined.title VARCHAR(255)` 컬럼 + 코스 제목 수정 API (`PATCH /{courseId}/title`)
- **코스 소유권 이전** (`PATCH /{courseId}/assign`) — userId=null 코스에 로그인 사용자 귀속
- **코스 목록 조회** (`GET /api/v1/tour-course`) — 로그인 사용자 전체 코스 목록
- **코스 상세 조회** (`GET /api/v1/tour-course/{courseId}`) — 소유자 인증 + 일정 상세 반환
- **코스 삭제** (`DELETE /api/v1/tour-course/{courseId}`) — 소유자 인증 + detail→course 순 삭제
- **공개 코스 뷰** (`GET /api/v1/tour-course/{courseId}/view`) — 인증 불필요, 카카오 공유 수신자용
- **공통 응답 포맷 표준화** — `ApiResponse<T>` 래퍼 도입, GlobalExceptionHandler·Security 핸들러 통일 (`INF1`)

### 미구현 (우선순위 순)

1. POI 큐레이션 전용 조회 API (인원버킷·테마·지역 필터)
2. POI 상세 통합 조회 API (contentId → 공통·소개·상세 통합)
3. 시군구 목록·데이터 보유 여부 응답 API
4. 교통비 추정 계산 로직 및 API
5. 예산 메타데이터(평균 객단가) API
6. TourAPI 데이터 월 1회 주기 수집 배치 스케줄링

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
| PATCH | `/{courseId}/assign` | 코스 소유권 이전 (인증 필요) | ✅ |

### POI (`/api/v1/poi`)

| 메서드 | 경로 | 설명 | 구현 |
| --- | --- | --- | --- |
| GET | `/` | 큐레이션 POI 목록 (지역·인원버킷·테마) | 🔜 |
| GET | `/{contentId}` | POI 상세 통합 조회 | 🔜 |
| POST | `/{contentId}/like` | POI 좋아요 토글 (인증 필요) | ✅ |

### 관리자 (`/api/admin`)

| 메서드 | 경로 | 설명 | 구현 |
| --- | --- | --- | --- |
| POST | `/migration/**` | TourAPI 데이터 수집·적재 | ✅ |

---

## 9. 가정 및 미결 (Open Questions)

> 프론트엔드 PRD의 OQ와 연계하여 백엔드 관점에서 추가로 필요한 결정 사항을 기술한다.

- **BOQ1. 인원 버킷 정의** — FE 기준(`1→1`, `2→2`, `≥3→'3-4'`)을 백엔드 쿼리 파라미터로 어떻게 매핑할지 확정 필요. 현재 `peopleCount` 그대로 수신.
- **BOQ2. 교통비 추정 알고리즘** — 직선거리 기반 유류비 단가, 대중교통 요금 테이블 정의 필요. 카카오 모빌리티 API 활용 여부 검토.
- **BOQ3. 평균 객단가 데이터 출처** — `FoodAvgPrice` 엔티티 존재하나 데이터 채우는 방법(TourAPI, 외식통계, 수동 입력) 확정 필요.
- **BOQ4. 비로그인 코스 소유권 이전 타이밍** — 로그인 모달 성공 직후 `PATCH /api/v1/tour-course/{courseId}/assign` 방식 vs. FE 세션토큰 전달 방식 결정 필요.
- **BOQ5. 공유 링크 만료 정책** — 스냅샷 TTL(무제한 vs. N일) 및 삭제 정책 확정 필요.
- **BOQ6. 카카오 OAuth 처리 방식** — ✅ **확정·구현 완료 (v0.2.7)**: FE에서 발급된 카카오 AccessToken을 `POST /api/v1/auth/oauth/kakao/callback`으로 전달 → `KakaoApiClient`로 카카오 사용자 정보 검증 → 자체 JWT 발급. 기존 로컬 계정과 이메일 일치 시 카카오 연결, 신규 사용자는 자동 가입.
- **BOQ7. 추천 코스 생성 주체** — 기본 추천 코스를 Groq AI가 생성하는지(현재 방식), `stars`·`likes` 기반 알고리즘으로 전환하는지, 또는 병행하는지 확정 필요. (FE PRD OQ9)
- **BOQ8. 데이터 커버리지 범위** — 경주·포항·영덕·안동 우선 처리 시 시군구 필터 플래그를 DB에서 관리할지 하드코딩할지 결정 필요.
- **BOQ9. POI별 예산 오버라이드 저장** — `tour_course_user_defined_detail`에 `budget_override INT NULL` 컬럼 추가 여부. FE의 인라인 가격 수정값을 영속화하려면 필요.
- **BOQ10. 코스 제목(title) 저장** — ✅ **확정·구현 완료**: `tour_course_user_defined.title VARCHAR(255) NULL` 컬럼 추가(DDL ALTER). 코스 제목 수정 API(`PATCH /{courseId}/title`) 구현 완료.
- **BOQ11. 공유 기능 스키마** — ✅ **확정 (v0.2.6)**: `share_snapshot` 테이블·`share_token` 컬럼 미추가. FE가 카카오 SDK로 courseId 기반 딥링크를 생성하고, 수신자는 `GET /{courseId}/view` 공개 API로 조회하는 방식으로 결정.
- **BOQ12. `stars`·`likes` 데이터 수집 방법** — 부분 확정: `likes`는 PO5 좋아요 토글 API(v0.2.6)로 앱 내 수집. `stars`는 AI 검색 기반 수동 입력 예정 (크롤링 방법 미확정).

---

## 참고

- 기능 분해도: [FEATURES_BACK.md](FEATURES_BACK.md)
- 프론트엔드 PRD: [PRD_FRONT.md](PRD_FRONT.md)
- 개발 가이드: [CLAUDE.md](CLAUDE.md)
- 기능 상세 문서: [func/FEAT_TOURCOURSE_GEN.md](func/FEAT_TOURCOURSE_GEN.md)

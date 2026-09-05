# 경북 CoCo — 통합 제품 기획서 (PRD)

> 출처: 『2026 관광데이터 활용 공모전』 웹·앱 개발 부문 제안서.
> 본 문서는 프론트엔드 PRD([PRD_FRONT.md](PRD_FRONT.md))와 백엔드 PRD([PRD_BACK.md](PRD_BACK.md))를 통합한 **전체 서비스 기준 PRD**다.
> FE·BE 양측이 공유하는 제품 비전·핵심 기능·화면-API 계약·미결 사항을 한 곳에 정리한다.
> 화면 인벤토리(§8)는 **인터랙티브 화면 설계 프로토타입**(`경북 CoCo 화면 설계 (standalone).html`)과 정합한다. 시각 설계 SoT는 해당 프로토타입 + [DESIGN.md](../DESIGN.md) 토큰이다.
> 버전 0.5.0 · 기준일 2026-08-08.

> **⚠️ v0.5.0 아키텍처 전환 (공모전 의무사항)**: 관광 정보를 로컬 DB에 적재해 사용하는 것이 규정 위반으로 확인되어, 백엔드는 TourAPI를 요청 시점에 라이브 호출(Caffeine 캐시 TTL 6h 경유)하는 구조로 전환했다. `tour`와 유형별 상세 테이블은 DB에서 제거되었고, 사용자 참여로 쌓이는 별점(`stars`)·좋아요(`likes`)만 신규 `poi_rating` 테이블로 분리 보존한다. 상세: [PRD_BACK.md](PRD_BACK.md).

---

## 1. 한 줄 정의

**경북 지역 소규모 여행자(1~4인)를 위한 추천·예산 스마트 플래너.**

인원수에 맞게 큐레이션된 숙소·식당·관광지를 추천하고, 사용자가 입력한 실제 비용을 기준으로 총예산과 1인당 부담액을 계산하며, 완성된 일정을 카카오톡으로 한 번에 공유한다.
코스는 **Groq AI**로 생성하며, 이 방식을 영구 핵심 방식으로 유지한다(2026-09-05 확정). 별점(`stars`)·추천수(`likes`)는 AI 프롬프트 후보 품질을 높이는 보조 신호로 계속 활용한다 — "AI 의존 제거"는 더 이상 로드맵이 아니다.

---

## 2. 배경 및 문제

| # | 문제 | 근거 |
| --- | --- | --- |
| P1 | **소규모 여행 니즈 미충족** | 1인 가구 증가·개인화로 혼행/2~3인 소그룹이 주류가 되었으나, 기존 플랫폼은 4인 이상·가족 단위 정보에 편중. |
| P2 | **예산 예측의 번거로움** | 고물가·가성비 중심 소비. 숙박·식비·교통·입장료를 일일이 조사·합산해 예산을 예측하기 어렵다. |
| P3 | **여행 계획 피로감** | 블로그·SNS·예약앱·지도앱을 오가며 정보를 교차 검증하고, 동행인과 코스 공유·더치페이를 논의하는 과정의 마찰. |

---

## 3. 타깃 사용자 및 핵심 시나리오

- **주 타깃**: 가성비·계획 효율을 중시하는 2030 세대 소규모 여행자(1~4인).

### 핵심 시나리오 (해피 패스)

```
1. [메인 /]
   목적지(경북 시군구)·일정·인원·테마 선택 → 검색 버튼 → 조건이 URL 쿼리로 /planner 전달

2. [플래너 /planner]
   - 좌측: 인원 버킷·테마 기반 POI 목록 (지도/리스트 토글)
   - 우측: AI 기본 추천 코스 패널 (Day별 일정)
   - POI 클릭 → 상세 드로어 → "내 코스(Day N)에 추가"

3. [예산 대시보드 — 실시간]
   스팟 추가/삭제 또는 금액 인라인 수정 시 총예산·1인당 부담액 갱신
   (식비·숙박비·입장료·교통비 모두 FE가 산정 — BE는 저장만 담당(BU4), 서버 측 평균가·추정 로직 없음)

4. [공유]
   완성 코스 → 카카오톡 또는 링크 공유 → /share/:id 읽기전용 뷰어
   (수신자 "나도 편집하기" → 코스 복제 → 플래너 유입)

5. [저장·컬렉션]
   게스트 저장 클릭 → 로그인 모달 → 로그인 성공 → 작업 이어하기 → /collection 저장
   저장 코스 재열기 → /planner?load=:id 재편집
```

### 지역 특화 테마 (경북)

- **[경주]** 나홀로 도보 역사 산책 & 황리단길 혼밥 투어
- **[포항/영덕]** 2~3인 해안도로 드라이브 & 오션뷰 가성비 숙소
- **[안동]** 4인 친구 모임 전통 한옥 고택 체험 & 찜닭 거리 투어
- **로컬 상권 연계**: 소규모 식당·카페 정보 제공으로 지역 경제 선순환 유도.

---

## 4. 핵심 기능

### F1. Who & How Many — 인원수 기반 스마트 큐레이션

- **입력**: 자유 카운터(`number`) → 추천 시 버킷 매핑 (`1→1`, `2→2`, `≥3→'3-4'`).
- **FE 역할**: 검색 조건 수집 → URL 쿼리로 플래너 전달.
- **BE 역할**: 시군구(`sigunguCode`)·콘텐츠 유형(`contentTypeId`) 파라미터로 필터링된 POI 목록 반환. ⚠️ **인원 버킷(`peopleCount`)·테마(`theme`) 필터, 숙박 인원별 분류 태그는 모두 스코프 아웃 확정**(BOQ14, 2026-08-08/08-22) — `peopleCount`는 필수 파라미터로만 받고 필터링에는 미사용, `theme`은 파라미터 자체를 받지 않음. 재검토 예정 없음.
- **연결**: S1 메인 → S2 플래너.

### F2. Smart Itinerary & Budget Planner — 코스 빌더 + 예산

- **FE 역할**: 플래너 워크스페이스(지도/리스트/코스 패널/예산 대시보드) 렌더링, POI 인라인 금액 수정.
- **BE 역할**:
  - Groq AI로 Day별 여행 코스 자동 생성. TourAPI 라이브 조회(Caffeine 캐시 경유) 결과의 POI를 `poi_rating.stars`·`likes` 기반 Tier 샘플링으로 유형별 할당량만큼 뽑아 LLM 프롬프트 컨텍스트로 제공. **2026-09-05 확정: 이 방식(Groq)을 영구 핵심 방식으로 유지** — `stars`·`likes` 기반 순수 알고리즘으로 완전 대체하는 로드맵은 취소됨 (`§12 코스 생성 진화 로드맵` 참고). Groq 완전 실패 시 대비한 Degraded Fallback 안전망은 설계만 해두고 미착수(BOQ18).
  - POI 상세(설명·운영시간·요금) 통합 응답 — TourAPI 라이브 조회(캐시 경유) 기반.
  - **음식점 평균 객단가·숙박 요금 산정 로직은 BE에 두지 않기로 확정** (BU1/BU2/BU3 취소, 2026-08-08/08-16) — 근거 테이블(`food_avg_price`/`accommodation_detail_info`)이 로컬 DB 미저장 원칙으로 소실된 뒤 재설계하지 않기로 결정. 대신 FE가 산정한 POI별 실제 비용을 그대로 받아 `tour_course_user_defined_detail.cost`에 저장만 담당 (BU4, 구현 완료).
  - 교통비 추정 로직도 BE에 두지 않음 (BU3 취소) — FE가 전담.
- **연결**: S2 플래너 워크스페이스, S2a POI 상세 드로어.

### F3. One-Click Share — 카카오톡 기반 공유

- **FE 역할**: 카카오 공유 SDK로 `courseId` 포함 딥링크 직접 생성·공유, 링크 복사 UI.
- **BE 역할**: 별도 공유 토큰 발급·스냅샷 저장 없음 (BOQ11 확정, 2026-08-16 이전) — `GET /api/v1/tour-course/{courseId}/view`로 인증 없이 코스 일정을 공개 조회만 제공.
- **연결**: S2 공유 액션 → S3 공유 뷰어 (`/share/:id`).
- 구현 완료: `tour_course_user_defined`에 `share_token`·`is_public` 컬럼을 추가하는 재설계는 하지 않기로 확정됨 — courseId 기반 공개 뷰로 대체.

---

## 5. 차별성

| 구분 | 기존 여행 서비스 | 경북 CoCo |
| --- | --- | --- |
| 추천 기준 | 평점·리뷰·광고 위주 | **인원수 및 1인당 예산 최적화** |
| 비용 계산 | 개별 상품 결제 가격만 | **숙식·교통 포함 전체 여정 예산 시뮬레이션** |
| 공유 방식 | 개별 링크 복사 후 전송 | **완성 코스 + 비용분담 내역 통합 공유** |
| 코스 생성 | 수동 검색·조합 | **Groq AI 기반 자동 일정 생성** (영구 핵심 방식, 별점·추천수는 AI 후보 선별 보조 신호) |

---

## 6. 데이터 파이프라인

> **v0.5.0부터 관광 정보 로컬 DB 적재 폐지** (공모전 의무사항). 매 요청 TourAPI를 라이브 호출하고 Caffeine 캐시(TTL 6h)로 응답 속도·호출한도를 보호한다.

```
[한국관광공사 TourAPI]
    ↓ 요청 시점 라이브 호출 (areaBasedList2 + detailCommon2 + detailIntro2 + detailInfo2)
    ↓ areaCode=35 (경상북도), contentTypeId: 12/14/15/28/32/38/39
[Caffeine 캐시 (TTL 6h, 인프로세스, 비영속)]
    지역 POI 후보 리스트 캐시 (시군구 조합 키)
    POI 개별 상세 캐시 (contentId 키 — 공통정보·소개정보·요금/운영시간)
           │
           └─ Groq AI 프롬프트 컨텍스트 (유형별 할당량 샘플링, poi_rating.stars/likes로 Tier 샘플링)
                     ↓ Groq LLM → Day별 일정 JSON → 캐시된 후보 리스트와 대조 검증 → DB 저장
                     (2026-09-05 확정: 이 경로가 영구 핵심 방식 — 알고리즘으로 완전 대체하는 로드맵은 취소됨)

[백엔드 DB (MariaDB) — 관광 정보 미저장, 아래만 보존]
    poi_rating (contentId PK, stars, likes — TourAPI가 제공 안 하는 앱 자체 데이터)
    tour_course_user_defined + tour_course_user_defined_detail (contentId는 단순 참조 컬럼)
    user_poi_like / mst_sigungu / mst_theme (기준정보)
    ↓ 코스 목록·상세 API
[FE — 플래너·컬렉션]
```

---

## 7. 시스템 구성

```
[FE — React/TypeScript]          [BE — Spring Boot 4.0 / Java 25]
  Vite + Zustand + Axios    ←──────  REST API (/api/v1/**)
  카카오맵 SDK                          JWT 인증 (Stateless)
  카카오 로그인 SDK                      MariaDB (JPA + MyBatis) — 유저/코스/평점만, 관광정보 미저장
  카카오 공유 SDK             ←────────  Caffeine 캐시 (TourAPI 응답, TTL 6h)
                                         Groq API Client (핵심, 영구 유지)
                                         TourAPI Client (v0.5.0부터 라이브 조회 전용, 배치 마이그레이션 제거)
```

---

## 8. 화면 인벤토리

> 화면 설계 프로토타입과 정합. 화면별 레이아웃 변형은 FE PRD([PRD_FRONT.md](PRD_FRONT.md) §0.2)에서 관리한다. S2a·S2b는 독립 라우트가 아닌 S2 내부/전역 오버레이다.

| ID | 화면 | 라우트 | 인증 | BE API 필요 | 레이아웃 변형 |
| --- | --- | --- | --- | --- | --- |
| S1 | 메인/검색 | `/` | 게스트 | ~~시군구 목록·플래그~~ (취소) | 히어로 3안(검색중심/이미지/미니멀) |
| S2 | 플래너 | `/planner` | 게스트(저장 시 로그인) | POI 큐레이션, 코스 생성, POI 상세, 코스 저장·수정 (교통비 추정은 BE 미제공 — FE 전담, BU3 취소) | 레이아웃 3안(3분할/지도중심/스택) · 예산 3안 · POI 카드 3안 |
| S2a | POI 상세 드로어 | (S2 오버레이) | 게스트 | POI 상세 통합 조회 | 드로어/바텀시트 |
| S2b | 로그인 모달 | (전역 오버레이) | — | 카카오 OAuth, 토큰 발급 | — |
| S3 | 공유 뷰어 | `/share/:id` | 공개 | 공개 코스 조회 (`GET /tour-course/{courseId}/view`, 별도 스냅샷 없음) | — |
| S4 | 컬렉션 | `/collection` | 로그인 | 코스 목록·상세·삭제 | — |
| S5 | 로그인 | `/auth/login` | 게스트 | 카카오 OAuth / 로컬 로그인 | — |
| S6 | 회원가입 | `/auth/register` | 게스트 | 회원가입 | — |
| S7 | 서비스 소개 | `/about` | 공개 | 없음 | — |
| S8 | 404 | `*` | 공개 | 없음 | — |

---

## 9. API 계약 요약 (FE ↔ BE 경계)

> 상세 스키마는 별도 API 명세서에서 확정한다.

### 인증

| 메서드 | 경로 | 요약 | 구현 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/login` | 이메일·비밀번호 → AccessToken + RefreshToken | ✅ |
| POST | `/api/v1/auth/logout` | RefreshToken 폐기 | ✅ |
| POST | `/api/v1/auth/reissue` | RefreshToken → 신규 AccessToken | ✅ |
| POST | `/api/v1/auth/oauth/kakao/callback` | 카카오 AccessToken → 자체 JWT 발급 | ✅ |

### 회원

| 메서드 | 경로 | 요약 | 구현 |
| --- | --- | --- | --- |
| POST | `/api/v1/user/join` | 회원가입 | ✅ |
| GET | `/api/v1/user/{userId}` | 회원 정보 조회 | ✅ |
| PATCH | `/api/v1/user/{userId}/nickname` | 닉네임 수정 | ✅ |
| PATCH | `/api/v1/user/{userId}/password` | 비밀번호 변경 | ✅ |
| DELETE | `/api/v1/user/{userId}` | 회원 탈퇴 (Soft Delete) | ✅ |

### POI

> `peopleGroup`(인원 버킷)·`theme[]` 필터, 시군구 목록·데이터 보유 플래그 API는 모두 스코프 아웃 확정(BOQ14, 2026-08-22) — 재검토 예정 없음.

| 메서드 | 경로 | 주요 파라미터 | 응답 핵심 필드 | 구현 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/poi` | `sigunguCode`(필수), `contentTypeId`(선택) | `contentId`, `title`, `mapx`, `mapy`, `firstimage`, `avgPrice`(항상 null, BU1 취소), `stars`, `liked` | ✅ |
| GET | `/api/v1/poi/{contentId}` | — | 공통+소개+상세 통합 응답, `stars`·`liked`·`totalLiked` 포함 | ✅ |
| POST | `/api/v1/poi/{contentId}/like` | — (인증 필요) | `{ liked, totalLiked }` | ✅ |

### 여행 코스

| 메서드 | 경로 | 요약 | 구현 |
| --- | --- | --- | --- |
| POST | `/api/v1/tour-course` | 코스 생성 (Groq AI, 영구 핵심 방식·비로그인 허용) | ✅ |
| GET | `/api/v1/tour-course` | 내 코스 목록 (인증 필요) | ✅ |
| GET | `/api/v1/tour-course/{courseId}` | 코스 상세 + 일정 (인증·소유자) | ✅ |
| PATCH | `/api/v1/tour-course/{courseId}` | 코스 일정 전량 교체 (인증·소유자) | ✅ |
| PATCH | `/api/v1/tour-course/{courseId}/title` | 코스 제목 수정 (인증·소유자) | ✅ |
| DELETE | `/api/v1/tour-course/{courseId}` | 코스 삭제 (인증·소유자) | ✅ |
| PATCH | `/api/v1/tour-course/{courseId}/assign` | 비로그인 코스 소유권 이전 (인증 필요) | ✅ |
| GET | `/api/v1/tour-course/{courseId}/view` | 공개 코스 뷰 (인증 불필요, 공유 수신자용) | ✅ |

### 공유

별도 공유 엔드포인트 없음 — SH1(링크 생성)은 FE가 카카오 SDK로 `courseId` 딥링크를 직접 생성하는 FE 전담 기능이고(BOQ11 확정), 수신자는 위 `GET /{courseId}/view`로 조회한다.

---

## 10. 인증 게이팅 정책

| 행동 | 인증 요구 | 처리 |
| --- | --- | --- |
| 메인 탐색·검색 | ❌ 게스트 허용 | — |
| AI 코스 생성 | ❌ 게스트 허용 | userId=null 임시 저장 |
| POI 목록·상세 조회 | ❌ 게스트 허용 | — |
| 코스 저장 (컬렉션 귀속) | ✅ 로그인 필요 | 게스트 → 로그인 모달 → 소유권 이전 |
| 공유 링크 생성 | ✅ 로그인 필요 | 게스트 → 로그인 모달 |
| 공유 링크 열람 | ❌ 공개 | — |
| 컬렉션 조회·삭제 | ✅ 로그인 필요 | 미인증 → 로그인 유도 |

---

## 11. 가정 및 미결 (통합 Open Questions)

> ✅ 확정됨 / 🔶 미결 (양 팀 합의 필요)

| ID | 내용 | 상태 |
| --- | --- | --- |
| OQ1 | FE repo는 자체 백엔드 API만 소비 (TourAPI 직접 호출 없음) | ✅ |
| OQ2 | 인원 입력 모델: 카운터 + 버킷 매핑(`1→1`, `2→2`, `≥3→'3-4'`), 1인당 분배는 실제 `n` | ✅ |
| OQ3 | 목적지: 경북 23개 시군구 전체 노출, 경주·포항·영덕·안동 우선 데이터. 나머지 "준비 중" | ✅ |
| OQ4 | ✅ **확정 (2026-08-16, 애초 방향에서 변경)**: 서버 측 평균 객단가·교통비 추정 로직은 두지 않기로 취소(BU1/BU3). FE가 산정한 실제 비용을 BE가 그대로 저장만 함(BU4, `tour_course_user_defined_detail.cost`) | ✅ |
| OQ5 | 공유 링크: 임시 URL 인코딩(A) → 서버 저장(B) 단계적 전환 | ✅ |
| OQ6 | 인증 게이팅: 탐색·플래닝 게스트 허용, 저장·공유 시점 로그인 모달 | ✅ |
| OQ7 | 비용 분담: 기본 균등 분배(`n`). 차등 분배는 Post-MVP | 🔶 |
| OQ8 | ✅ **확정·구현 완료**: 비로그인 코스는 userId=null로 저장, 로그인 후 `PATCH /{courseId}/assign`으로 소유권 이전(CO2) | ✅ |
| OQ9 | ✅ **확정 (2026-09-05)**: Groq AI 코스 생성을 영구 핵심 방식으로 유지한다. 순수 알고리즘 전환(구 CO6)은 v2+ 고도화가 아니라 **취소**로 최종 확정. `§12 로드맵` 참고 | ✅ |
| OQ10 | ✅ **확정·구현 완료**: FE에서 발급된 카카오 AccessToken을 BE로 전달 → `KakaoApiClient`로 검증 → 자체 JWT 발급 (AU4) | ✅ |
| OQ11 | ✅ **취소 (2026-08-16)**: 교통비 추정 로직은 BE에 두지 않음(BU3) — FE 전담 | ✅ |
| OQ12 | ✅ **해당 없음**: 별도 공유 스냅샷 자체가 없음(BOQ11) — courseId 기반 공개 뷰라 만료 정책 이슈가 발생하지 않음 | ✅ |
| OQ13 | ✅ **확정 (BOQ11)**: `share_token`·`is_public` 컬럼 재추가·별도 스냅샷 테이블 모두 불필요 — courseId로 직접 공개 뷰(`GET /{courseId}/view`) 조회 | ✅ |
| OQ14 | ✅ **확정·구현 완료**: `tour_course_user_defined.title VARCHAR(255)` 컬럼 추가, `PATCH /{courseId}/title`(CO7)로 수정 | ✅ |
| OQ15 | ✅ **확정·구현 완료 (BOQ9)**: `budget_override`가 아니라 단순 `tour_course_user_defined_detail.cost INT NULL` 컬럼으로 FE 입력값을 그대로 저장(BU4) | ✅ |
| OQ16 | 부분 확정: `likes`는 좋아요 API(PO5)로 앱 내 수집 완료. `stars`는 AI 검색 기반 285개 초기값만 수동 입력, 이후 수집 방법(크롤링 등)은 미확정 (PRD_BACK.md BOQ12) | 🔶 |
| OQ17 | **관광 정보 로컬 DB 미저장 전환 (v0.5.0)**: ✅ **확정·적용 완료**: 공모전 규정에 따라 TourAPI 라이브 호출 + Caffeine 캐시(TTL 6h) 구조로 전환. 하위 BOQ14(BU1/BU2 예산 메타데이터 재설계)도 ✅ **확정 (2026-08-16)**: 재설계하지 않고 BU1/BU2 기능 자체를 취소 | ✅ (하위 BOQ14도 ✅) |

---

## 12. 코스 생성 로드맵

> **2026-09-05 최종 확정**: Groq AI 기반 코스 생성을 영구 핵심 방식으로 유지한다. "언젠가 stars·likes 기반 순수 알고리즘으로 전환"이라는 로드맵(구 CO6 Phase 2/3)은 v2+ 고도화 대상이 아니라 **취소**됐다 — 2026-08-16엔 "v1만 Groq 유지, 이후 재검토"였지만, 순수 알고리즘 엔진을 새로 구축하는 비용 대비 Groq 기반 품질이 이미 충분하다고 판단해 재검토 자체를 접었다.

| 단계 | 내용 | 상태 |
| --- | --- | --- |
| **핵심 방식 (영구)** | POI 유형별 할당량 샘플링(stars Tier 샘플링 + likes 보조 정렬 포함, v0.2.6) → Groq LLM 프롬프트 → Day별 코스 반환. 샘플링 소스는 v0.5.0부터 TourAPI 라이브 조회(Caffeine 캐시 TTL 6h) | ✅ 유지 중 |
| ~~stars·likes 가중치 알고리즘으로 Groq 보조~~ | ~~라이브 조회 결과에서 `poi_rating.stars`·`likes` 상위 우선 선택, LLM은 일정 조합만 담당~~ | ❌ 취소 |
| ~~순수 알고리즘 추천 (Groq 완전 제거)~~ | ~~인원버킷·테마·이동수단·`stars`·`likes`·`travel_type`으로 스코어 계산 → 규칙 엔진으로 Day별 코스 직접 조합~~ | ❌ 취소 |
| **AI 실패 시 안전망 (고도화 TODO)** | Groq 재시도(3회) 소진 후 완전 실패 시, 순수 알고리즘 대신 이미 있는 Tier 샘플링 후보 + 지리적 클러스터(`g` 필드)로 규칙 기반 최소 코스를 반환하는 Degraded Fallback. 설계만 완료(BOQ18, [PRD_BACK.md](PRD_BACK.md#9-가정-및-미결-open-questions)), 착수 조건 없음 — 공모전 규모에서 발생 빈도 낮아 실제 장애 관측 전까지 보류 | 🔶 설계만 완료 |

---

## 13. 발전 방향

| 단계 | 내용 |
| --- | --- |
| **단기** | UGC 데이터 축적 — 사용자 '가성비 실전 코스'·'실제 지출 비용' 리뷰 → `stars`·`likes` 데이터 자연 축적 → Groq 프롬프트 후보 선별(Tier 샘플링) 품질 향상 |
| **중기** | 소상공인 제휴 할인 — 추천 상권 식당·카페 쿠폰 발급 + 수수료 기반 BM |
| **장기** | B2B/B2G 확장 — 경북 실증 모델 전국화, 소규모 여행객 동선·소비 데이터 비식별 제공 |

---

## 14. 구현 현황 요약

### 백엔드 구현 완료

- JWT 기반 로컬 인증/인가 (로그인·로그아웃·토큰 갱신·회원 CRUD·비밀번호 변경·탈퇴)
- 카카오 OAuth 연동 (`POST /oauth/kakao/callback`) — 신규 가입·기존 계정 연결·자체 JWT 발급
- TourAPI 라이브 연동 + Caffeine 캐시(v0.5.0) — 콘텐츠타입별 분리 수집·캐시 워밍(v0.5.8)까지 포함
- Groq AI 여행 코스 생성(CO1) — Tier 샘플링, rate limit 재시도, reasoning 모델 대응, AI 에러 전용 499 응답, 지리적 클러스터링+이동거리 사후 검증(v0.6.8). **2026-09-05 확정: 영구 핵심 방식으로 유지, 순수 알고리즘 대체(CO6)는 취소**
- POI 큐레이션 목록(PO2)·상세 통합 조회(PO3)·좋아요 토글(PO5) — `stars`·`liked`·`totalLiked` 응답 포함
- 코스 CRUD 전체: 생성/목록조회(CO3)/상세조회(CO4)/일정 수정(CO8)/제목 수정(CO7)/삭제(CO5)/소유권 이전(CO2)/공개 뷰(SH2)
- POI별 비용 저장(BU4) — FE 입력값을 `tour_course_user_defined_detail.cost`에 저장
- 무중단 배포(Blue/Green, INF7) — 레포 측 구현 및 실서버 적용 완료
- 공통 응답 포맷(INF1), CORS(INF4), CI/CD(INF5)

### 백엔드 남은 작업 (고도화 TODO, 당장 착수 안 함)

1. **BOQ18** — AI 코스 생성 완전 실패 시 Degraded Fallback 안전망 (설계만 완료, 2026-09-05 신규)
2. **BOQ17** — PO5 좋아요 토글 동시 요청 경합 처리 (더블탭·재시도 시 500 가능, 심각도 낮음)
3. **BOQ15/INF6** — DB·TourAPI 연동 통합 테스트 인프라 (Testcontainers·WireMock 도입 필요)
4. **BOQ12** — `stars` 데이터 추가 수집 방법 미확정 (`likes`는 수집 파이프라인 완료)
5. 숙소 인원수 기반 필터링 — v2로 유예 (TourAPI `detailInfo2` 연동 필요)

### 취소된 항목 (재검토 예정 없음)

- PO4(시군구 목록·플래그), BU1(음식점 평균 객단가), BU2(숙박 인원별 분류), BU3(교통비 추정), CO6(순수 알고리즘 코스 추천)
- `GET /api/v1/poi`의 `peopleCount`·`theme` 필터(BOQ14), DA1~DA3(TourAPI 배치 수집·스케줄링·FoodAvgPrice 적재)

### 프론트엔드 구현 필요 (MVP 우선순위)

1. API 클라이언트 기반 (`src/api/`)
2. `searchStore`·`courseStore`·`budgetStore`·`authStore` (Zustand)
3. 카카오 OAuth 세션 스토어 연동
4. 플래너 워크스페이스 (지도·코스 패널·예산 대시보드)
5. POI 큐레이션 목록 + 지도 마커
6. 코스 저장·컬렉션 화면
7. 공유 뷰어 (`/share/:id`)

---

## 참고

- 백엔드 PRD: [PRD_BACK.md](PRD_BACK.md)
- 프론트엔드 PRD: [PRD_FRONT.md](PRD_FRONT.md)
- 백엔드 기능 분해도: [FEATURES_BACK.md](FEATURES_BACK.md)
- 프론트엔드 기능 분해도: [FEATURES_FRONT.md](FEATURES_FRONT.md)
- 디자인 시스템(시각 SoT): [DESIGN.md](../DESIGN.md)
- 시각 설계 원본: `경북 CoCo 화면 설계 (standalone).html` (인터랙티브 프로토타입)
- 백엔드 개발 가이드: [CLAUDE.md](CLAUDE.md)

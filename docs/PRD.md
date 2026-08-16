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

인원수에 맞게 큐레이션된 숙소·식당·관광지를 추천하고, 코스를 구성하는 즉시 총예산과 1인당 부담액을 실시간 산출하며, 완성된 일정과 비용 분담 내역을 카카오톡으로 한 번에 공유한다.
현재는 Groq AI로 코스를 생성하며, 최종 목표는 **별점(`stars`)·추천수(`likes`) 기반 알고리즘 추천**으로 진화하는 것이다.

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
   (식비·숙박비·입장료 = 백엔드 메타데이터 기본값, 교통비 = 백엔드 추정값)

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
- **BE 역할**: 인원 버킷·테마·시군구 파라미터로 필터링된 POI 목록 반환. 숙박 인원별 분류 태그 포함.
- **연결**: S1 메인 → S2 플래너.

### F2. Smart Itinerary & Budget Planner — 코스 빌더 + 실시간 예산

- **FE 역할**: 플래너 워크스페이스(지도/리스트/코스 패널/예산 대시보드) 렌더링, POI 인라인 금액 수정.
- **BE 역할**:
  - **현재**: Groq AI로 Day별 여행 코스 자동 생성. TourAPI 라이브 조회(Caffeine 캐시 경유) 결과의 POI를 유형별 할당량으로 샘플링해 LLM 프롬프트 컨텍스트로 제공 (v0.5.0부터 로컬 DB 스캔 아님).
  - **목표**: `poi_rating.stars`·`poi_rating.likes` 기반 스코어링 알고리즘으로 LLM 없이 코스 생성. (`§12 코스 생성 진화 로드맵` 참고)
  - POI 상세(설명·운영시간·요금) 통합 응답 — v0.5.0부터 TourAPI 라이브 조회(캐시 경유) 기반.
  - 음식점 평균 객단가·숙박 요금 기본값 제공. ⚠️ 근거였던 `food_avg_price`/`accommodation_detail_info` 테이블이 v0.5.0에서 제거되어 재설계 필요 (OQ17 하위 BOQ14, [PRD_BACK.md](PRD_BACK.md)).
  - 코스 좌표 열에서 이동수단(`TransportType`)별 교통비 추정 (좌표는 TourAPI 라이브 응답 기준).
- **연결**: S2 플래너 워크스페이스, S2a POI 상세 드로어.

### F3. One-Click Share — 카카오톡 기반 비용 분담 공유

- **FE 역할**: 카카오 공유 SDK 호출, 링크 복사 UI.
- **BE 역할**: 코스·예산 스냅샷 직렬화·저장 → 공유 토큰 발급. 공유 토큰 기반 읽기전용 조회.
- **연결**: S2 공유 액션 → S3 공유 뷰어 (`/share/:id`).
- ⚠️ **스키마 갭**: `tour_course_user_defined`에서 `share_token`·`is_public` 컬럼이 제거됨. 공유 구현 전 스키마 재설계 필요 (OQ13).

---

## 5. 차별성

| 구분 | 기존 여행 서비스 | 경북 CoCo |
| --- | --- | --- |
| 추천 기준 | 평점·리뷰·광고 위주 | **인원수 및 1인당 예산 최적화** |
| 비용 계산 | 개별 상품 결제 가격만 | **숙식·교통 포함 전체 여정 예산 시뮬레이션** |
| 공유 방식 | 개별 링크 복사 후 전송 | **완성 코스 + 비용분담 내역 통합 공유** |
| 코스 생성 (현재) | 수동 검색·조합 | Groq AI 기반 자동 일정 생성 |
| 코스 생성 (목표) | 수동 검색·조합 | **별점·추천수 스코어링 알고리즘 기반 자동 추천** |

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
           ├─ [현재] Groq AI 프롬프트 컨텍스트 (유형별 할당량 샘플링, poi_rating.stars/likes로 Tier 샘플링)
           │         ↓ Groq LLM → Day별 일정 JSON → 캐시된 후보 리스트와 대조 검증 → DB 저장
           │
           └─ [목표] poi_rating.stars·likes 스코어링 알고리즘 → Day별 일정 직접 생성 → DB 저장

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
                                         Groq API Client (현재, 점진적 제거 목표)
                                         TourAPI Client (v0.5.0부터 라이브 조회 전용, 배치 마이그레이션 제거)
```

---

## 8. 화면 인벤토리

> 화면 설계 프로토타입과 정합. 화면별 레이아웃 변형은 FE PRD([PRD_FRONT.md](PRD_FRONT.md) §0.2)에서 관리한다. S2a·S2b는 독립 라우트가 아닌 S2 내부/전역 오버레이다.

| ID | 화면 | 라우트 | 인증 | BE API 필요 | 레이아웃 변형 |
| --- | --- | --- | --- | --- | --- |
| S1 | 메인/검색 | `/` | 게스트 | ~~시군구 목록·플래그~~ (취소) | 히어로 3안(검색중심/이미지/미니멀) |
| S2 | 플래너 | `/planner` | 게스트(저장 시 로그인) | POI 큐레이션, 코스 생성, POI 상세, 교통비 추정, 코스 저장 | 레이아웃 3안(3분할/지도중심/스택) · 예산 3안 · POI 카드 3안 |
| S2a | POI 상세 드로어 | (S2 오버레이) | 게스트 | POI 상세 통합 조회 | 드로어/바텀시트 |
| S2b | 로그인 모달 | (전역 오버레이) | — | 카카오 OAuth, 토큰 발급 | — |
| S3 | 공유 뷰어 | `/share/:id` | 공개 | 공유 스냅샷 조회 | — |
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
| POST | `/api/v1/auth/oauth/kakao/callback` | 카카오 AccessToken → 자체 JWT 발급 | 🔜 |

### 회원

| 메서드 | 경로 | 요약 | 구현 |
| --- | --- | --- | --- |
| POST | `/api/v1/user/join` | 회원가입 | ✅ |
| GET | `/api/v1/user/{userId}` | 회원 정보 조회 | ✅ |
| PATCH | `/api/v1/user/{userId}/nickname` | 닉네임 수정 | ✅ |
| DELETE | `/api/v1/user/{userId}` | 회원 탈퇴 (Soft Delete) | ✅ |

### POI

| 메서드 | 경로 | 주요 파라미터 | 응답 핵심 필드 | 구현 |
| --- | --- | --- | --- | --- |
| GET | ~~`/api/v1/poi/regions`~~ (취소) | — | — | — |
| GET | `/api/v1/poi` | `sigunguCode`, `peopleGroup`, `theme[]`, `contentTypeId` | `contentId`, `title`, `mapx`, `mapy`, `firstimage`, `avgPrice`, `stars`, `likes` | 🔜 |
| GET | `/api/v1/poi/{contentId}` | — | 공통+소개+상세 통합 응답 | 🔜 |

### 여행 코스

| 메서드 | 경로 | 요약 | 구현 |
| --- | --- | --- | --- |
| POST | `/api/v1/tour-course` | 코스 생성 (현재: Groq AI / 목표: 알고리즘) | ✅ |
| GET | `/api/v1/tour-course` | 내 코스 목록 (인증 필요) | 🔜 |
| GET | `/api/v1/tour-course/{courseId}` | 코스 상세 + 일정 | 🔜 |
| DELETE | `/api/v1/tour-course/{courseId}` | 코스 삭제 | 🔜 |
| PATCH | `/api/v1/tour-course/{courseId}/assign` | 비로그인 코스 소유권 이전 | 🔜 |
| POST | `/api/v1/tour-course/{courseId}/share` | 공유 토큰 생성 (⚠️ 스키마 재설계 필요) | 🔜 |

### 공유

| 메서드 | 경로 | 요약 | 구현 |
| --- | --- | --- | --- |
| GET | `/api/v1/share/{shareId}` | 공유 코스·예산 스냅샷 공개 조회 | 🔜 |

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
| OQ4 | 예산 기본값 = 백엔드 근사치(음식점은 lclsSystm3 조인 기준), FE에서 인라인 수정 가능. 교통비는 BE 추정값 표시 | ✅ |
| OQ5 | 공유 링크: 임시 URL 인코딩(A) → 서버 저장(B) 단계적 전환 | ✅ |
| OQ6 | 인증 게이팅: 탐색·플래닝 게스트 허용, 저장·공유 시점 로그인 모달 | ✅ |
| OQ7 | 비용 분담: 기본 균등 분배(`n`). 차등 분배는 Post-MVP | 🔶 |
| OQ8 | 비로그인 코스 임시 보관: 1단계 로컬스토리지, 로그인 후 `PATCH /assign` 소유권 이전 | 🔶 |
| OQ9 | 기본 추천 코스: 현재 Groq AI 생성. `§12 로드맵` 참고 | 🔶 |
| OQ10 | 카카오 OAuth BE 처리 방식: FE에서 발급된 토큰 전달 → BE 검증 방식으로 우선 결정 | 🔶 |
| OQ11 | 교통비 추정 알고리즘: 직선거리 기반 유류비·대중교통 단가. 카카오 모빌리티 API 활용 여부 검토 필요 | 🔶 |
| OQ12 | 공유 스냅샷 만료 정책: TTL 무제한 vs. N일 결정 필요 | 🔶 |
| OQ13 | **공유 스키마**: DDL v2에서 `share_token`·`is_public` 제거됨. 별도 `share_snapshot` 테이블 vs. 컬럼 재추가 결정 필요 | 🔶 |
| OQ14 | **코스 제목 저장**: `tour_course_user_defined`에 `title` 컬럼 없음. 컬렉션 표시를 위해 추가 여부 결정 필요 | 🔶 |
| OQ15 | **POI별 예산 오버라이드 저장**: `tour_course_user_defined_detail`에 `budget_override` 컬럼 없음. FE 인라인 수정값 영속화 여부 결정 필요 | 🔶 |
| OQ16 | **`stars`·`likes` 데이터 수집 방법**: 앱 내 사용자 참여(별점/추천 UI) vs. 외부 소스 매핑 vs. 초기 수동 입력 결정 필요. v0.5.0부터 저장 위치는 `poi_rating` 테이블 | 🔶 |
| OQ17 | **관광 정보 로컬 DB 미저장 전환 (v0.5.0)**: ✅ **확정·적용 완료**: 공모전 규정에 따라 TourAPI 라이브 호출 + Caffeine 캐시(TTL 6h) 구조로 전환. `food_avg_price`·`accommodation_detail_info` 소실에 따른 BU1/BU2 예산 메타데이터 재설계는 미확정 (PRD_BACK.md BOQ14) | ✅ (하위 BOQ14는 🔶) |

---

## 12. 코스 생성 진화 로드맵

코스 생성 기능은 3단계로 진화한다.

| 단계 | 버전 | 방식 | 핵심 변경 |
| --- | --- | --- | --- |
| **Phase 1 — 현재** | v0.2 | Groq AI 기반 | DB POI 무작위 샘플링 → LLM 프롬프트 → Day별 코스 반환 |
| **Phase 1.5 — 현재** | v0.5.0 | Groq AI 기반, 데이터 소스 전환 | POI 샘플링 소스가 로컬 DB → TourAPI 라이브 조회(Caffeine 캐시 TTL 6h)로 변경. 알고리즘 로직 자체는 동일 |
| **Phase 2 — 중기** | v0.3+ | stars·likes 가중치 + Groq 보조 | 라이브 조회 결과에서 `poi_rating.stars`·`likes` 상위 우선 선택. LLM은 여전히 일정 조합 담당 |
| **Phase 3 — 최종** | v1.0 | 순수 알고리즘 추천 | Groq 완전 제거. 인원버킷·테마·이동수단·`poi_rating.stars`·`likes`·사용자 `travel_type`으로 스코어 계산 → 유형별 할당 규칙 엔진으로 Day별 코스 직접 조합 |

**Phase 3 입력값 → 스코어 계산 예시:**

```
입력: sigunguCode, peopleGroup, theme[], transport, startDate~endDate
      user.travel_type (개인 선호 여행 타입)

POI 스코어 = stars × 가중치_s + (likes / 최대좋아요) × 가중치_l
             + 테마_매칭 × 가중치_t + 인원버킷_적합도 × 가중치_p

Day별 배치: 유형별 할당량(숙박1·식사2·관광N·문화M) 규칙 적용
            → 상위 스코어 POI를 운영시간·이동거리 제약 안에서 배치
```

**Phase 3 선결 조건:**
1. OQ16: `stars`·`likes` 데이터 수집 완료 (DA4)
2. 가중치 파라미터 튜닝 (초기값은 임의 설정 후 사용자 반응으로 개선)
3. 할당 규칙 엔진 설계 (식사 횟수/Day, 숙박 위치 제약 등)

---

## 13. 발전 방향

| 단계 | 내용 |
| --- | --- |
| **단기** | UGC 데이터 축적 — 사용자 '가성비 실전 코스'·'실제 지출 비용' 리뷰 → `stars`·`likes` 데이터 자연 축적 → 알고리즘 추천 품질 향상 |
| **중기** | 소상공인 제휴 할인 — 추천 상권 식당·카페 쿠폰 발급 + 수수료 기반 BM |
| **장기** | B2B/B2G 확장 — 경북 실증 모델 전국화, 소규모 여행객 동선·소비 데이터 비식별 제공 |

---

## 14. 구현 현황 요약

### 백엔드 구현 완료

- JWT 기반 로컬 인증/인가 (로그인·로그아웃·토큰 갱신·회원 CRUD)
- TourAPI 라이브 연동 (`TourApiClient`) — v0.5.0부터 DB 적재 배치는 폐지, 요청 시점 라이브 호출 전용
- Groq AI 여행 코스 생성 (`POST /api/v1/tour-course`) — 비로그인 허용
- `TourCourseUserDefined` + `TourCourseUserDefinedDetail` 저장
- `poi_rating.stars`·`poi_rating.likes` 컬럼 스키마 준비 (v0.5.0부터 `tour` 테이블에서 분리, 285개 stars 초기값 이관 완료)
- **DB 마이그레이션 완료 (2026-08-08)**: `tour`/`attraction`/`culture`/`event`/`tour_course`/`leports`/`accommodation`/`shopping`/`food`/`detail_common`/`detail_info`/`accommodation_detail_info`/`tour_course_detail_info`/`food_avg_price` 14개 테이블 백업 후 DROP, `poi_rating` 신설

### 백엔드 구현 필요 (MVP 우선순위)

1. CORS 설정 (INF4)
2. 카카오 OAuth 콜백 (AU4)
3. ~~시군구 목록·데이터 보유 플래그 API (PO4)~~ (취소)
4. POI 큐레이션 목록 API — `stars`·`likes` 응답 포함 (PO2)
5. POI 상세 통합 조회 API (PO3)
6. 코스 소유권 이전 (CO2)
7. 코스 목록·상세·삭제 API (CO3/CO4/CO5)
8. 교통비 추정 로직 (BU3)
9. 공유 스냅샷 생성·조회 API — 스키마 결정 후 (SH1/SH2)

### 스키마 결정 필요 (구현 전 선결)

- OQ13: 공유 스키마 (share_token 재추가 vs. 별도 테이블)
- OQ14: `tour_course_user_defined.title` 컬럼 추가 여부
- OQ15: `tour_course_user_defined_detail.budget_override` 컬럼 추가 여부

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

# 경북 CoCo — 기능 분해도 (백엔드 기준)

> 목적: 백엔드가 구현해야 할 전체 기능을 도메인 영역별로 분해한 상위 문서.
> "무엇이 필요한가"를 API·서비스·인프라 단위로 나열하며, 상세 동작 규칙·스키마·검증 조건은 다음 단계인 **API 명세서**로 위임한다.
> 출처: [PRD_BACK.md](PRD_BACK.md) · [PRD_FRONT.md](PRD_FRONT.md) · 버전 0.5.0 · 기준일 2026-08-08.

> **⚠️ v0.5.0 아키텍처 전환**: 공모전 규정상 관광 정보를 로컬 DB에 저장할 수 없어, TourAPI 라이브 호출(Caffeine 캐시 TTL 6h 경유) 구조로 전환했다. `tour`·`attraction`·`culture`·`event`·`tour_course`·`leports`·`accommodation`·`shopping`·`food`·`detail_common`·`detail_info`·`accommodation_detail_info`·`tour_course_detail_info`·`food_avg_price` 테이블·엔티티는 전부 삭제됨. 아래 기능 블록 중 이 테이블들에 의존하던 것들은 데이터 소스가 "로컬 DB 조회" → "TourAPI 라이브 호출(캐시)"로 변경되었고, 근거 테이블이 완전히 사라진 BU1·BU2는 별도 재설계가 필요해 신규 미결 항목(OQ17·OQ18)으로 플래그했다.

---

## 읽는 법

각 기능은 다음 6개 필드로 기술한다.

- **설명** — 기능이 하는 일 1~2줄.
- **상태** — 성공 / 실패 / 엣지케이스.
- **MVP** — ✅ 1차 출시 포함 / 🔜 Post-MVP.
- **구현 상태** — ✅ 완료 / 🔧 부분 구현 / ❌ 미구현.
- **FE 의존** — 이 기능에 의존하는 프론트엔드 화면(S*).
- **가치** — 왜 필요한가.

기능 ID 규약: 인증 `AU#`, 회원 `US#`, POI `PO#`, 코스 `CO#`, 예산 `BU#`, 공유 `SH#`, 데이터 `DA#`.

> ⚠️ **스키마 갭 표기**: DDL v2 기준으로 현재 스키마에 누락된 컬럼·테이블이 일부 있음. 해당 기능 블록에 갭 표시(⚠️)를 붙이고 BOQ 번호로 추적한다.

---

## 0. 공통 인프라 (Cross-cutting)

### INF1. REST API 공통 응답 구조
- **설명**: 성공/실패 시 일관된 JSON 응답 구조(`code`, `msg`, `data`) 및 글로벌 예외 핸들러. 모든 엔드포인트가 `ApiResponse<T>` 래퍼를 통해 세 필드를 반드시 반환.
- **상태**: `GlobalExceptionHandler`로 `@Valid` 실패·런타임 예외 모두 처리. Security 레이어(401/403)도 동일 포맷 반환.
- **MVP**: ✅
- **구현 상태**: ✅ (`dto/ApiResponse.java` 제네릭 래퍼 도입, 전체 Controller·ExceptionHandler·Security 핸들러 통일 완료)
- **FE 의존**: 전체 화면.
- **가치**: FE 에러 핸들링 일관성.
- **응답 포맷**:
  ```json
  { "code": "200", "msg": "로그인에 성공했습니다.", "data": { "accessToken": "..." } }
  { "code": "400", "msg": "이메일 형식이 아닙니다, 8자 이상이어야 합니다", "data": null }
  { "code": "401", "msg": "인증이 필요합니다.", "data": null }
  ```
- **validation 오류 처리**: 필드명 노출 없이 `getDefaultMessage()` 값만 `", "` 로 join해 `msg`에 반환. `data`는 항상 `null`.
- **내부 정보 노출 차단**: `ResponseStatusException` reason null 시 `ex.getMessage()` 대신 고정 문구 반환. `NoSuchElementException` 메시지 없는 경우(예: `Optional.get()`) null 가드로 고정 문구 반환.

### INF2. Spring Security + JWT 필터 체인
- **설명**: `JwtAuthenticationFilter`가 모든 요청의 Bearer 토큰을 검증하고 SecurityContext에 사용자 정보 주입.
- **상태**: 토큰 만료·위변조·미존재 각각 401 반환.
- **MVP**: ✅
- **구현 상태**: ✅
- **FE 의존**: 모든 인증 필요 화면.
- **가치**: Stateless 보안의 전제.

### INF3. 환경변수 기반 설정 관리
- **설명**: DB·JWT·TourAPI·Groq API 키를 `.env`로 분리, `application.yaml`에서 참조.
- **상태**: 누락 시 애플리케이션 기동 실패.
- **MVP**: ✅
- **구현 상태**: ✅
- **FE 의존**: 없음.
- **가치**: 보안·환경별 설정 분리.

### INF4. CORS 설정
- **설명**: FE(프론트엔드 도메인)에서 오는 요청 허용, 프리플라이트 처리. `allowCredentials=true`로 HttpOnly 쿠키 전송 허용. 허용 Origin은 환경변수 `cors.allowed-origins`로 관리.
- **상태**: 미허용 Origin → 403.
- **MVP**: ✅
- **구현 상태**: ✅ (`SecurityConfig.corsConfigurationSource()` 구현 완료 — GET/POST/PUT/PATCH/DELETE/OPTIONS 허용, `allowCredentials=true`)
- **FE 의존**: 전체 화면.
- **가치**: FE-BE 통신의 전제.

### INF5. CI/CD 배포 파이프라인 (GitHub Actions + systemd)
- **설명**: 오라클 클라우드 Free Tier 백엔드 인스턴스(Ubuntu)에 대한 자동 배포. `main` push 시 Gradle 빌드 → SCP로 jar 전송 → 서버 `.env` 재생성 → `systemctl restart`.
- **상태**: DB 서버와 동일 VCN 내 프라이빗 IP로 통신 (공인 IP 미노출). 앱 런타임 시크릿은 GitHub Secrets를 원본으로 삼아 배포마다 서버 `.env`(`chmod 600`)를 덮어씀.
- **MVP**: ✅
- **구현 상태**: ✅ (`.github/workflows/deploy.yml`, `deploy/cocobackend.service`)
- **FE 의존**: 없음.
- **가치**: 수동 배포·서버 SSH 접속 없이 push만으로 반영, 시크릿 교체 시에도 서버 접속 불필요.

---

## 1. 인증 (Authentication)

### AU1. 로컬 로그인
- **설명**: 이메일·비밀번호 검증 후 AccessToken(15분) + RefreshToken(7일) 발급. RefreshToken DB 저장.
- **상태**: 자격증명 불일치 → 401 / 삭제된 계정 → 401 / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: ✅ (`POST /api/v1/auth/login`)
- **FE 의존**: S5/S6 인증, S2b 로그인 모달.
- **가치**: 사용자 식별·개인화 기반.

### AU2. 로그아웃
- **설명**: DB에서 해당 사용자·Provider의 RefreshToken 삭제.
- **상태**: 토큰 없음 → 그냥 성공 처리 / 성공 → 204.
- **MVP**: ✅
- **구현 상태**: ✅ (`POST /api/v1/auth/logout`)
- **FE 의존**: S4 컬렉션, 헤더 로그아웃.
- **가치**: 세션 폐기·보안.

### AU3. AccessToken 재발급 (RefreshToken 로테이션)
- **설명**: 만료된 AccessToken을 RefreshToken으로 갱신. 기존 RefreshToken 삭제 후 신규 발급.
- **상태**: RefreshToken 만료·불일치 → 401 / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: ✅ (`POST /api/v1/auth/reissue`)
- **FE 의존**: 모든 인증 화면.
- **가치**: 장기 세션 유지·보안 강화.

### AU4. 카카오 OAuth 콜백 처리
- **설명**: FE에서 전달된 카카오 AccessToken 검증 → 신규 사용자 자동 가입 또는 기존 사용자 세션 발급. 기존 로컬 계정과 이메일 일치 시 카카오 계정을 연결(`linkKakao()`). RefreshToken 로테이션 적용.
- **상태**: 카카오 토큰 검증 실패 → 401 / 성공 → 자체 JWT 발급.
- **MVP**: ✅
- **구현 상태**: ✅ (`POST /api/v1/auth/oauth/kakao/callback`, `KakaoOAuthService` + `KakaoApiClient` 구현 완료)
- **FE 의존**: S5 로그인, S2b 로그인 모달.
- **가치**: 카카오 소셜 로그인 — FE PRD의 핵심 인증 수단.

---

## 2. 회원 (User)

### US1. 회원가입
- **설명**: 이메일·비밀번호·닉네임 입력으로 회원 생성. 이메일 중복 체크 (Soft Delete 제외).
- **상태**: 이메일 중복 → 409 / 입력 오류 → 400 / 성공 → 201.
- **MVP**: ✅
- **구현 상태**: ✅ (`POST /api/v1/user/join`)
- **FE 의존**: S6 회원가입.
- **가치**: 로컬 계정 생성.

### US2. 회원 정보 조회
- **설명**: 로그인 사용자 본인 정보(닉네임·이메일·가입일) 반환.
- **상태**: 미인증 → 401 / 타인 조회 → 403 / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: ✅ (`GET /api/v1/user/{userId}`)
- **FE 의존**: S4 컬렉션 프로필.
- **가치**: 사용자 정보 표시.

### US3. 닉네임 수정
- **설명**: 로그인 사용자 닉네임 변경.
- **MVP**: ✅
- **구현 상태**: ✅ (`PATCH /api/v1/user/{userId}/nickname`)
- **FE 의존**: S4 컬렉션.
- **가치**: 프로필 관리.

### US4. 비밀번호 변경
- **설명**: 현재 비밀번호 검증 후 새 비밀번호로 교체. BCrypt 인코딩 저장.
- **MVP**: ✅
- **구현 상태**: ✅ (`PATCH /api/v1/user/{userId}/password`)
- **FE 의존**: S4 컬렉션.
- **가치**: 계정 보안.

### US5. 회원 탈퇴 (Soft Delete)
- **설명**: `deletedAt` 필드 설정으로 논리 삭제. 재가입 시 복구 가능.
- **MVP**: ✅
- **구현 상태**: ✅ (`DELETE /api/v1/user/{userId}`)
- **FE 의존**: S4 컬렉션.
- **가치**: 데이터 보존·재가입 허용.

---

## 3. POI 데이터 (Points of Interest)

### PO1. TourAPI 라이브 조회 (v0.5.0 — 배치 적재 폐지)
- **설명**: ~~한국관광공사 TourAPI `areaBasedList` → 경북(areaCode=35) 전체 시군구·콘텐츠 유형별 수집 → `tour` 테이블 적재~~. **v0.5.0부터 로컬 적재 자체가 공모전 규정 위반**이라 폐지. 대신 `TourApiClient`로 `areaBasedList2`/`detailCommon2`/`detailIntro2`/`detailInfo2`를 요청 시점 라이브 호출하고, Caffeine 캐시(지역 후보 리스트·POI 상세 각각 TTL 6h)를 경유해 응답 속도·호출한도를 보호한다.
- **상태**: TourAPI 오류 → 해당 요청 실패 응답(재시도 없음, 배치가 아니므로) / 성공 → 캐시에 저장 후 반환.
- **MVP**: ✅
- **구현 상태**: 🔧 (`TourApiClient`는 기존 존재·재사용, 캐시 계층·요청 시점 라이브 조회 서비스는 신규 구현 필요)
- **FE 의존**: 없음 (인프라).
- **가치**: 모든 POI·예산·코스 기능의 데이터 원천 (배치 대신 요청 시점 실시간 원천으로 전환).

### PO2. 큐레이션 POI 목록 조회
- **설명**: 지역(sigunguCode)·인원 버킷(1/2/3-4)·테마·콘텐츠 유형 파라미터로 필터링된 POI 목록 반환. 응답에 `mapx`/`mapy` 좌표, 썸네일, 예상 객단가 포함. **v0.5.0**: 데이터 소스가 TourAPI 라이브 호출(PO1, 캐시 경유)로 변경 — 필터링·정렬은 애플리케이션 메모리에서 처리.
- **상태**: TourAPI가 데이터 없는 시군구 응답 → 빈 배열 + `available: false` 플래그 / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: 🔧 (`GET /api/v1/poi` 구현됨 — `sigunguCode`(필수)·`contentTypeId`(선택) 필터만 동작. `peopleCount`는 필수 파라미터로만 받고 필터링에는 미사용(BU2 스코프 아웃), `theme`은 파라미터 자체 미수신, `avgPrice`는 항상 `null` 반환. 근거: [PRD_BACK.md BOQ14](PRD_BACK.md))
- **FE 의존**: S2 플래너 좌측 결과 영역 (P2).
- **가치**: F1 인원수 기반 큐레이션의 핵심 응답.

### PO3. POI 상세 통합 조회
- **설명**: `contentId` 기반으로 공통 상세(설명·이미지)·유형별 반복정보(요금·시설 등)를 통합해 단일 응답으로 반환. **v0.5.0**: `DetailCommon`/`DetailInfo`/`Attraction`/`Food`/`Accommodation` 등 로컬 엔티티 조인 대신, `TourApiClient.detailCommon2`/`detailInfo2` 라이브 호출을 조합해 응답 구성 (`TourLiveDataService.getFullDetail()`, 신규 `poiFullDetail` 캐시 TTL 6h 경유).
- **상태**: TourAPI에 존재하지 않는 contentId → 404 / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: ✅ (`GET /api/v1/poi/{contentId}`, 인증 불필요) — `avgPrice`는 BOQ14 미확정으로 항상 `null` 반환 (PO2와 동일)
- **FE 의존**: S2a POI 상세 드로어.
- **가치**: 사용자가 코스 추가 전 상세 정보를 확인하는 핵심 API.

### PO5. POI 좋아요 토글
- **설명**: 로그인 사용자가 특정 POI에 좋아요를 추가하거나 취소. `user_poi_like` 중계 테이블로 중복 방지. `poi_rating.likes`(v0.5.0부터 `tour.likes`에서 이전)를 원자적 JPQL UPDATE로 증감.
- **상태 (v0.5.0 변경)**: 미인증 → 401 / 좋아요 추가 → `{liked: true, likes: N}` / 취소 → `{liked: false, likes: N}` / 성공 → 200. **"존재하지 않는 POI → 404" 검증 제거**: `Tour` 로컬 테이블이 없어져 존재 여부를 확인할 근거가 없음. `poi_rating` 행이 없으면 좋아요 액션 시 on-demand 생성(`likes=1`, `stars=null`)하고, TourAPI 라이브 재검증은 하지 않음(프론트가 이미 API로 확인된 contentId만 전달한다고 신뢰).
- **MVP**: ✅
- **구현 상태**: ✅ (`POST /api/v1/poi/{contentId}/like`, 인증 필수) — `TourRepository` 의존 제거하고 `PoiRatingRepository` 기반으로 재구현 필요
- **FE 의존**: S2 플래너 POI 카드 좋아요 버튼.
- **가치**: likes 데이터 축적 → CO6 추천 품질 향상의 원천 데이터.

### PO4. 시군구 목록 및 데이터 보유 플래그 조회
- **설명**: 경북 23개 시군구 목록과 각 시군구의 데이터 보유 여부(`available`)를 반환. 경주·포항·영덕·안동이 우선 제공. 시군구 코드·이름 목록은 `mst_sigungu` 기준정보 테이블에서 조회 (v0.5.0부터 `tour.lDongSignguCd` 실데이터 기반이 아닌 기준정보 기반).
- **상태**: 항상 200.
- **MVP**: ✅
- **구현 상태**: ❌ (`GET /api/v1/poi/regions` 미구현)
- **FE 의존**: S1 메인/검색 목적지 셀렉트 (I1).
- **가치**: FE가 "준비 중" 시군구를 비활성 표시하기 위한 플래그.

---

## 4. AI 여행 코스 생성 (Course Generation)

### CO1. AI 코스 생성 (Groq API — 현재)
- **설명**: 인원·기간·이동수단·테마·시군구(`sigunguCodes`, 복수 선택)를 입력받아 POI를 유형별 할당량으로 샘플링 → Groq LLM 프롬프트 구성 → Day별 일정 생성 → 검증 후 `TourCourseUserDefined` + `TourCourseUserDefinedDetail` 저장. **v0.5.0**: POI 후보는 로컬 `tour` 테이블 스캔이 아니라 TourAPI 라이브 조회(PO1, Caffeine 캐시 TTL 6h) 결과에서 샘플링. AI 응답 contentId 검증(하단 참조)도 요청 내에서 이미 확보한 후보 리스트와 메모리 대조로 변경 — 추가 DB/API 호출 없음.
  - **샘플링 알고리즘 (v0.2.6 개선)**: Hard exclusion(stars ≤ 1 제거) → Tier A(stars ≥ 4, 70% 슬롯) / Tier B(stars 2-3 또는 null, 30% 슬롯) 확률적 샘플링. Tier A 부족분은 Tier B로 보충. Cold-start(null stars) → Tier B 편입. likes 데이터 있으면 각 Tier 내 likes DESC 정렬, 없으면 shuffle. (v0.5.0: stars/likes 조회원이 `tour` → `poi_rating`으로 변경, 알고리즘 로직 자체는 동일)
  - **Rate Limit 재시도 (v0.3.1 개선)**: `GroqApiClient`에서 HTTP 429 응답을 `HttpClientErrorException`으로 명시 감지. `retry-after` 헤더 값(초→ms 변환) 우선 대기, 헤더 없으면 20초 기본 대기 후 재시도. 기타 에러(네트워크·5xx 등)는 기존대로 1초 대기. 최대 재시도(3회) 소진 시 rate limit 전용 에러 메시지 반환.
  - **장소별 상세 필드 (v0.4.0 추가, v0.5.0 소스 변경)**: AI가 장소별 `durationMinutes`(예상 소요시간)를 추정해 응답에 포함(`TourCourseUserDefinedDetail`에 저장, 변경 없음). 응답에 `thumbnailImg`·`operatingHours`·`cost`도 포함하되, v0.5.0부터 로컬 DB(`tour.firstimage`, type별 detail 테이블) 대신 TourAPI 라이브 조회(PO1 캐시 재사용) 파싱 결과 사용 — `cost`는 캐시된 라이브 응답 우선 → type 기본값 fallback.
- **상태**: TourAPI가 해당 지역 데이터 없음 → 400 / AI 응답 비정상 → 500 재시도 / Rate Limit 초과(3회) → 500 "잠시 후 다시 시도" / 검증 실패(contentId 불일치·날짜 초과) → 400 / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: ✅ (`POST /api/v1/tour-course`) — 비로그인 허용, userId=null 저장.
- **FE 의존**: S2 플래너 (기본 추천 코스 P4, AI 코스 생성 연계).
- **가치**: 핵심 차별점 — 인원·테마 맞춤 자동 일정 생성.
- **⚠️ 스키마 갭**: POI별 예산 오버라이드 저장 불가(BOQ9).

### CO7. 코스 제목 수정
- **설명**: 로그인 사용자가 본인 코스의 제목(`title`)만 단독으로 수정. 소유권 확인 후 `TourCourseUserDefined.updateTitle()` 호출.
- **상태**: 미인증 → 401 / 타인 코스 → 403 / 코스 없음 → 404 / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: ✅ (`PATCH /api/v1/tour-course/{courseId}/title`, 인증 필수)
- **FE 의존**: S4 컬렉션 — 코스 이름 인라인 편집.
- **가치**: 저장 코스 식별성 — 기간·인원만으로는 코스 구분이 어렵기 때문에 제목 부여·수정이 필요.

### CO6. 별점·추천수 기반 알고리즘 코스 추천 (목표)
- **설명**: `poi_rating.stars`·`poi_rating.likes`(v0.5.0부터 `tour.stars`/`tour.likes`에서 이전) 기반 POI 스코어링 알고리즘으로 여행자 조건(인원 버킷·테마·이동수단·기간·시군구)에 최적화된 Day별 코스를 Groq 없이 생성. 사용자 `travel_type` 선호도를 추가 가중치로 반영.
  - **단계 1 (v0.2.6 부분 구현)**: stars 기반 Tier 샘플링 + likes 정렬 보조 신호를 CO1 샘플링에 적용 완료 (Groq 여전히 사용).
  - **단계 2 (v1.0)**: Groq 완전 제거. 스코어링 결과로 직접 Day별 일정 조합. 유형별 할당량(식사·숙박·관광·문화 등) 규칙 엔진으로 구현.
- **상태**: stars 데이터 없는 POI → Tier B 편입(Cold-start) / likes 0 → shuffle / 성공 → CO1과 동일 응답.
- **MVP**: 🔜
- **구현 상태**: 🔧 (Tier 샘플링·likes 보조 정렬 CO1에 적용 완료 / Groq 제거·순수 알고리즘은 미구현)
- **FE 의존**: S2 플래너 (CO1과 동일 API, 내부 구현만 교체).
- **가치**: LLM 의존 제거로 응답속도·비용·예측 가능성 개선. 사용자 반응 데이터가 쌓일수록 추천 품질 자동 향상.

### CO2. 코스 소유권 이전 (비로그인 → 로그인)
- **설명**: 비로그인으로 생성된 코스(userId=null)에 로그인 후 사용자 ID를 귀속시킴 (`assignUser()`).
- **상태**: 이미 다른 사용자 소유 → 403 / 코스 없음 → 404 / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: ✅ (`PATCH /api/v1/tour-course/{courseId}/assign`, 인증 필수)
- **FE 의존**: S2b 로그인 모달 성공 후 저장 이어하기.
- **가치**: FE PRD OQ8 — 비로그인 코스 임시 보관 후 소유권 이전.

### CO3. 사용자 코스 목록 조회
- **설명**: 로그인 사용자의 저장 코스 목록 반환 (코스 ID·제목·기간·인원·이동수단·테마 요약·생성일).
- **상태**: 미인증 → 401 / 코스 없음 → 빈 배열 / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: ✅ (`GET /api/v1/tour-course`, 인증 필수)
- **FE 의존**: S4 컬렉션 (CO1).
- **가치**: 컬렉션 화면의 핵심 데이터.

### CO4. 코스 상세 조회
- **설명**: 코스 헤더 + 일정 상세(날짜·순서·시간·contentId·장소명·durationMinutes·thumbnailImg·operatingHours·cost) 통합 반환. 소유자 인증 필요. **v0.5.0**: 장소명·썸네일·운영시간·비용은 로컬 `Tour`/detail 테이블 재조회 대신 TourAPI 라이브 조회(PO1, POI 상세 캐시 TTL 6h) 결과로 조립.
- **상태**: 미인증 → 401 / 본인 코스 아님(또는 userId=null 코스) → 403 / 코스 없음 → 404 / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: ✅ (`GET /api/v1/tour-course/{courseId}`, 인증 필수, `TourCourseShareResponseDto` 반환)
- **FE 의존**: S4 컬렉션 상세 (CO2), 플래너 재로드 (`?load=:id`).
- **가치**: 컬렉션 상세 보기·재편집의 전제.

### CO5. 코스 삭제
- **설명**: 로그인 사용자 본인 코스 삭제. 상세(detail) 먼저 삭제 후 헤더(course) 삭제 (FK 순서 보장).
- **상태**: 미인증 → 401 / 타인 코스 → 403 / 코스 없음 → 404 / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: ✅ (`DELETE /api/v1/tour-course/{courseId}`, 인증 필수)
- **FE 의존**: S4 컬렉션 (CO3).
- **가치**: 컬렉션 정리.

---

## 5. 예산 (Budget)

### BU1. 음식점 평균 객단가 제공 ⚠️
- **설명**: ~~`food.lclsSystm3`(소분류코드)로 `food_avg_price.lclsSystm3`을 조인해 음식점 소분류별 평균 식비 근사치를 POI 응답에 포함~~. **v0.5.0 재설계 필요**: 근거였던 `food`·`food_avg_price` 테이블이 로컬 DB 미저장 원칙에 따라 제거됨. TourAPI 라이브 응답에는 소분류별 평균 객단가가 없어 대체 데이터 소스(외식통계 API 등) 또는 기능 축소 여부 결정 필요. (BOQ14, [PRD_BACK.md](PRD_BACK.md))
- **상태**: (재설계 전까지 보류)
- **MVP**: ✅ → 🔶 재검토
- **구현 상태**: ❌ (근거 테이블 소실로 기존 🔧 상태에서 후퇴)
- **FE 의존**: S2 플래너 예산 대시보드 (P9), POI 상세 드로어 (S2a).
- **가치**: P2 — 예산 시뮬레이션의 식비 기본값.

### BU2. 숙박 인원별 적합 숙소 분류 ❌ 스코프 아웃 (2026-08-08)
- **설명**: ~~`AccommodationDetailInfo`의 객실 수용 인원·요금 정보를 분석해 인원 버킷(1/2/3-4) 적합도 태그 및 1박 기준 예상 비용 제공~~. 근거였던 `accommodation_detail_info` 테이블이 로컬 DB 미저장 원칙에 따라 제거됨. **2026-08-08 기획 결정: 재설계 없이 기능 자체를 스코프 아웃** — `GET /api/v1/poi`의 `peopleCount`는 필수 입력값으로만 받고 숙박(contentTypeId=32) 필터링에는 사용하지 않음.
- **상태**: 스코프 아웃 확정 (재검토 예정 없음)
- **MVP**: ✅ → ❌ 제외
- **구현 상태**: ❌ (의도적 미구현)
- **FE 의존**: 없음 (peopleCount는 파라미터로만 수신, 필터링 미적용).
- **가치**: P1/P2 — 인원 맞춤 숙소 + 숙박비 예산 기본값.

### BU3. 교통비 추정 계산 및 제공
- **설명**: 코스 내 POI 순서대로 좌표(mapx/mapy) 기반 이동거리 계산 → 이동수단(`TransportType`: CAR/PUBLIC_TRANSPORT/WALK)별 교통비 추정값 산출 → 응답에 포함. `TourCourseUserDefined.transport` 컬럼 값을 기준으로 계산. **v0.5.0**: 좌표 출처가 로컬 `tour.mapx`/`tour.mapy` → TourAPI 라이브 조회(PO1 캐시) 응답의 `mapx`/`mapy`로 변경.
- **상태**: 좌표 누락 POI → 해당 구간 0 처리 / 성공 → 추정값 반환.
- **MVP**: ✅
- **구현 상태**: ❌ (계산 로직 및 API 미구현)
- **FE 의존**: S2 플래너 예산 대시보드 교통비 항목 (P11).
- **가치**: P2 — 숙식+교통 포함 전체 여정 예산.

### BU4. POI별 예산 오버라이드 저장 ⚠️
- **설명**: FE에서 사용자가 수정한 POI별 금액을 `tour_course_user_defined_detail`에 영속화. 현재 스키마에 `budget_override` 컬럼이 없어 구현 불가.
- **상태**: (스키마 변경 후) 오버라이드 없음 → null / 있음 → override 값 우선 반환.
- **MVP**: ✅
- **구현 상태**: ❌ (`tour_course_user_defined_detail`에 `budget_override INT NULL` 컬럼 추가 필요. BOQ9)
- **FE 의존**: S2 플래너 인라인 가격 수정 (P10).
- **가치**: P2 — 사용자 수정값이 저장되지 않으면 재방문 시 초기화됨.

---

## 6. 공유 (Share)

### SH1. 공유 링크 생성 (BOQ11 확정 — FE 전담)
- **설명**: FE가 카카오 SDK로 `courseId`를 포함한 딥링크를 직접 생성·공유. 백엔드에서 별도 `share_token` 발급·스냅샷 저장 불필요.
- **상태**: BE 작업 없음.
- **MVP**: ✅
- **구현 상태**: ✅ (FE 전담 — BE 구현 범위 외. BOQ11 확정: `share_snapshot` 테이블·`share_token` 컬럼 추가하지 않음)
- **FE 의존**: S2 플래너 공유 액션 (P13), S4 컬렉션 공유.
- **가치**: F3 One-Click Share — 서버 저장 없이 courseId 기반 공개 뷰 URL로 공유.

### SH2. 공개 코스 뷰 (공유 수신자용)
- **설명**: `courseId`로 코스 일정을 공개 조회. 인증 불필요(게스트 접근). FE는 SH1에서 생성한 링크로 이 API를 호출. 읽기 전용 — 수정·삭제 불가. CO4와 동일한 `TourCourseShareResponseDto` 반환 (durationMinutes·thumbnailImg·operatingHours·cost 포함). **v0.5.0 주의**: 인증 없는 공개 엔드포인트라 반복 호출(봇 포함) 시 TourAPI 호출량이 가장 크게 튈 수 있는 지점 — POI 상세 캐시(TTL 6h) 재사용이 CO4와 공유되어 실제 라이브 호출은 캐시 미스 시에만 발생.
- **상태**: 코스 없음 → 404 / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: ✅ (`GET /api/v1/tour-course/{courseId}/view`, 인증 불필요, `TourCourseShareResponseDto` 반환)
- **FE 의존**: S3 공유 뷰어.
- **가치**: 수신자가 일정을 확인하는 읽기전용 뷰. 예산 스냅샷은 공모전 이후 고려.

---

## 7. 데이터 수집·관리 (Data)

### DA1. TourAPI 전체 수집 트리거 (관리자) — ❌ v0.5.0 폐지
- **설명**: ~~관리자가 `/api/admin/migration/**`으로 전체 또는 유형별 수집 실행. 수집 단계: areaBasedList → detailCommon → detailIntro → detailInfo~~. **로컬 DB 적재 자체가 공모전 규정 위반이라 `DataMigrationController`/`DataMigrationService` 전체 삭제.** `TourApiClient`(호출 메서드 자체)는 남아 PO1 라이브 조회의 기반으로 재사용.
- **구현 상태**: ❌ (제거됨)
- **FE 의존**: 없음.

### DA2. TourAPI 주기 수집 배치 스케줄링 — ❌ v0.5.0 대상 소멸
- **설명**: ~~월 1회 자동으로 DA1 수집 실행~~. **DA1 자체가 폐지되어 더 이상 필요 없음.** "최신성 유지"라는 목적은 v0.5.0에서 Caffeine 캐시 TTL 6h로 대체됨 (자동 만료·재조회).
- **구현 상태**: ❌ (해당 없음)
- **FE 의존**: 없음.

### DA3. FoodAvgPrice 데이터 적재 — ❌ v0.5.0 폐지
- **설명**: ~~음식점 소분류(`lclsSystm3`)별 평균 객단가를 외식통계 또는 수동 입력으로 `food_avg_price` 테이블에 적재~~. **`food_avg_price` 테이블이 로컬 DB 미저장 원칙에 따라 제거됨.** BU1 재설계(BOQ14) 결과에 따라 대체 방식 결정 필요.
- **구현 상태**: ❌ (제거됨)
- **FE 의존**: 없음.

### DA4. `poi_rating.stars`·`poi_rating.likes` 데이터 수집
- **설명**: `poi_rating` 테이블(v0.5.0부터 `tour.stars`/`tour.likes`에서 분리)의 `stars`·`likes` 컬럼에 실제 데이터를 채움.
  - `likes`: PO5 좋아요 토글 API로 앱 내 수집 중 (`user_poi_like` + 원자적 JPQL UPDATE, on-demand 행 생성). ✅ 파이프라인 구축 완료.
  - `stars`: 네이버·다이닝코드·구글 평점 웹 검색 기반으로 285개 POI 일괄 입력 완료 (2026-06-27, 당시 `tour.stars`). v0.5.0 DB 마이그레이션 시 `poi_rating`으로 이관 완료(2026-08-08). 여행코스(contenttypeid=25) 15개는 별점 대상 제외(null 유지).
- **MVP**: 🔜 (CO6 알고리즘 추천 구현 전 선결 조건)
- **구현 상태**: ✅ (`likes` 수집 파이프라인 완료 / `stars` 285개 초기값 `poi_rating`으로 이관 완료)
- **FE 의존**: PO5 좋아요 버튼 (likes 수집 연결됨).
- **가치**: CO6 별점·추천수 기반 알고리즘 추천의 핵심 원천 데이터.

### DA5. TourAPI 응답 캐싱 (Caffeine) — **신규 (v0.5.0)**
- **설명**: 로컬 DB 미저장 원칙에 따라 매 요청 TourAPI 라이브 호출이 되면서 발생하는 응답 지연·호출한도 소진 리스크를 완화하기 위한 인프로세스 캐시 계층. 캐시 2종, 둘 다 TTL 6시간:
  - 지역 POI 후보 리스트 캐시 (코스 생성용 `areaBasedList2` 결과, 시군구 조합 키)
  - POI 개별 상세 캐시 (`detailCommon2`/`detailIntro2`/`detailInfo2` 결과, `contentId` 키) — 코스 생성(CO1)·코스 조회(CO4)·공개 뷰(SH2)·POI 상세 조회(PO3)에서 공통 재사용
- **상태**: 캐시 히트 → TourAPI 호출 없이 즉시 반환 / 캐시 미스·만료 → 라이브 호출 후 캐시 적재.
- **MVP**: ✅ (v0.5.0 전환의 필수 구성요소)
- **구현 상태**: ❌ (신규 구현 필요 — `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine` 의존성 추가 필요, 현재 프로젝트에 캐시 라이브러리 없음)
- **FE 의존**: 없음 (인프라, 응답 속도에 간접 영향).
- **가치**: 1GB 메모리 프리티어 서버에서 별도 인프라(Redis 등) 없이 TourAPI 호출량·응답 지연을 실질적으로 절감.

---

## 부록: 구현 우선순위 요약

FE MVP 기준으로 백엔드 미구현 항목 우선순위를 나열한다.

**MVP 구현 우선순위**

| 우선순위 | 기능 ID | 기능명 | 이유 |
| --- | --- | --- | --- |
| 1 | INF4 | CORS 설정 | FE-BE 통신 전제, 모든 API 사용 전 필요 |
| 2 | DA5 | Caffeine 캐시 계층 | v0.5.0 라이브 호출 구조 전환의 선결 조건 (PO1~PO3, CO1, CO4, SH2 전부 의존) |
| 3 | PO4 | 시군구 목록·플래그 | S1 메인 화면 목적지 셀렉트 |
| 4 | PO2 | 큐레이션 POI 목록 | S2 플래너 핵심 데이터 |
| ~~5~~ | ~~PO3~~ | ~~POI 상세 통합 조회~~ | ~~S2a 상세 드로어~~ — ✅ 구현 완료 |
| 6 | BU3 | 교통비 추정 | S2 예산 대시보드 |

> ❌ v0.5.0에서 DA1~DA3(TourAPI 배치 수집·스케줄링·FoodAvgPrice 적재) 폐지 — 로컬 DB 미저장 원칙에 따라 대상 자체가 사라짐.

> ✅ v0.2.6에서 완료: CO2(소유권 이전), CO3/CO4/CO5(코스 목록·상세·삭제), SH2(공개 뷰), PO5(좋아요 토글)
> ✅ v0.2.7에서 완료: AU4(카카오 OAuth 콜백, `KakaoOAuthService` + `KakaoApiClient` 구현)

**스키마 결정 선결 과제 (구현 전 BOQ 확정 필요)**

| BOQ | 내용 | 영향 기능 | 상태 |
| --- | --- | --- | --- |
| BOQ9 | `tour_course_user_defined_detail.budget_override` 컬럼 추가 여부 | BU4 예산 오버라이드 저장 | 미확정 |
| BOQ11 | 공유 스키마: `share_token` 컬럼 재추가 vs. 별도 `share_snapshot` 테이블 | SH1/SH2 공유 기능 | ✅ 확정: 별도 스냅샷 없이 courseId 직접 공개 뷰로 처리 |
| BOQ12 | `stars` 데이터 수집 방법 확정 (`likes`는 PO5로 수집 중) | DA4, CO6 알고리즘 추천 | 미확정 (AI 검색 수동 입력 예정) |
| BOQ13 | Caffeine 캐시 TTL | DA5, PO1~PO3, CO1, CO4, SH2 | ✅ 확정 (v0.5.0): 지역 후보 리스트·POI 상세 모두 TTL 6h |
| BOQ14 | BU1 근거 테이블(`food_avg_price`) 소실에 따른 예산 메타데이터 재설계. BU2는 2026-08-08 스코프 아웃 확정(재설계 대상 아님) | BU1, DA3 | 🔶 BU1만 미확정 |

**Post-MVP 로드맵**

| 단계 | 기능 ID | 기능명 |
| --- | --- | --- |
| v0.3 | CO6-1 | stars·likes 가중치 기반 POI 샘플링 (Groq 보조) |
| v0.3 | DA4 | tour.stars·likes 데이터 수집 파이프라인 |
| v0.5 | DA5 | TourAPI 응답 Caffeine 캐싱 (로컬 DB 미저장 전환) |
| v1.0 | CO6-2 | 순수 알고리즘 기반 코스 추천 (Groq 완전 제거) |
| ~~v1.0~~ | ~~DA2~~ | ~~TourAPI 배치 스케줄링~~ — v0.5.0에서 폐지 |

---

## 다음 단계

이 분해도를 입력으로 **API 명세서**를 작성한다. 각 기능 블록의 API 의존 항목을 받아 엔드포인트·요청/응답 스키마·HTTP 상태코드·인증 여부·검증 조건으로 전개한다.
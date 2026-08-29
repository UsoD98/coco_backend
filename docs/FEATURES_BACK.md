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

### INF6. DB·외부 API 연동 통합 테스트 인프라 (TODO — 당장 착수 안 함)
- **설명**: 현재 테스트는 Mockito 단위 테스트(서비스 계층, Repository/외부 클라이언트 Mock)와 `@WebMvcTest` 슬라이스 테스트(컨트롤러 계층, 서비스 Mock)까지만 구성됨. 실제 MariaDB에 쓰기까지 검증하는 테스트(`PoiRating`/`UserPoiLike` insert·update 등)와 실제 TourAPI 응답 계약을 검증하는 테스트는 없음.
- **상태**: (미착수) — 도입 시 (1) Testcontainers 기반 MariaDB 통합 테스트 환경, (2) `TourApiClient`가 `RestClient.create()`를 필드에서 직접 생성해 현재는 가로챌 수 없으므로 `RestClient.Builder` 주입 리팩터링 후 `MockRestServiceServer`/WireMock 스텁 도입이 선행되어야 함.
- **MVP**: 🔜
- **구현 상태**: ❌ (TODO, PRD_BACK.md BOQ15 참고)
- **FE 의존**: 없음 (인프라).
- **가치**: 서비스 로직 단위 검증을 넘어 실제 DB 트랜잭션·외부 API 응답 계약 변경까지 자동 검증해 회귀를 조기 발견.

### INF7. 무중단 배포 전환 (TODO — 개발 완료 후 추가 개발, 2026-08-22)
- **설명**: 현재 배포(INF5)는 `systemctl restart cocobackend` 단일 호출이라 재시작 중 다운타임이 발생. Docker/K8s 없이 지금의 systemd 직접 배포 방식을 유지하면서 무중단 배포로 전환하는 것이 목표.
- **전환 방향**: systemd + nginx(리버스 프록시) 조합의 블루/그린 배포.
  - `cocobackend-blue`(예: 8080)·`cocobackend-green`(예: 8081) 두 개의 systemd 유닛으로 동일 jar를 다른 포트에서 운영.
  - nginx가 활성 포트로만 트래픽을 전달 (현재 서버에 nginx가 없다면 먼저 설치·구성 필요).
  - 배포 스크립트: 비활성 인스턴스에 새 jar 배포 → 재시작 → 헬스체크 통과 확인 → nginx upstream을 새 인스턴스로 전환(`nginx -s reload`, 무중단) → 이전 인스턴스 종료.
  - `.github/workflows/deploy.yml`의 빌드·scp 단계는 그대로 재사용하고, 마지막 `Restart service` 스텝만 블루/그린 전환 로직으로 교체.
- **참고**: Caffeine 캐시(DA5)가 인스턴스 로컬이라 블루/그린 전환 직후 새 인스턴스는 캐시가 비어있는 상태로 시작(콜드스타트) — `PoiCacheWarmupScheduler`가 `ApplicationReadyEvent` 시점에 워밍하므로 큰 문제는 아니나 전환 타이밍에 유의.
- **MVP**: 🔜 (당장 착수 안 함 — 개발 완료 후 별도 작업으로 진행)
- **구현 상태**: ❌ (TODO)
- **FE 의존**: 없음 (인프라).
- **가치**: 배포 시점 API 응답 끊김 제거.

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
- **설명**: ~~한국관광공사 TourAPI `areaBasedList` → 경북(areaCode=35) 전체 시군구·콘텐츠 유형별 수집 → `tour` 테이블 적재~~. **v0.5.0부터 로컬 적재 자체가 공모전 규정 위반**이라 폐지. 대신 `TourApiClient`로 `areaBasedList2`/`detailCommon2`/`detailIntro2`/`detailInfo2`를 요청 시점 라이브 호출하고, Caffeine 캐시(지역 후보 리스트·POI 상세 각각 TTL 6h)를 경유해 응답 속도·호출한도를 보호한다. **2026-08-16(v0.5.8) 개선**: 후보 리스트 수집을 `contentTypeId` 없는 단일 호출(2000건 캡)에서 7개 콘텐츠타입별 분리 수집(타입당 최대 3000건)으로 전환 — 물량이 큰 타입이 소형 타입을 후보 풀에서 밀어내던 문제 해소. 페이지 크기 100→300, 페이지·타입 병렬 수집(가상 스레드) 추가.
- **상태**: TourAPI 오류 → 429/5xx는 지수 백오프 재시도(최대 3회) 후에도 실패 시 해당 페이지 결과 실패 처리 / 성공 → 캐시에 저장 후 반환.
- **MVP**: ✅
- **구현 상태**: ✅ (`TourApiClient` 라이브 호출 + `Semaphore(4)` 동시 요청 제한 + 재시도, `TourLiveDataService.getAllCandidates()` 타입별 병렬 수집, `PoiCacheWarmupScheduler`로 배포 직후·TTL 만료 전 백그라운드 워밍)
- **FE 의존**: 없음 (인프라).
- **가치**: 모든 POI·예산·코스 기능의 데이터 원천 (배치 대신 요청 시점 실시간 원천으로 전환).

### PO2. 큐레이션 POI 목록 조회
- **설명**: 지역(sigunguCode)·콘텐츠 유형 파라미터로 필터링된 POI 목록 반환. 응답에 `mapx`/`mapy` 좌표, 썸네일 포함. **v0.5.0**: 데이터 소스가 TourAPI 라이브 호출(PO1, 캐시 경유)로 변경 — 필터링·정렬은 애플리케이션 메모리에서 처리. **v0.6.4**: 각 아이템에 로그인 사용자의 좋아요 여부(`liked`, boolean) 추가 — 비로그인/탈퇴 사용자는 항상 `false`. **2026-08-22 스코프 확정**: `peopleCount`(인원 버킷 필터, BU2)·`theme`(테마 필터, BOQ14) 둘 다 구현하지 않기로 확정 — 아래 참고. **v0.6.7**: 각 아이템에 `poi_rating.stars` 기반 별점(`stars`, BigDecimal, nullable) 추가 — 미입력 POI는 `null`.
- **상태**: TourAPI가 데이터 없는 시군구 응답 → 빈 배열 + `available: false` 플래그 / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: ✅ (`GET /api/v1/poi` — `sigunguCode`(필수)·`contentTypeId`(선택) 필터로 최종 스코프 확정. `peopleCount`는 필수 파라미터로만 받고 필터링에는 미사용(BU2 스코프 아웃, 2026-08-08), `theme`은 파라미터 자체 미수신(BOQ14, 2026-08-22 구현 안 하기로 확정 — 이전엔 "설계 확정 후 추가 예정" TODO였으나 착수하지 않기로 결정), `avgPrice`는 BU1 취소로 항상 `null` 반환. 셋 다 재검토 예정 없음)
- **FE 의존**: S2 플래너 좌측 결과 영역 (P2).
- **가치**: 지역·유형 기반 큐레이션의 핵심 응답.

### PO3. POI 상세 통합 조회
- **설명**: `contentId` 기반으로 공통 상세(설명·이미지)·유형별 반복정보(요금·시설 등)를 통합해 단일 응답으로 반환. **v0.5.0**: `DetailCommon`/`DetailInfo`/`Attraction`/`Food`/`Accommodation` 등 로컬 엔티티 조인 대신, `TourApiClient.detailCommon2`/`detailInfo2` 라이브 호출을 조합해 응답 구성 (`TourLiveDataService.getFullDetail()`, 신규 `poiFullDetail` 캐시 TTL 6h 경유). **v0.6.4**: 로그인 사용자의 좋아요 여부(`liked`, boolean)와 `poi_rating.likes` 기반 총 좋아요 수(`totalLiked`, int) 추가. **v0.6.7**: `poi_rating.stars` 기반 별점(`stars`, BigDecimal, nullable) 추가 — 미입력 POI는 `null`.
- **상태**: TourAPI에 존재하지 않는 contentId → 404 / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: ✅ (`GET /api/v1/poi/{contentId}`, 인증 불필요) — `avgPrice`는 BU1 취소로 항상 `null` 반환 (PO2와 동일)
- **FE 의존**: S2a POI 상세 드로어.
- **가치**: 사용자가 코스 추가 전 상세 정보를 확인하는 핵심 API.

### PO5. POI 좋아요 토글
- **설명**: 로그인 사용자가 특정 POI에 좋아요를 추가하거나 취소. `user_poi_like` 중계 테이블로 중복 방지. `poi_rating.likes`(v0.5.0부터 `tour.likes`에서 이전)를 원자적 JPQL UPDATE로 증감.
- **상태 (v0.5.0 변경)**: 미인증 → 401 / 좋아요 추가 → `{liked: true, totalLiked: N}` / 취소 → `{liked: false, totalLiked: N}` / 성공 → 200. **"존재하지 않는 POI → 404" 검증 제거**: `Tour` 로컬 테이블이 없어져 존재 여부를 확인할 근거가 없음. `poi_rating` 행이 없으면 좋아요 액션 시 on-demand 생성(`likes=1`, `stars=null`)하고, TourAPI 라이브 재검증은 하지 않음(프론트가 이미 API로 확인된 contentId만 전달한다고 신뢰). **v0.6.5**: 응답 필드명을 `likes`→`totalLiked`로 변경해 PO2/PO3(GBC017/GBC018, v0.6.4)의 `totalLiked`와 통일 — 프론트가 좋아요 토글 응답으로 목록/상세 화면의 로컬 상태를 직접 patch할 때 필드명이 같아야 매핑이 단순해짐.
- **MVP**: ✅
- **구현 상태**: ✅ (`POST /api/v1/poi/{contentId}/like`, 인증 필수) — `TourRepository` 의존 제거하고 `PoiRatingRepository` 기반으로 재구현 필요
- **FE 의존**: S2 플래너 POI 카드 좋아요 버튼.
- **가치**: likes 데이터 축적 → CO6 추천 품질 향상의 원천 데이터.
- **TODO (고도화, 2026-08-22)**: 동시 중복 요청(더블탭·재시도) 경합 시 나중 요청이 유니크 제약 위반으로 500 에러 반환 가능. 데이터 정합성은 PK 제약으로 이미 보호되어 있어 심각도 낮음, 당장 착수 안 함. 상세: [PRD_BACK.md BOQ17](PRD_BACK.md#9-가정-및-미결-open-questions).

### PO4. 시군구 목록 및 데이터 보유 플래그 조회 (취소)

---

## 4. AI 여행 코스 생성 (Course Generation)

### CO1. AI 코스 생성 (Groq API — 현재)
- **설명**: 인원·기간·이동수단·테마·시군구(`sigunguCodes`, 복수 선택)를 입력받아 POI를 유형별 할당량으로 샘플링 → Groq LLM 프롬프트 구성 → Day별 일정 생성 → 검증 후 `TourCourseUserDefined` + `TourCourseUserDefinedDetail` 저장. **v0.5.0**: POI 후보는 로컬 `tour` 테이블 스캔이 아니라 TourAPI 라이브 조회(PO1, Caffeine 캐시 TTL 6h) 결과에서 샘플링. AI 응답 contentId 검증(하단 참조)도 요청 내에서 이미 확보한 후보 리스트와 메모리 대조로 변경 — 추가 DB/API 호출 없음.
  - **샘플링 알고리즘 (v0.2.6 개선)**: Hard exclusion(stars ≤ 1 제거) → Tier A(stars ≥ 4, 70% 슬롯) / Tier B(stars 2-3 또는 null, 30% 슬롯) 확률적 샘플링. Tier A 부족분은 Tier B로 보충. Cold-start(null stars) → Tier B 편입. likes 데이터 있으면 각 Tier 내 likes DESC 정렬, 없으면 shuffle. (v0.5.0: stars/likes 조회원이 `tour` → `poi_rating`으로 변경, 알고리즘 로직 자체는 동일)
  - **Rate Limit 재시도 (v0.3.1 개선)**: `GroqApiClient`에서 HTTP 429 응답을 `HttpClientErrorException`으로 명시 감지. `retry-after` 헤더 값(초→ms 변환) 우선 대기, 헤더 없으면 20초 기본 대기 후 재시도. 기타 에러(네트워크·5xx 등)는 기존대로 1초 대기. 최대 재시도(3회) 소진 시 rate limit 전용 에러 메시지 반환.
  - **reasoning 모델 대응 및 진단 로깅 (v0.6.0)**: `openai/gpt-oss-20b`(v0.5.12) reasoning 특성상 추론(CoT) 토큰이 `max_tokens`를 소진하면 `content`가 빈 문자열로 반환되어 파싱 실패하던 문제 수정. `reasoning_effort: "low"`·`reasoning_format: "parsed"`를 요청에 추가해 추론 토큰 소모를 최소화하고 reasoning이 `content`에 섞이지 않도록 분리. 응답 `finish_reason`·`usage`(prompt/completion/total tokens)를 캡처해 매 호출 시 로그로 남기고(`finish_reason=length`면 별도 경고), 파싱 실패 시에도 해당 정보를 에러 로그에 포함.
  - **전용 에러 응답 — HTTP 499 (v0.6.1)**: Groq 호출 실패(rate limit·API 에러·빈 응답)·AI 응답 파싱 실패·AI 응답 검증 실패(날짜 범위·타입·contentId)를 모두 신규 `AiCourseGenerationException`으로 통일. `GlobalExceptionHandler`가 이를 표준 코드가 아닌 **499**로 매핑해, 프론트가 일반 400(사용자 입력 오류)·500(서버 오류)과 구분되는 "AI 생성 실패"로 별도 처리할 수 있게 함. 응답 `data`에 `errorCode`(RATE_LIMITED/API_CALL_FAILED/EMPTY_RESPONSE/RESPONSE_PARSE_FAILED/RESPONSE_VALIDATION_FAILED)·`retryable`(재시도로 성공 가능성 있는지)·`finishReason`(Groq 진단 정보, 있는 경우)을 포함해 프론트가 원인별로 재시도 유도 여부를 판단 가능.
  - **장소별 상세 필드 (v0.4.0 추가, v0.5.0 소스 변경)**: AI가 장소별 `durationMinutes`(예상 소요시간)를 추정해 응답에 포함(`TourCourseUserDefinedDetail`에 저장, 변경 없음). 응답에 `thumbnailImg`·`operatingHours`·`cost`도 포함하되, v0.5.0부터 로컬 DB(`tour.firstimage`, type별 detail 테이블) 대신 TourAPI 라이브 조회(PO1 캐시 재사용) 파싱 결과 사용 — `cost`는 캐시된 라이브 응답 우선 → type 기본값 fallback.
  - **이동거리(2시간 이내) 지리적 클러스터링 및 사후 검증 (v0.6.8)**: 기존엔 AI에게 좌표 없이 `{id, t, n}`만 전달해 "CAR 2-3시간 이내" 같은 프롬프트 문구를 지킬 근거 데이터 자체가 없었음(무료 소형 모델이 이름만 보고 거리를 추측 → 실제 3시간 이상 떨어진 장소를 같은 날에 배치). Haversine 거리로 후보 POI를 이동수단별 반경(CAR 60km/PUBLIC_TRANSPORT 25km, 각각 최대 leg 120km/50km ≈ 2시간 상당)으로 지리적 클러스터링해, AI에게는 원본 좌표 대신 클러스터 번호(`g` 필드)만 전달 — 토큰을 아끼면서 소형 모델에게 거리 계산 대신 "같은 g값끼리 묶기"만 시켜 신뢰성 확보. WALK는 범위가 모호해 이번 스코프 제외. AI 응답 수신 후에는 실제 mapx/mapy로 같은 날 연속 방문지 간 거리를 재검증(`validateTravelDistances()`)해 한계 초과 시 기존 `AiCourseGenerationException(RESPONSE_VALIDATION_FAILED, retryable=true)` 흐름으로 실패시키는 안전장치 추가(신규 인프라 없이 기존 499 처리 경로 재사용). 숙소 인원수 기반 필터링(TourAPI `detailInfo2` 객실 정원 연동)은 검토 결과 v2로 유예.
- **상태**: TourAPI가 해당 지역 데이터 없음 → 400 / Groq 호출·파싱·응답 검증 실패(Rate Limit 초과, 빈 응답, 파싱 실패, contentId 불일치·날짜 초과 등) → **499**(`AiCourseGenerationException`, v0.6.1) / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: ✅ (`POST /api/v1/tour-course`) — 비로그인 허용, userId=null 저장.
- **FE 의존**: S2 플래너 (기본 추천 코스 P4, AI 코스 생성 연계).
- **가치**: 핵심 차별점 — 인원·테마 맞춤 자동 일정 생성.

### CO7. 코스 제목 수정
- **설명**: 로그인 사용자가 본인 코스의 제목(`title`)만 단독으로 수정. 소유권 확인 후 `TourCourseUserDefined.updateTitle()` 호출.
- **상태**: 미인증 → 401 / 타인 코스 → 403 / 코스 없음 → 404 / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: ✅ (`PATCH /api/v1/tour-course/{courseId}/title`, 인증 필수)
- **FE 의존**: S4 컬렉션 — 코스 이름 인라인 편집.
- **가치**: 저장 코스 식별성 — 기간·인원만으로는 코스 구분이 어렵기 때문에 제목 부여·수정이 필요.

### CO8. 코스 일정 수정
- **설명**: 로그인 사용자가 본인 코스의 일정 상세(`schedule`: 날짜별 장소 목록 — seq·time·type·contentId·durationMinutes)를 통째로 교체. 소유권 확인 후 기존 `TourCourseUserDefinedDetail` 전량 삭제 후 요청 내용으로 재삽입. 날짜는 코스 `startDate`~`endDate` 범위, `contentId`는 TourAPI 라이브 후보(캐시) 존재 여부, `type`은 `PlaceType` 값인지 검증(CO1 AI 응답 검증과 동일 규칙). 요청 바디의 `contentName`·`thumbnailImg`·`operatingHours`·`cost`는 조회 전용 표시 필드라 저장하지 않고 무시 — 응답 시 TourAPI 라이브 조회로 재조립.
- **상태**: 미인증 → 401 / 타인 코스 → 403 / 코스 없음 → 404 / 날짜 범위 초과·존재하지 않는 contentId·잘못된 type → 400 / 성공 → 200.
- **MVP**: ✅
- **구현 상태**: ✅ (`PATCH /api/v1/tour-course/{courseId}`, 인증 필수, `TourCourseShareResponseDto` 반환)
- **FE 의존**: S2 플래너 — 저장된 코스 재편집 후 저장.
- **가치**: 컬렉션 상세 보기 이후 재편집·저장 플로우의 핵심.

### CO6. 별점·추천수 기반 알고리즘 코스 추천 (v2+ 고도화 — v1 공모전 출시 범위 아님)
- **2026-08-16 결정**: 공모전 출시(v1)는 CO1(Groq AI 코스 생성)을 그대로 유지한다. 순수 알고리즘 추천으로의 전환은 출시 후 고도화 단계(v2/v3)에서 검토 — 더 나은 설계가 나오면 그때 붙인다. 아래 단계 구분은 그 고도화 로드맵.
- **설명**: `poi_rating.stars`·`poi_rating.likes`(v0.5.0부터 `tour.stars`/`tour.likes`에서 이전) 기반 POI 스코어링 알고리즘으로 여행자 조건(인원 버킷·테마·이동수단·기간·시군구)에 최적화된 Day별 코스를 Groq 없이 생성. 사용자 `travel_type` 선호도를 추가 가중치로 반영.
  - **단계 1 (v0.2.6 부분 구현, v1에서도 유지)**: stars 기반 Tier 샘플링 + likes 정렬 보조 신호를 CO1 샘플링에 적용 완료 (Groq 여전히 사용). 이건 CO1의 일부라 v1에도 그대로 남음.
  - **단계 2 (v2+ 고도화, 보류)**: Groq 완전 제거. 스코어링 결과로 직접 Day별 일정 조합. 유형별 할당량(식사·숙박·관광·문화 등) 규칙 엔진으로 구현.
- **상태**: stars 데이터 없는 POI → Tier B 편입(Cold-start) / likes 0 → shuffle / 성공 → CO1과 동일 응답.
- **MVP**: 🔜 (v1 범위 아님 — v2+ 고도화 대상)
- **구현 상태**: 🔧 (Tier 샘플링·likes 보조 정렬은 CO1에 적용되어 v1에도 유지 / Groq 제거·순수 알고리즘 전환은 v1 이후로 보류)
- **FE 의존**: S2 플래너 (CO1과 동일 API, 전환 시 내부 구현만 교체 — v1은 변경 없음).
- **가치**: LLM 의존 제거로 응답속도·비용·예측 가능성 개선. 사용자 반응 데이터가 쌓일수록 추천 품질 자동 향상. (v1 이후 데이터가 더 쌓인 뒤 재검토)

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
- **설명**: 코스 헤더 + 일정 상세(날짜·순서·시간·contentId·장소명·durationMinutes·thumbnailImg·operatingHours·cost·mapx·mapy) 통합 반환. 소유자 인증 필요. **v0.5.0**: 장소명·썸네일·운영시간·비용은 로컬 `Tour`/detail 테이블 재조회 대신 TourAPI 라이브 조회(PO1, POI 상세 캐시 TTL 6h) 결과로 조립. **v0.6.3**: 장소별 지도 좌표(`mapx`/`mapy`) 추가 — 지도 표현용. 이미 조회 중이던 `PoiSummary`(PO1 캐시)에 좌표가 포함돼 있어 TourAPI 추가 호출 없이 노출만 함.
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

### BU1. 음식점 평균 객단가 제공 (취소, 2026-08-16)
- **설명**: 서버 측 평균 객단가 산정 자체를 취소. 근거 데이터 소스가 없어 정확도를 담보할 수 없다고 판단, FE가 사용자로부터 실제 비용을 직접 입력받아 BE에 저장하는 방식(BU4)으로 대체.
- **MVP**: ❌ 제외
- **구현 상태**: ❌ (의도적 미구현, 재검토 예정 없음)

### BU2. 숙박 인원별 적합 숙소 분류 ❌ 스코프 아웃 (2026-08-08)
- **설명**: ~~`AccommodationDetailInfo`의 객실 수용 인원·요금 정보를 분석해 인원 버킷(1/2/3-4) 적합도 태그 및 1박 기준 예상 비용 제공~~. 근거였던 `accommodation_detail_info` 테이블이 로컬 DB 미저장 원칙에 따라 제거됨. **2026-08-08 기획 결정: 재설계 없이 기능 자체를 스코프 아웃** — `GET /api/v1/poi`의 `peopleCount`는 필수 입력값으로만 받고 숙박(contentTypeId=32) 필터링에는 사용하지 않음.
- **상태**: 스코프 아웃 확정 (재검토 예정 없음)
- **MVP**: ✅ → ❌ 제외
- **구현 상태**: ❌ (의도적 미구현)
- **FE 의존**: 없음 (peopleCount는 파라미터로만 수신, 필터링 미적용).
- **가치**: P1/P2 — 인원 맞춤 숙소 + 숙박비 예산 기본값.

### BU3. 교통비 추정 계산 및 제공 (취소, 2026-08-16)
- **설명**: 이동 관련 비용 계산은 BE가 아닌 FE에서 전담하기로 결정. BE는 관련 로직을 구현하지 않는다.
- **MVP**: ❌ 제외
- **구현 상태**: ❌ (의도적 미구현, 재검토 예정 없음)

### BU4. POI별 비용 저장 ✅
- **설명**: FE가 산정한 POI별 실제 비용(입장료·식비·숙박비 등, 이동비 포함 여부는 FE 재량)을 `tour_course_user_defined_detail.cost`에 그대로 저장. BE는 별도의 평균가·추정 로직 없이 입력값을 신뢰해 영속화만 담당. 코스 조회(CO4)·공개 뷰(SH2) 응답 시 저장된 `cost`를 우선 반환하고, 값이 없는 기존/AI 생성 일정은 기존과 동일하게 TourAPI 라이브 응답(`usefee`) → type별 기본값 순으로 폴백.
- **상태**: 저장된 `cost` 있음 → 그 값 반환 / 없음 → 라이브 조회·기본값 폴백 반환.
- **MVP**: ✅
- **구현 상태**: ✅ (`tour_course_user_defined_detail.cost INT NULL` 컬럼 추가 완료(2026-08-16, BOQ9 확정). `PUT /api/v1/tour-course/{courseId}`(`TourCourseUpdateRequestDto.PlaceUpdate.cost`)로 저장, `TourCourseServiceImpl.resolveCost()`가 저장값 우선 반영)
- **FE 의존**: S2 플래너 인라인 가격 수정 (P10).
- **가치**: P2 — 사용자가 입력한 실제 비용이 저장되지 않으면 재방문 시 초기화됨.

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
- **설명**: `courseId`로 코스 일정을 공개 조회. 인증 불필요(게스트 접근). FE는 SH1에서 생성한 링크로 이 API를 호출. 읽기 전용 — 수정·삭제 불가. CO4와 동일한 `TourCourseShareResponseDto` 반환 (durationMinutes·thumbnailImg·operatingHours·cost·mapx·mapy 포함, v0.6.3). **v0.5.0 주의**: 인증 없는 공개 엔드포인트라 반복 호출(봇 포함) 시 TourAPI 호출량이 가장 크게 튈 수 있는 지점 — POI 상세 캐시(TTL 6h) 재사용이 CO4와 공유되어 실제 라이브 호출은 캐시 미스 시에만 발생.
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

### DA3. FoodAvgPrice 데이터 적재 — ❌ v0.5.0 폐지, 2026-08-16 재검토 종료
- **설명**: ~~음식점 소분류(`lclsSystm3`)별 평균 객단가를 외식통계 또는 수동 입력으로 `food_avg_price` 테이블에 적재~~. **`food_avg_price` 테이블이 로컬 DB 미저장 원칙에 따라 제거됨.** BU1이 취소되어 대체 방식 검토도 종료 (재검토 예정 없음).
- **구현 상태**: ❌ (제거됨)
- **FE 의존**: 없음.

### DA4. `poi_rating.stars`·`poi_rating.likes` 데이터 수집
- **설명**: `poi_rating` 테이블(v0.5.0부터 `tour.stars`/`tour.likes`에서 분리)의 `stars`·`likes` 컬럼에 실제 데이터를 채움.
  - `likes`: PO5 좋아요 토글 API로 앱 내 수집 중 (`user_poi_like` + 원자적 JPQL UPDATE, on-demand 행 생성). ✅ 파이프라인 구축 완료.
  - `stars`: 네이버·다이닝코드·구글 평점 웹 검색 기반으로 285개 POI 일괄 입력 완료 (2026-06-27, 당시 `tour.stars`). v0.5.0 DB 마이그레이션 시 `poi_rating`으로 이관 완료(2026-08-08). 여행코스(contenttypeid=25) 15개는 별점 대상 제외(null 유지).
- **MVP**: 🔜 (CO6 알고리즘 추천 구현 전 선결 조건)
- **구현 상태**: ✅ (`likes` 수집 파이프라인 완료 / `stars` 285개 초기값 `poi_rating`으로 이관 완료)
- **FE 의존**: PO5 좋아요 버튼 (likes 수집 연결됨) / **v0.6.7부터** PO2·PO3 응답의 `stars` 필드로 화면에 직접 노출됨.
- **가치**: CO6 별점·추천수 기반 알고리즘 추천의 핵심 원천 데이터. v0.6.7부터는 PO2/PO3 응답에도 노출되어 사용자에게 직접 보여지는 표시용 데이터를 겸함.

### DA5. TourAPI 응답 캐싱 (Caffeine) — **신규 (v0.5.0)**
- **설명**: 로컬 DB 미저장 원칙에 따라 매 요청 TourAPI 라이브 호출이 되면서 발생하는 응답 지연·호출한도 소진 리스크를 완화하기 위한 인프로세스 캐시 계층. 캐시 2종, 둘 다 TTL 6시간:
  - 지역 POI 후보 리스트 캐시 (코스 생성용 `areaBasedList2` 결과, 단일 키 `'all'` — 7개 콘텐츠타입별로 나눠 수집한 뒤 하나로 병합해 캐싱. v0.5.8부터 시군구별이 아닌 전체 후보를 한 번에 담고 애플리케이션 메모리에서 시군구 필터링)
  - POI 개별 상세 캐시 (`detailCommon2`/`detailIntro2`/`detailInfo2` 결과, `contentId` 키) — 코스 생성(CO1)·코스 조회(CO4)·공개 뷰(SH2)·POI 상세 조회(PO3)에서 공통 재사용
- **상태**: 캐시 히트 → TourAPI 호출 없이 즉시 반환 / 캐시 미스·만료 → 라이브 호출 후 캐시 적재. v0.5.8부터 `getAllCandidates()`는 `sync=true`라 캐시 미스 시 동시 요청이 중복 호출하지 않음.
- **MVP**: ✅ (v0.5.0 전환의 필수 구성요소)
- **구현 상태**: ✅ (`CacheConfig`에 `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine` 의존성 기반 `CaffeineCacheManager` 구성 완료 — `poiCandidates`·`poiDetail`·`poiFullDetail` 3종 캐시, 전부 TTL 6h·`maximumSize(2000)`. `TourLiveDataService.getAllCandidates()`/`getDetail()`/`getFullDetail()`이 `@Cacheable`로 각 캐시 실사용 중, PO1~PO3·CO1·CO4·SH2 전부 재사용. **v0.5.8 추가**: `PoiCacheWarmupScheduler`가 배포 직후(`ApplicationReadyEvent`) 및 TTL 만료 전(5시간50분 주기)에 후보 캐시를 백그라운드로 미리 채워, 실사용자가 콜드스타트를 밟지 않도록 함)
- **FE 의존**: 없음 (인프라, 응답 속도에 간접 영향).
- **가치**: 1GB 메모리 프리티어 서버에서 별도 인프라(Redis 등) 없이 TourAPI 호출량·응답 지연을 실질적으로 절감.

---

## 부록: 구현 우선순위 요약

FE MVP 기준으로 백엔드 미구현 항목 우선순위를 나열한다.

**MVP 구현 우선순위**

| 우선순위 | 기능 ID | 기능명 | 이유 |
| --- | --- | --- | --- |
| ~~1~~ | ~~INF4~~ | ~~CORS 설정~~ | ~~FE-BE 통신 전제, 모든 API 사용 전 필요~~ — ✅ 구현 완료 |
| ~~2~~ | ~~DA5~~ | ~~Caffeine 캐시 계층~~ | ~~v0.5.0 라이브 호출 구조 전환의 선결 조건~~ — ✅ 구현 완료 |
| ~~3~~ | ~~PO4~~ | ~~시군구 목록·플래그~~ | 취소 |
| ~~4~~ | ~~PO2~~ | ~~큐레이션 POI 목록~~ | ~~S2 플래너 핵심 데이터~~ — ✅ 구현 완료 (`sigunguCode`·`contentTypeId` 필터가 최종 스코프. `peopleCount`/`theme`/`avgPrice`는 전부 의도적 미구현으로 확정, 2026-08-22) |
| ~~5~~ | ~~PO3~~ | ~~POI 상세 통합 조회~~ | ~~S2a 상세 드로어~~ — ✅ 구현 완료 |
| ~~6~~ | ~~BU3~~ | ~~교통비 추정~~ | 취소 — FE 전담 |
| ~~7~~ | ~~BU4~~ | ~~POI별 비용 저장~~ | ~~S2 예산 대시보드 인라인 수정~~ — ✅ 구현 완료 |

> ❌ v0.5.0에서 DA1~DA3(TourAPI 배치 수집·스케줄링·FoodAvgPrice 적재) 폐지 — 로컬 DB 미저장 원칙에 따라 대상 자체가 사라짐.

> ✅ v0.2.6에서 완료: CO2(소유권 이전), CO3/CO4/CO5(코스 목록·상세·삭제), SH2(공개 뷰), PO5(좋아요 토글)
> ✅ v0.2.7에서 완료: AU4(카카오 OAuth 콜백, `KakaoOAuthService` + `KakaoApiClient` 구현)

**스키마 결정 선결 과제 (구현 전 BOQ 확정 필요)**

| BOQ | 내용 | 영향 기능 | 상태 |
| --- | --- | --- | --- |
| BOQ9 | `tour_course_user_defined_detail`에 비용 컬럼 추가 여부 | BU4 POI별 비용 저장 | ✅ 확정 (2026-08-16): `budget_override`(오버라이드 개념) 대신 단순 `cost INT NULL` 컬럼 추가 — FE 입력값을 그대로 저장 |
| BOQ11 | 공유 스키마: `share_token` 컬럼 재추가 vs. 별도 `share_snapshot` 테이블 | SH1/SH2 공유 기능 | ✅ 확정: 별도 스냅샷 없이 courseId 직접 공개 뷰로 처리 |
| BOQ12 | `stars` 데이터 수집 방법 확정 (`likes`는 PO5로 수집 중) | DA4, CO6 알고리즘 추천 | 미확정 (AI 검색 수동 입력 예정) |
| BOQ13 | Caffeine 캐시 TTL | DA5, PO1~PO3, CO1, CO4, SH2 | ✅ 확정 (v0.5.0): 지역 후보 리스트·POI 상세 모두 TTL 6h |
| BOQ14 | BU1 근거 테이블(`food_avg_price`) 소실에 따른 예산 메타데이터 재설계 | BU1, DA3 | ✅ 확정 (2026-08-16): 재설계하지 않고 BU1 기능 자체 취소. BU4(비용 직접 입력)로 대체 |

**Post-MVP 로드맵**

| 단계 | 기능 ID | 기능명 |
| --- | --- | --- |
| v0.3 | CO6-1 | stars·likes 가중치 기반 POI 샘플링 (Groq 보조) |
| v0.3 | DA4 | tour.stars·likes 데이터 수집 파이프라인 |
| v0.5 | DA5 | TourAPI 응답 Caffeine 캐싱 (로컬 DB 미저장 전환) |
| v2+ | CO6-2 | 순수 알고리즘 기반 코스 추천 (Groq 완전 제거) — 2026-08-16: v1 공모전 출시는 Groq 유지 확정, 고도화 단계로 연기 |
| ~~v1.0~~ | ~~DA2~~ | ~~TourAPI 배치 스케줄링~~ — v0.5.0에서 폐지 |

---

## 다음 단계

이 분해도를 입력으로 **API 명세서**를 작성한다. 각 기능 블록의 API 의존 항목을 받아 엔드포인트·요청/응답 스키마·HTTP 상태코드·인증 여부·검증 조건으로 전개한다.
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.7.1] - 2026-09-05

### Added

#### 프론트 요청 IP·네트워크 정보 로깅용 nginx access_log 커스텀 포맷 추가

프론트 요청이 실제로 어떤 IP에서 오는지 확인하고 향후 운영 가시성을 확보하기 위해
`deploy/nginx-cocobackend.conf`에 라벨 붙은 `log_format`을 추가함.

FE(Netlify, `gbcoco.netlify.app`)가 CORS 회피를 위해 자체 프록시로 이 서버를 호출하는
구조라, 기존 기본 access_log의 `$remote_addr`는 실사용자 IP가 아니라 Netlify 프록시의
유동 IP로 찍히는 것을 확인함. 실사용자 IP는 `X-Forwarded-For` 체인의 맨 앞 값.

- `log_format cocobackend_named` 신규 — `time`/`remote_addr`/`client_ip`(XFF)/`method`/
  `uri`/`status`/`request_time`/`upstream_time`/`referer`/`user_agent`를 `key=value` 형태로
  라벨링해 가독성 확보 (`client_ip` 값은 `"IP, IP"` 형태로 쉼표+공백을 포함하므로 다른
  값과 마찬가지로 따옴표로 감쌈, `time_local`도 날짜와 UTC 오프셋 사이에 공백이 있어
  `combined` 포맷 관례대로 `[$time_local]`로 감쌈 — 둘 다 공백 기준 파싱이 깨지지
  않도록)
- 사이트 전용 `access_log /var/log/nginx/cocobackend-access.log`로 분리 (기존 기본
  access_log는 그대로 유지)
- 스프링부트 앱 레벨의 IP 로깅(인증 유저-IP 연계)은 이번 범위에서 제외 — 지금은 nginx
  로그만으로 목적(실사용자 IP 확인 + 운영 데이터 축적)이 충분히 커버됨

### Docs

#### BOQ1/BOQ4/BOQ5 문서 정리 — 실제로는 이미 해결됐거나 대상이 사라진 open question 정리

`docs/PRD_BACK.md` 미결(Open Questions) 중 ✅ 표시 없이 남아있던 세 항목을 실제 코드
상태와 대조해 확인 후 정리함. 코드 변경 없음.

- BOQ1(인원 버킷 정의): PO2의 `peopleCount` 필터 자체가 BOQ14로 스코프아웃되며 매핑을
  결정할 대상이 사라짐
- BOQ4(비로그인 코스 소유권 이전): `PATCH /{courseId}/assign`으로 이미 구현 완료
  상태였음(`TourCourseController.assignCourse()`), 문서 갱신만 누락돼 있었음
- BOQ5(공유 링크 만료 정책): BOQ11 결정(스냅샷 테이블 없이 courseId 기반 라이브 view)에
  따라 만료 정책 자체가 불필요해짐

#### BOQ12 stars 자동 수집 조사 결과 기록 — 자동화·수동 입력 모두 보류

`poi_rating.stars` 추가 수집을 위해 구글 Places API/네이버 지도/카카오 로컬 API 3개
경로를 실제로 조사·테스트했으나 전부 막힘을 확인하고 그 내용을 문서화함.

- 구글 Places API: `rating` 필드는 있으나 결제 계정(카드) 등록이 필수라 배제
- 네이버 지도: 비공식 검색 엔드포인트가 `ncaptcha`로 자동화 요청을 차단 — 우회 시도는
  하지 않기로 함
- 카카오 로컬 API: 무료·카드 불필요하지만 응답에 별점 필드 자체가 없음
- 경북 전체 라이브 후보 재조회 결과 총 1,624건으로, 기존 수동 입력 285건 대비 갭이
  약 17.5%밖에 안 됨을 확인
- 자동화 경로가 모두 막힌 김에 기존 "AI 검색 기반 수동 입력" 계획도 함께 보류로 확정.
  재검토 시점·방향은 미정으로 열어둠 (stars는 현재 정렬/추천에 쓰이지 않는 순수 표시값)

#### 백엔드 코드 리뷰 할 일 문서 신규 (`docs/code_review/`)

`/code-review high src/main/java/com/eodegano/cocobackend` 실행 결과(버그·보안·
효율성 관점 10건)를 실제 코드와 대조 검증한 뒤, 앞으로 이 문서만 보고 수정 작업을
진행할 수 있도록 우선순위·파일·라인·실패 시나리오·수정 방향을 정리한 체크리스트
문서를 신규 작성함. 코드 변경 없음(문서만 추가).

- `docs/code_review/2026-09-05_backend-review.md` 신규 — 보안 2건(카카오 OAuth
  이메일 일치 자동 연결, 회원 정보 API IDOR), 정확성 버그 7건(TourAPI 재시도 소진 시
  장애 은폐, POI 타입-contentTypeId 불일치, 캐시 키 누락, 코스 소유권 배정 동시성,
  Groq 응답 필드 누락 시 미처리 NPE 3건), 효율성 1건(POI 상세 순차 조회) 기록
- 검증 과정에서 초안의 부정확한 부분 2곳 수정: 비밀번호 변경은 대상 계정 현재
  비밀번호 검증이 있어 순수 IDOR로는 불가함을 명시, `resolveContentTypeId` 관련 줄
  번호를 실제 위치로 정정

### Files Changed (5 files)

- `deploy/nginx-cocobackend.conf`
- `docs/FEATURES_BACK.md`
- `docs/PRD_BACK.md`
- `docs/code_review/2026-09-05_backend-review.md`
- `CHANGELOG.md`

## [0.7.0] - 2026-09-05

### Docs

#### 코스 생성 방향 영구 확정 — CO6(순수 알고리즘) 로드맵 취소, PRD.md 백엔드 섹션 동기화

Groq AI 기반 코스 생성(CO1)을 영구 핵심 방식으로 유지하기로 확정하고, "Groq 완전
제거"를 목표로 하던 CO6(순수 알고리즘 코스 추천) 로드맵을 v2+ 고도화 대상이 아니라
취소로 재작성함. Groq 완전 실패 시 대비한 Degraded Fallback 안전망은 설계만 남기고
착수는 보류(BOQ18 신규).

오랫동안 갱신되지 않았던 최상위 통합 문서 `docs/PRD.md`의 백엔드 관련 섹션(한 줄
정의·핵심 기능·데이터 파이프라인·시스템 구성·API 계약 요약·Open Questions·로드맵·
구현 현황)을 `PRD_BACK.md`/`FEATURES_BACK.md` 기준으로 동기화함.

- `docs/FEATURES_BACK.md`: CO1에 BOQ18 TODO 추가, CO6를 "취소"로 재작성
- `docs/PRD_BACK.md`: 한 줄 정의·책임 영역 표·B-F2 로드맵·BOQ7 갱신, BOQ18 신규 추가
- `docs/PRD.md`: §1/§3/§4/§5/§6/§7/§8/§9/§11/§12/§14 백엔드 관련 내용 전면 정리

### Fixed

#### TourCourseControllerTest 하드코딩 날짜로 인한 테스트 실패 수정

AI 코스 생성 499 응답 테스트가 요청 바디에 `startDate: "2026-09-01"`을 하드코딩해
뒀는데, 시간이 지나 해당 날짜가 과거가 되면서 `@FutureOrPresent` 검증에 걸려
실패(400)하던 문제 수정. `LocalDate.now()` 기준 상대 날짜로 교체.

### Files Changed (5 files)

- `docs/FEATURES_BACK.md`
- `docs/PRD_BACK.md`
- `docs/PRD.md`
- `CHANGELOG.md`
- `src/test/java/com/eodegano/cocobackend/controller/TourCourseControllerTest.java`

## [0.6.11] - 2026-08-29

### Added

#### AI 코스 생성 응답에 기본 제목(title) 추가

`generateTourCourse` 응답에는 `courseId`만 있고 제목이 없어, 프론트에서 별도로 제목을
붙여줘야 했음. 요청에 이미 `sigunguCodes`가 들어오므로 이를 기반으로 기본 제목을
서버에서 생성해 저장하고 응답에도 실어주기로 함.

시군구 코드→이름 매핑은 이미 `mst_sigungu` 테이블(`MstSigungu` 엔티티)에 시드되어
있었으나(`docs/CLAUDE.md`에 "레포지토리·서비스 미연결" 상태로 명시) 연결되어 있지 않던
상태였음. 동일 데이터를 enum으로 새로 하드코딩하면 DB와 코드 두 곳에서 관리해야 하므로,
대신 리포지토리를 새로 뚫어 기존 테이블을 그대로 활용함.

- `MstSigunguRepository` 신규 추가 — `mst_sigungu` 조회용
- `TourCourseServiceImpl.buildDefaultTitle()` 추가 — `sigunguCodes`를 시군구명으로
  변환해 기본 제목 생성 (코드 없음 → "여행 코스", 1개 → "{시군구명} 여행 코스", 2개
  이상 → "{첫 시군구명} 외 N곳 여행 코스")
- 코스 저장(`saveTourCourse`) 시 위 제목을 `title` 컬럼에 저장 (기존
  `updateCourseTitle` API로 사용자가 이후 변경 가능한 점은 유지)
- `TourCourseGenerateResponseDto`에 `title` 필드 추가, `generateTourCourse` 응답에 포함

### Files Changed (4 files)

- `src/main/java/com/eodegano/cocobackend/repository/MstSigunguRepository.java`
- `src/main/java/com/eodegano/cocobackend/service/TourCourseServiceImpl.java`
- `src/main/java/com/eodegano/cocobackend/dto/TourCourseGenerateResponseDto.java`
- `src/test/java/com/eodegano/cocobackend/service/TourCourseServiceImplTest.java`

## [0.6.10] - 2026-08-29

### Added

#### 무중단 배포(Blue/Green) 전환 — INF7/BOQ16

기존 배포(`.github/workflows/deploy.yml`)는 `systemctl restart cocobackend` 단일 호출이라
재시작 중 짧은 다운타임이 발생했음(INF7 TODO). Docker/K8s 없이 지금의 systemd 직접
배포 방식을 유지하면서, systemd blue/green 유닛 2개(8080/8081) + nginx 리버스 프록시
조합으로 무중단 전환.

- `deploy/cocobackend-blue.service` / `deploy/cocobackend-green.service` 신규 —
  기존 `deploy/cocobackend.service`(단일 인스턴스)를 대체. 두 유닛 모두 공유
  `/home/ubuntu/cocobackend/.env`를 `EnvironmentFile`로 참조하고, 포트만
  `Environment=SERVER_PORT`로 분리
- `deploy/nginx-cocobackend.conf` 신규 — `/etc/nginx/conf.d/cocobackend_upstream.conf`가
  가리키는 활성 포트로만 프록시하는 사이트 설정
- `deploy/switch-active.sh` 신규 — 지정한 포트로 nginx 업스트림을 갱신하고
  `nginx -t && systemctl reload nginx` 실행 (CI가 sudo NOPASSWD로 호출하는 유일한 진입점)
- `.github/workflows/deploy.yml`의 마지막 스텝을 `Restart service`(단일 재시작)에서
  `Blue/Green switch`로 교체 — `active_color` 상태 파일로 비활성 슬롯 판별 → jar 배포 →
  재시작 → 기존 `spring-boot-starter-actuator`의 `/actuator/health`(permitAll, 코드
  변경 없음) 헬스체크(최대 60초 재시도) → 통과 시 `switch-active.sh`로 nginx 전환 →
  `active_color` 갱신 → 이전 슬롯 `systemctl stop`. 헬스체크 실패 시 배포만 실패
  처리하고 기존 활성 슬롯은 그대로 유지(자동 롤백 효과)
- `deploy/BLUEGREEN_SETUP.md` 신규 — 서버 최초 1회 설정(nginx 설치, 디렉토리·상태
  파일 준비, systemd 유닛 등록, `switch-active.sh` 배치, nginx 사이트 활성화, sudoers
  NOPASSWD 등록) 및 수동 롤백 절차 runbook. 이 세션에서는 서버에 직접 접속하지 않기로
  확정해 레포 측 파일만 준비 — 서버 적용은 runbook을 보고 별도로 진행 필요

### Fixed

#### nginx 사이트 설치 경로(conf.d) 및 헬스체크 타임아웃 — 실서버 적용 중 발견

서버 최초 1회 설정을 실제로 적용하는 과정에서 문서상 가정과 다른 부분 2가지가 발견되어 수정:

- **nginx 사이트 설정 경로**: 문서는 `sites-available`/`sites-enabled` 관례를 전제했으나,
  실제 서버(nginx 1.30.4, `apt install nginx`)의 `nginx.conf`는 `include
  /etc/nginx/conf.d/*.conf;`만 있고 `sites-enabled`는 include하지 않아 사이트 설정이
  전혀 적용되지 않았음. `deploy/nginx-cocobackend.conf`를 `conf.d/cocobackend.conf`로
  직접 설치하는 방식으로 통일하고, `BLUEGREEN_SETUP.md`에 사전에 `nginx.conf`의
  include 설정을 확인하는 절차 추가
- **헬스체크 타임아웃**: 최초 배포 시도에서 green 인스턴스가 60초(30회×2초) 안에
  기동을 못 마쳐 헬스체크 타임아웃으로 배포 실패(기존 활성 슬롯은 영향 없이 유지 —
  롤백 설계는 의도대로 동작). 실제 로그 확인 결과 `Started CocoBackendApplication`까지
  약 76초(서비스 시작 기준 약 82초) 소요 — blue+green 동시 기동 시 메모리 압박(swap
  발생 확인)으로 평소보다 느려진 것으로 추정. 헬스체크 대기를 180초(60회×3초)로 확대

### Files Changed

- `.github/workflows/deploy.yml`
- `deploy/cocobackend-blue.service` (신규)
- `deploy/cocobackend-green.service` (신규)
- `deploy/cocobackend.service` (삭제, blue/green 유닛으로 대체)
- `deploy/nginx-cocobackend.conf` (신규)
- `deploy/switch-active.sh` (신규)
- `deploy/BLUEGREEN_SETUP.md` (신규)
- `docs/FEATURES_BACK.md`
- `docs/PRD_BACK.md`

## [0.6.9] - 2026-08-29

### Fixed

#### AI 코스 생성 — 마지막 날(체크아웃일)에 숙소가 포함되는 문제 수정

`tour_course_user_defined` 데이터를 확인해보니 다수의 코스에서 여행 마지막 날(귀가일)
마지막 일정으로 `ACCOMMODATION`이 들어가 있었음(id=85 등). 코드에는 이를 막는 검증이
전혀 없었고, 전적으로 프롬프트 지시(`system-prompt.txt` RULE 4 "Include ACCOMMODATION
at the end of each day (multi-day trips only)")에만 의존하는 구조였는데, 이 문구가
"multi-day trip이면 매일 끝에 숙소"로 읽혀 마지막 날도 예외 없이 포함 대상이 됐던 것.
같은 날 생성된 코스끼리도 결과가 들쭉날쭉했던 걸로 보아 온도 0.7의 소형 모델이 규칙을
문자 그대로 따르는 경우와 상식적으로 마지막 날을 제외하는 경우가 뒤섞여 비결정적으로
발생한 문제였음.

- `system-prompt.txt` RULE 4에 "마지막 날은 체크아웃/귀가일이므로 ACCOMMODATION 제외"를
  명시적으로 추가
- `daily-schedule-template.txt`의 Night 항목·"End multi-day trips with accommodation"
  문구를 마지막 날 예외로 수정(마지막 날은 단일 여행처럼 저녁 식사로 마무리)
- 프롬프트만으로는 소형 모델이 규칙을 어길 수 있어, `TourCourseServiceImpl.validateAiResponse()`에
  사후 검증(`validateNoAccommodationOnLastDay()`) 추가 — 마지막 날 스케줄에 `ACCOMMODATION`이
  있으면 기존 이동거리 검증(v0.6.8)과 동일하게 `AiCourseGenerationException
  (RESPONSE_VALIDATION_FAILED, retryable=true)`로 실패시켜 프론트가 재시도하도록 함(신규
  인프라 없이 기존 499 처리 경로 재사용)

### Files Changed (4 files)

- `src/main/java/com/eodegano/cocobackend/service/TourCourseServiceImpl.java`
- `src/main/resources/prompts/system-prompt.txt`
- `src/main/resources/prompts/daily-schedule-template.txt`
- `src/test/java/com/eodegano/cocobackend/service/TourCourseServiceImplTest.java`

## [0.6.8] - 2026-08-29

### Added

#### AI 코스 생성 — 이동거리(2시간 이내) 지리적 클러스터링 및 사후 검증 추가

AI(Groq gpt-oss-20b)가 생성한 코스에서 같은 날 연속 방문지 간 실제 이동거리가 2~3시간을
넘는 문제 수정. 원인은 AI에게 넘기는 후보 데이터(`id`/`t`/`n`)에 좌표가 아예 없어서, 무료
소형 모델이 좌표 없이 "가까움"을 이름만으로 추측하고 있었던 것 — 프롬프트 문구("CAR 2-3시간
이내")만으로는 근본적으로 지켜질 수 없는 구조였음.

- `TourCourseServiceImpl`에 Haversine 거리 기반 지리적 클러스터링 추가. 이동수단별로 실효
  평균속도를 가정해 클러스터 반경을 다르게 설정(CAR: 반경 60km/최대 leg 120km ≈ 2시간
  @60km/h, PUBLIC_TRANSPORT: 반경 25km/최대 leg 50km ≈ 2시간 @25km/h). WALK는 범위가
  애매해 이번 스코프에서 제외(기존 동작 유지, 클러스터링 미적용)
- AI에게는 원본 좌표 대신 클러스터 그룹 번호(`g` 필드, 정수 1~2자)만 전달 — 소형 모델에게
  거리 계산 대신 "같은 g값끼리 묶기"만 시켜 토큰도 아끼고(원본 좌표 대비 POI당 토큰 절감)
  안정성도 높임 (`prompts/system-prompt.txt`, `prompts/daily-schedule-template.txt` 갱신)
- AI 응답 수신 후 실제 mapx/mapy로 같은 날 연속 방문지 간 거리를 재검증하는 안전장치
  추가(`validateTravelDistances()`) — 클러스터링에도 불구하고 AI가 그룹을 무시하고 배치한
  경우, 이동수단별 한계 초과 시 기존 `AiCourseGenerationException(RESPONSE_VALIDATION_FAILED,
  retryable=true)` 흐름으로 실패시켜 프론트가 재시도하도록 함(신규 인프라 없이 기존 499 처리
  경로 재사용)
- 숙소 인원수 기반 필터링(TourAPI `detailInfo2` 객실 정원 연동)은 검토 결과 별도 API 연동이
  필요한 작업이라 v2로 유예 — CO6(별점·추천수 기반 알고리즘 코스 추천) v2/v3 고도화 로드맵과 일관

### Files Changed (5 files)

- `src/main/java/com/eodegano/cocobackend/client/GroqApiClient.java`
- `src/main/java/com/eodegano/cocobackend/service/TourCourseServiceImpl.java`
- `src/main/resources/prompts/system-prompt.txt`
- `src/main/resources/prompts/daily-schedule-template.txt`
- `src/test/java/com/eodegano/cocobackend/service/TourCourseServiceImplTest.java`

## [0.6.7] - 2026-08-29

### Added

#### POI 목록/상세 조회 응답에 별점(stars) 추가

`totalLiked`(`poi_rating.likes`)와 동일한 방식으로 `poi_rating.stars`도 프론트에 노출.

- `GET /api/v1/poi`(PO2)·`GET /api/v1/poi/{contentId}`(PO3) 둘 다 `stars`(BigDecimal, nullable) 필드 추가
- `poi_rating` 행이 없거나 `stars`가 아직 입력되지 않은 POI는 `null` 반환 (에러 아님, 안전 처리)
- 목록 조회는 `PoiRatingRepository.findByContentidIn()` 벌크 조회로 N+1 없이 한 번에 평점 Map을 구성 (liked 조회와 동일 패턴)
- 상세 조회는 기존에 `totalLiked`만을 위해 호출하던 `poiRatingRepository.findById()` 결과를 재사용해 `stars`까지 추출 (쿼리 중복 없음)

### Files Changed (7 files)

- `src/main/java/com/eodegano/cocobackend/dto/PoiCurationItemDto.java`
- `src/main/java/com/eodegano/cocobackend/dto/PoiDetailResponseDto.java`
- `src/main/java/com/eodegano/cocobackend/service/PoiCurationServiceImpl.java`
- `src/main/java/com/eodegano/cocobackend/service/PoiDetailServiceImpl.java`
- `src/test/java/com/eodegano/cocobackend/controller/PoiControllerTest.java`
- `src/test/java/com/eodegano/cocobackend/service/PoiCurationServiceImplTest.java`
- `src/test/java/com/eodegano/cocobackend/service/PoiDetailServiceImplTest.java`

## [0.6.6] - 2026-08-22

### Changed

#### `GET /api/v1/poi`(PO2) — `peopleCount`·`theme` 필터 구현 안 하기로 확정 (BOQ14)

기획 재검토 결과 큐레이션 POI 목록 조회의 필터 스코프를 `sigunguCode`·`contentTypeId` 두 개로 최종 확정했다. 문서상 상태만 정리, 코드 변경 없음(두 파라미터 모두 애초에 컨트롤러가 받고 있지 않았음).

- `peopleCount`: 이미 2026-08-08 BU2 스코프아웃 결정으로 필터링에 미사용 확정된 상태 — 문서상 "🔧 미완성" 표기를 "✅ 의도적 미구현"으로 정리
- `theme`: 기존에는 "데이터 소스·매핑 설계 후 추가 예정" TODO였으나, 착수하지 않기로 결정 — `mst_theme` 마스터 테이블은 유지하되 이 API와는 연결하지 않음
- `PO2` 구현 상태를 🔧 → ✅로 변경 (남은 스코프인 지역·유형 필터가 최종 형태)

### Files Changed (2 files)

- `docs/FEATURES_BACK.md`
- `docs/PRD_BACK.md`

## [0.6.5] - 2026-08-22

### Fixed

#### POI 좋아요 토글 응답 필드명 통일 (likes → totalLiked)

`POST /api/v1/poi/{contentId}/like`(PO5) 응답의 좋아요 총개수 필드명이 v0.6.4에서 추가한
`GET /api/v1/poi`·`GET /api/v1/poi/{contentId}`의 `totalLiked`와 달라(`likes`) 프론트가
두 응답을 같은 로컬 상태로 합칠 때 매핑이 번거로웠던 문제 수정.

- `PoiLikeResponseDto.likes` → `totalLiked`로 필드명 변경 (`{liked, totalLiked}`)
- 프론트는 좋아요 토글 후 GET 재조회 없이 이 응답으로 목록/상세의 `liked`·`totalLiked`를
  바로 patch하면 됨 (필드명이 같아져 별도 매핑 불필요)

### Files Changed (5 files)

- `src/main/java/com/eodegano/cocobackend/dto/PoiLikeResponseDto.java`
- `src/main/java/com/eodegano/cocobackend/service/PoiLikeServiceImpl.java`
- `src/test/java/com/eodegano/cocobackend/service/PoiLikeServiceImplTest.java`
- `src/test/java/com/eodegano/cocobackend/controller/PoiControllerTest.java`
- `docs/FEATURES_BACK.md`

## [0.6.4] - 2026-08-22

### Added

#### POI 목록/상세 조회 응답에 좋아요 여부(liked)·총 좋아요 수(totalLiked) 추가

프론트가 POI 카드·상세 화면에서 좋아요 버튼 상태와 총 좋아요 수를 표시할 수 있도록 필드 추가.

- `GET /api/v1/poi`(PO2)·`GET /api/v1/poi/{contentId}`(PO3) 둘 다 로그인 사용자의 좋아요 여부(`liked`, boolean) 추가 — `user_poi_like` 테이블 기준
- `GET /api/v1/poi/{contentId}`에는 `poi_rating.likes` 기반 총 좋아요 수(`totalLiked`, int)도 추가
- 두 API 모두 `permitAll`(비로그인 허용)이므로 비로그인·유효 토큰이지만 사용자 미조회(탈퇴 등)인 경우 예외 없이 `liked=false`로 안전 처리
- 목록 조회는 N개 POI를 한 번에 반환하므로 `UserPoiLikeRepository.findContentIdsByUserIdAndContentIdIn()` 벌크 조회를 신규 추가해 건당 exists 쿼리(N+1) 없이 한 번에 좋아요 Set을 구함

### Files Changed (11 files)

- `src/main/java/com/eodegano/cocobackend/controller/PoiController.java`
- `src/main/java/com/eodegano/cocobackend/dto/PoiCurationItemDto.java`
- `src/main/java/com/eodegano/cocobackend/dto/PoiDetailResponseDto.java`
- `src/main/java/com/eodegano/cocobackend/repository/UserPoiLikeRepository.java`
- `src/main/java/com/eodegano/cocobackend/service/PoiCurationService.java`
- `src/main/java/com/eodegano/cocobackend/service/PoiCurationServiceImpl.java`
- `src/main/java/com/eodegano/cocobackend/service/PoiDetailService.java`
- `src/main/java/com/eodegano/cocobackend/service/PoiDetailServiceImpl.java`
- `src/test/java/com/eodegano/cocobackend/controller/PoiControllerTest.java`
- `src/test/java/com/eodegano/cocobackend/service/PoiCurationServiceImplTest.java`
- `src/test/java/com/eodegano/cocobackend/service/PoiDetailServiceImplTest.java`

## [0.6.3] - 2026-08-22

### Added

#### 코스 상세/공유뷰 응답에 지도 좌표(mapx/mapy) 추가

프론트가 코스 상세 화면에서 장소를 지도에 표시할 수 있도록, 일정별 장소에 `mapx`/`mapy` 좌표 필드 추가.

- `TourCourseShareResponseDto.PlaceInfo`에 `mapx`/`mapy`(`BigDecimal`) 필드 추가
- `GET /api/v1/tour-course/{courseId}`(CO4)·`GET /api/v1/tour-course/{courseId}/view`(SH2) 둘 다 `TourCourseShareResponseDto`를 공유해 한 번의 수정으로 동시 적용
- **TourAPI 추가 호출 없음**: `buildCourseResponse()`가 이미 `tourLiveDataService.getAllCandidates()`(PO1, Caffeine 캐시 TTL 6h)로 만든 `summaryMap`을 사용 중이었고, `PoiSummary` 레코드에는 이미 `mapx`/`mapy`가 파싱되어 있었음 — 캐시된 값을 DTO에 노출만 함

### Files Changed (4 files)

- `src/main/java/com/eodegano/cocobackend/dto/TourCourseShareResponseDto.java`
- `src/main/java/com/eodegano/cocobackend/service/TourCourseServiceImpl.java`
- `docs/FEATURES_BACK.md`
- `docs/PRD_BACK.md`

## [0.6.2] - 2026-08-22

### Testing

#### AI 코스 생성(v0.6.1 HTTP 499 처리) 단위/슬라이스 테스트 추가

`generateTourCourse()`(AI 코스 생성 엔트리포인트)가 기존에 테스트 없이 남아 있던 것을 보완. `GroqApiClient`는 목(mock)으로 대체해 실제 Groq 호출 없이 요청/응답 형태와 에러 매핑만 검증.

- `TourCourseServiceImplTest`: 성공 시 저장·응답 DTO 필드 검증, Groq 예외가 감싸지지 않고 그대로 전파되는지, AI가 검증 규칙(날짜 범위)을 위반한 응답을 생성했을 때 `AiCourseGenerationException(RESPONSE_VALIDATION_FAILED)`이 발생하는지 3건 추가
- `TourCourseControllerTest`: `AiCourseGenerationException` 발생 시 실제 HTTP **499** + `data.errorCode`/`retryable`/`finishReason` 바디까지 `GlobalExceptionHandler` 경유로 검증하는 슬라이스 테스트 1건 추가
- AssertJ `catchThrowableOfType(callable, class)` 오버로드가 현재 버전(3.27.7)에서 deprecated라 `catchThrowableOfType(class, callable)`로 사용

### Files Changed (2 files)

- `src/test/java/com/eodegano/cocobackend/service/TourCourseServiceImplTest.java`
- `src/test/java/com/eodegano/cocobackend/controller/TourCourseControllerTest.java`

## [0.6.1] - 2026-08-22

### Added

#### AI 코스 생성 전용 에러 응답 (HTTP 499)

AI(Groq) 코스 생성 플로우에서 발생하는 에러를 일반 400/500과 구분해, 프론트가 "AI 생성 실패" 케이스를 별도로 처리할 수 있도록 전용 에러 체계 도입.

- `AiCourseGenerationException` 신규 추가: `errorCode`(RATE_LIMITED/API_CALL_FAILED/EMPTY_RESPONSE/RESPONSE_PARSE_FAILED/RESPONSE_VALIDATION_FAILED), `retryable`(재시도로 성공 가능성 있는지), `finishReason`(Groq 진단 정보, 있는 경우) 보유
- `GlobalExceptionHandler`에 전용 핸들러 추가 — 표준 코드가 아닌 **499**로 응답, `ApiResponse.data`에 `AiErrorDetail`(errorCode/retryable/finishReason) 포함
- `GroqApiClient`: rate limit 소진·API 호출 실패·빈 응답·JSON 파싱 실패를 모두 이 예외로 통일 (파싱 실패 시 v0.6.0에서 추가한 `finishReason`을 그대로 프론트까지 전달)
- `TourCourseServiceImpl.validateAiResponse()`: AI가 생성한 일정의 날짜 범위·타입·contentId 검증 실패도 동일하게 처리
- TourAPI 지역 데이터 없음(400)·DB 저장 실패(500) 등 AI 자체와 무관한 에러는 기존 그대로 유지

### Files Changed (5 files, 2 files added)

- `src/main/java/com/eodegano/cocobackend/exception/AiCourseGenerationException.java` (신규)
- `src/main/java/com/eodegano/cocobackend/dto/AiErrorDetail.java` (신규)
- `src/main/java/com/eodegano/cocobackend/exception/GlobalExceptionHandler.java`
- `src/main/java/com/eodegano/cocobackend/client/GroqApiClient.java`
- `src/main/java/com/eodegano/cocobackend/service/TourCourseServiceImpl.java`

## [0.6.0] - 2026-08-22

### Fixed

#### `openai/gpt-oss-20b` reasoning 모델 특성으로 인한 AI 응답 파싱 실패 수정

v0.5.12에서 `openai/gpt-oss-20b`로 전환한 뒤 운영에서 간헐적으로 `Failed to parse AI response: No content to map due to end-of-input` 에러 발생. Groq API 호출 자체는 성공(`choices` 비어있지 않음)했는데 파싱 단계에서만 실패.

- **원인**: 기존 `llama-3.1-8b-instant`와 달리 `openai/gpt-oss-20b`는 reasoning 모델이라, 최종 답변을 만들기 전에 내부 추론(CoT) 토큰을 먼저 소비함. `reasoning_effort`/`reasoning_format`을 지정하지 않아 기본값(medium)으로 동작했고, 후보 풀이 크거나 복잡한 요청에서는 추론이 `max_tokens`(4000) 예산을 다 소진해 최종 `content`가 빈 문자열(`""`)로 반환됨 — Jackson이 빈 문자열을 파싱하려다 `MismatchedInputException`을 던짐.
- `GroqApiRequestDto`에 `reasoning_effort: "low"` 추가: 코스 생성은 후보 목록에서 정해진 JSON 스키마로 배치하는 단순 작업이라 깊은 추론이 불필요 — 추론 토큰 소모를 최소화해 `max_tokens` 예산을 답변 생성에 더 쓸 수 있도록 함
- `reasoning_format: "parsed"` 추가: reasoning을 `content`와 분리된 필드로 받아, reasoning 텍스트(`<think>` 등)가 `content`에 섞여 JSON 파싱을 깨뜨릴 가능성을 원천 차단
- `GroqApiResponseDto`에 `Choice.finish_reason`, `Usage`(prompt_tokens/completion_tokens/total_tokens) 캡처 추가

### Added

#### Groq API 호출 진단 로깅

- 호출 성공 시마다 `finishReason`·`promptTokens`·`completionTokens`·`totalTokens`를 INFO 로그로 기록 (`GroqApiClient.logUsage`)
- `finish_reason=length`(응답이 `max_tokens`에 걸려 잘림)인 경우 별도 WARN 로그로 강조
- AI 응답 파싱 실패 시 에러 메시지만 남기던 것을, `finishReason`·`content` 길이도 함께 남기도록 개선 — 향후 동일 장애 발생 시 원인(토큰 부족 vs 그 외)을 로그만으로 즉시 판별 가능

### Files Changed (4 files)

- `src/main/java/com/eodegano/cocobackend/client/GroqApiClient.java`
- `src/main/java/com/eodegano/cocobackend/dto/GroqApiRequestDto.java`
- `src/main/java/com/eodegano/cocobackend/dto/GroqApiResponseDto.java`
- `docs/func/FEAT_TOURCOURSE_GEN.md`

## [0.5.12] - 2026-08-19

### Fixed

#### Groq API `llama-3.1-8b-instant` 모델 단종에 따른 여행 코스 생성 기능 장애 수정

Groq가 2026-08-16부로 `llama-3.1-8b-instant` 모델을 단종하며 이후 해당 모델로의 요청은 더 이상 서빙되지 않는다고 공지. 여행 코스 생성(`GroqApiClient`)이 이 모델명을 하드코딩해서 쓰고 있어 코스 생성 요청이 모두 실패하는 상태였음.

- Groq 공식 권장 대체 모델인 `openai/gpt-oss-20b`로 전환 (`GroqApiClient.MODEL`)
- 요청/응답 DTO, 재시도·JSON 파싱 로직은 모델 독립적이라 변경 없음

### Files Changed (2 files)

- `src/main/java/com/eodegano/cocobackend/client/GroqApiClient.java`
- `docs/func/FEAT_TOURCOURSE_GEN.md`

## [0.5.11] - 2026-08-16

### Fixed

#### POI 좋아요 등록/취소 시 user_poi_like 튜플이 저장되지 않던 문제 수정

운영 배포 후 좋아요 API가 200을 응답하고 `poi_rating.likes` 카운트도 정상 증가하는데, `user_poi_like` 테이블에는 튜플이 생기지(또는 지워지지) 않는 현상이 보고됨.

- 원인: `PoiRatingRepository.incrementLikes`/`decrementLikes`는 `@Modifying(clearAutomatically = true)` 벌크 UPDATE. `UserPoiLike`는 `@IdClass`로 PK를 애플리케이션에서 직접 세팅하는 구조라 `save()` 호출이 Spring Data JPA의 `isNew()` 오판으로 `merge()`를 타면서 즉시 flush되지 않고 영속성 컨텍스트에만 대기함. 이 상태에서 `poi_rating` 테이블만 건드리는 벌크 UPDATE가 실행되면 다른 테이블(`user_poi_like`)의 대기 변경은 auto-flush 대상에 포함되지 않고, 곧바로 `clearAutomatically=true`가 영속성 컨텍스트를 비워버려 아직 flush되지 않은 INSERT/DELETE가 DB에 반영되지 못한 채 유실됨. 벌크 UPDATE 자체는 즉시 SQL 실행이라 `likes` 카운트만 정상 반영된 것처럼 보였음
- `PoiLikeServiceImpl.toggleLike`에서 `userPoiLikeRepository.save(...)` → `saveAndFlush(...)`, `delete(...)` 직후 `flush()` 호출을 추가해 벌크 업데이트가 컨텍스트를 비우기 전에 `user_poi_like` 변경사항이 먼저 DB에 반영되도록 수정
- 디버깅용 `log.info`, `// todo 배포후 삭제` 주석 제거

### Testing

- `PoiLikeServiceImplTest`: `save()` 대신 `saveAndFlush()` 호출을 검증하도록 수정, 좋아요 취소 케이스에 `flush()` 호출 검증 추가

### Files Changed (3 files)

- `src/main/java/com/eodegano/cocobackend/controller/PoiController.java`
- `src/main/java/com/eodegano/cocobackend/service/PoiLikeServiceImpl.java`
- `src/test/java/com/eodegano/cocobackend/service/PoiLikeServiceImplTest.java`

## [0.5.10] - 2026-08-16

### Changed

#### `tour_course_user_defined_detail.cost`에 음수 방지 CHECK 제약 추가

전날(0.5.9) 신설한 `cost` 컬럼에 값 범위 제약이 없어 음수가 그대로 저장될 수 있었다. `NULL`은 "프론트가 아직 값을 보내지 않음"이라는 의미로 `resolveCost()` 폴백 로직(저장값 → TourAPI `usefee` → type별 기본값)의 판별 기준으로 쓰이고 있어 `NOT NULL` 전환은 보류하고, 음수만 DB 레벨에서 차단하도록 CHECK 제약만 추가했다.

```sql
ALTER TABLE tour_course_user_defined_detail
    ADD CONSTRAINT chk_tour_course_user_defined_detail_cost
    CHECK (cost IS NULL OR cost >= 0);
```

- `cost`는 계속 `INT NULL` 유지 — `NOT NULL DEFAULT 0`으로 바꾸면 `resolveCost()`가 `storedCost != null`을 "프론트 입력값 있음"으로 오판해 폴백(usefee/기본값)이 항상 무시되므로 채택하지 않음
- 기존 601건 모두 `cost = NULL` 상태라 제약 추가 시 위반 없이 적용됨. 애플리케이션 코드 변경 없음

### Files Changed

- DB 스키마만 변경 (`tour_course_user_defined_detail` 테이블), 애플리케이션 코드 변경 없음

## [0.5.9] - 2026-08-16

### Changed

#### 예산 관련 설계 변경 — 교통비 추정(BU3)·음식점 평균 객단가(BU1) 취소, POI별 비용은 프론트 입력값을 그대로 저장

기획 재검토 결과 이동 관련 비용 계산은 프론트엔드가 전담하기로 했고, 음식점 평균 객단가도 근거 데이터(`food_avg_price`)가 로컬 DB 미저장 원칙으로 사라진 뒤 대체 소스 없이는 정확도를 담보할 수 없다고 판단해 서버 측 산정 로직을 모두 걷어냈다. 대신 프론트가 산정한 POI별 실제 비용을 그대로 받아 저장만 하는 단순한 구조로 전환.

- `tour_course_user_defined_detail`에 `cost INT NULL` 컬럼 추가 (BOQ9 확정 — `budget_override`라는 "오버라이드" 개념 대신 사용자 입력값을 그대로 저장하는 단순 `cost` 컬럼으로 결정)
- `TourCourseUpdateRequestDto.PlaceUpdate.cost` 추가 — `PUT /api/v1/tour-course/{courseId}`로 프론트가 입력한 비용을 저장
- `TourCourseServiceImpl.resolveCost()`가 저장된 `cost`를 최우선으로 사용하도록 변경 (없으면 기존처럼 TourAPI 라이브 `usefee` → type별 기본값 순으로 폴백) — 코스 상세 조회(CO4)·공개 뷰(SH2) 응답에 반영
- BU3(교통비 추정)·BU1(음식점 평균 객단가) 기능 취소 — 관련 계산 로직은 구현하지 않음 (BOQ2·BOQ3·BOQ14 확정)

### Testing

`TourCourseServiceImplTest`의 `PlaceUpdate` 생성자 호출 3곳에 `cost` 인자 추가. 전체 테스트 통과.

### Files Changed (4 files)

- `src/main/java/com/eodegano/cocobackend/domain/TourCourseUserDefinedDetail.java`
- `src/main/java/com/eodegano/cocobackend/dto/TourCourseUpdateRequestDto.java`
- `src/main/java/com/eodegano/cocobackend/service/TourCourseServiceImpl.java`
- `src/test/java/com/eodegano/cocobackend/service/TourCourseServiceImplTest.java`

## [0.5.8] - 2026-08-16

### Changed

#### AI 코스 생성 POI 후보 풀 — TourAPI 수집 방식 개선 (범위 확장 + 속도 개선)

기존 `TourLiveDataService.getAllCandidates()`는 `contentTypeId` 없이 전체 타입을 뭉쳐서 `areaBasedList2`를 페이지당 100건씩 순차 호출하며 최대 2000건까지만 채웠다. 경북 전체 관광데이터(7개 콘텐츠타입 × 23개 시군구)는 2000건보다 훨씬 많을 가능성이 높아, TourAPI 기본 정렬 순서상 앞쪽에 오지 못한 유형·지역은 후보 풀에 아예 들어오지 못했다 — 코스 생성 추천 범위가 좁게 느껴진 원인. 캐시가 6시간 TTL 단일 키(`'all'`)라 만료 후 최초 요청자는 최대 20회 순차 API 호출 지연을 그대로 떠안는 콜드스타트 문제도 있었다.

- **타입별 분리 수집**: `PlaceType`의 7개 `contentTypeId`(12/14/15/28/32/38/39)로 나눠 각각 최대 3000건까지 수집 — 물량이 큰 타입(숙박·음식점)이 소형 타입(행사·레포츠 등)을 후보 풀에서 밀어내지 않도록 함
- **페이지 크기 확대**: `numOfRows` 100 → 300 — 동일 범위를 더 적은 호출로 커버(일일 호출 한도 1000건 절약)
- **페이지·타입 병렬 수집**: `TourApiClient.areaBasedListAll()`이 1페이지로 totalCount를 먼저 확인한 뒤 나머지 페이지를 가상 스레드로 병렬 수집. 신규 `areaBasedListAllByTypes()`가 7개 타입도 서로 병렬로 수집(중첩 `join()`이지만 가상 스레드라 데드락 없음)
- **동시 요청 수 상한 + 재시도**: `TourApiClient.callApi()`에 `Semaphore(4)`로 실제 동시 HTTP 요청 수를 제한하고, 429/5xx 응답에 지수 백오프 재시도(최대 3회) 추가 — 문서화되지 않은 TourAPI 초당 제한에 대한 안전장치이자, Groq 클라이언트에는 있었으나 `TourApiClient`에는 없던 재시도 로직의 비대칭 해소
- `getAllCandidates()`에 `@Cacheable(sync = true)` 추가 — 캐시 미스 시 동시 요청이 중복으로 TourAPI를 호출하지 않도록 방지

**호출 예산 (일일 한도 1000건 기준)**: 후보 캐시 워밍 최악 280건/일(7타입 × 최대 10콜 × 4사이클/일), 나머지 ~720건/일은 PO3·CO1 등 상세조회(`detailCommon2`/`detailIntro2`/`detailInfo2`)용으로 남김. 소형 타입은 3000건 캡 이전에 totalCount로 조기 종료되어 실사용량은 이보다 낮을 것으로 예상.

### Added

#### `PoiCacheWarmupScheduler` — POI 후보 캐시 워밍

실사용자가 콜드 캐시(최초 요청 시 타입별 TourAPI 호출)를 밟지 않도록, 배포 직후와 TTL(6h) 만료 직전에 백그라운드로 후보 캐시를 미리 채운다.

- `ApplicationReadyEvent` 시점에 가상 스레드로 1회 초기 워밍 — CI/CD가 `main` push마다 재배포하는 구조(INF5)라 재배포 직후 첫 사용자가 콜드스타트를 밟는 걸 방지
- `@Scheduled(fixedRate = 5시간50분)`로 TTL 만료 전 evict 후 재조회 — 신규 `TourLiveDataService.evictCandidatesCache()`(`@CacheEvict`) 호출 후 `getAllCandidates()` 재호출. 스케줄러가 별도 빈이라 Spring 프록시를 정상적으로 타 self-invocation 문제 없음
- `CacheConfig`에 `@EnableScheduling` 추가

### Testing

`TourLiveDataServiceTest.getAllCandidatesSuccess`를 타입별 호출 구조(7개 `contentTypeId` 중 1개만 데이터 있고 나머지는 빈 페이지)에 맞춰 수정. 전체 69건 테스트 통과.

### Files Created (1 file)

- `src/main/java/com/eodegano/cocobackend/service/PoiCacheWarmupScheduler.java`

### Files Changed (4 files)

- `src/main/java/com/eodegano/cocobackend/dataMig/service/TourApiClient.java`
- `src/main/java/com/eodegano/cocobackend/service/TourLiveDataService.java`
- `src/main/java/com/eodegano/cocobackend/config/CacheConfig.java`
- `src/test/java/com/eodegano/cocobackend/service/TourLiveDataServiceTest.java`

## [0.5.7] - 2026-08-16

### Fixed

#### `SecurityConfig` — 운영 배포에 맞춰 인가 규칙 정리 (`anyRequest().permitAll()` → `authenticated()`)

테스트 편의를 위해 걸어뒀던 catch-all `anyRequest().permitAll()`을 운영 배포에 맞춰 `authenticated()`로 전환했다. 이 규칙에 걸리는 요청 자체가 없어야 정상이지만(모든 컨트롤러 엔드포인트는 이미 개별 규칙으로 커버됨), 앞으로 신규 엔드포인트가 추가될 때 SecurityConfig에 규칙을 깜빡해도 기본값이 "인증 필요"가 되도록 방어선을 세운 것.

- `docs/FEATURES_BACK.md`·`CHANGELOG.md`(AU/US/PO/CO/SH 각 기능 블록)를 근거로 컨트롤러 5개(Auth/User/Poi/TourCourse/Test) 전체 엔드포인트를 대조 — `anyRequest()` 전환 전 이미 개별 규칙으로 인증 필요/불필요가 모두 정확히 커버되어 있었음(예: `PATCH /api/v1/tour-course/*`는 0.5.5에서 이미 보완됨). 신규로 인가 규칙이 빠져있던 엔드포인트는 없었음
- **`OPTIONS /**` permitAll 추가**: `anyRequest().permitAll()`에 가려 드러나지 않았던 문제 — `authenticated()`로 바꾸면 브라우저가 보내는 CORS preflight(OPTIONS, Authorization 헤더 없음) 요청이 인증 필요 엔드포인트(PATCH/DELETE 등)에서 401로 막혀 프론트 쪽에서 CORS 에러로 보임. Security 필터 체인에서 OPTIONS 메서드는 전 경로 permitAll 처리
- **`/test`(`TestController` 헬스체크) permitAll 추가**: 문서화되지 않았던 배포 확인용 진단 엔드포인트. 민감정보 없이 "서버 정상 동작" 문자열만 반환하므로 인증 없이 curl로 배포 확인 가능해야 함
- **`/actuator/health` permitAll 추가**: `spring-boot-starter-actuator` 의존성은 있으나 `SecurityConfig`에 규칙이 없어 `anyRequest()` 전환 시 함께 막힐 뻔했음. 기본 노출 엔드포인트(UP/DOWN 상태만 반환, 민감정보 없음)라 표준 관례대로 공개

### Files Changed (2 files)

- `src/main/java/com/eodegano/cocobackend/config/SecurityConfig.java`
- `CHANGELOG.md`

## [0.5.6] - 2026-08-15

### Fixed

#### 서비스에서 던진 AccessDeniedException이 403 대신 500으로 응답되던 문제 수정

`TourCourseServiceImpl`의 소유권 검증 5곳(`getCourseDetail`·`deleteCourse`·`assignCourse`·`updateCourseTitle`·`updateCourse`)에서 던지는 `AccessDeniedException`이 `GlobalExceptionHandler`에 전용 핸들러 없이 제네릭 `RuntimeException` 핸들러로 흡수되어 403이 아닌 500 "서버 오류가 발생했습니다"로 응답되고 있었다.

- 원인: `JwtAccessDeniedHandler`(`SecurityConfig`의 `exceptionHandling().accessDeniedHandler(...)`)는 `ExceptionTranslationFilter`가 필터 체인 밖으로 전파된 `AccessDeniedException`만 캐치하는데, 컨트롤러/서비스에서 애플리케이션 코드로 직접 던진 예외는 `DispatcherServlet` 내부에서 `HandlerExceptionResolver`(`@ExceptionHandler` 매칭)가 먼저 소비해버려 필터까지 전파되지 않음 — 즉 Security 필터 자체의 인가 실패(예: `hasRole` 불일치)에서 던져지는 것과, 애플리케이션 코드가 직접 던지는 것은 처리 경로가 다름
- `GlobalExceptionHandler`에 `@ExceptionHandler(AccessDeniedException.class)` 추가, `JwtAccessDeniedHandler`와 동일한 응답 형태(`ApiResponse.of(403, msg, null)`)로 통일

### Testing

- `TourCourseControllerTest`에 "본인 코스가 아님 → 403" 케이스 추가 — 이 예외는 `@WebMvcTest`가 로드하는 `GlobalExceptionHandler`가 처리하므로 `addFilters=false`(Security 필터 생략)와 무관하게 슬라이스 테스트에서도 검증 가능해짐. 기존 테스트 클래스 상단 주석(403은 서비스 단위 테스트에서만 검증 가능하다는 설명)도 함께 정정

### Files Changed (2 files)

- `src/main/java/com/eodegano/cocobackend/exception/GlobalExceptionHandler.java`
- `src/test/java/com/eodegano/cocobackend/controller/TourCourseControllerTest.java`

## [0.5.5] - 2026-08-15

### Added

#### `PATCH /api/v1/tour-course/{courseId}` — 코스 일정 수정 (GBC020)

로그인 사용자가 본인이 저장한 코스의 일정 상세(`schedule`)를 통째로 교체한다. 소유권 확인 후 기존 `TourCourseUserDefinedDetail`을 전량 삭제하고 요청 내용으로 재삽입 — CO1(AI 코스 생성) 응답 검증과 동일한 규칙으로 날짜 범위·`contentId`·`type`을 검증한다.

- 요청 바디의 `contentName`·`thumbnailImg`·`operatingHours`·`cost`는 조회 전용 표시 필드라 저장하지 않고 무시(`@JsonIgnoreProperties(ignoreUnknown=true)`) — 응답 시 TourAPI 라이브 조회로 재조립(`TourCourseAiResponseDto.PlaceVisit`과 동일 전략)
- 날짜는 코스 `startDate`~`endDate` 범위, `contentId`는 TourAPI 라이브 후보(캐시) 존재 여부, `type`은 `PlaceType` 값인지 검증하고 실패 시 400
- 상세 재삽입 시 Hibernate 기본 flush 순서(Insert가 Delete보다 먼저 실행)로 인해 `uq_course_date_seq_type` 유니크 제약 위반이 발생할 수 있어, `deleteAll()` 직후 명시적으로 `flush()`를 호출해 삭제를 먼저 커밋
- `SecurityConfig`에 `PATCH /api/v1/tour-course/*` 인가 규칙 누락 발견 — 기존에는 `/title`·`/assign`처럼 별도 하위 경로만 인증을 요구했고 `/{courseId}` 자체는 catch-all `permitAll()`로 흘러들어가 미인증 사용자도 타인 코스 일정을 수정할 수 있는 상태였음. `hasAnyRole("USER","ADMIN")` 규칙 추가로 해결
- `TourCourseUpdateRequestDto`(`schedule[].date`·`places[].seq`·`time`·`type`·`contentId`·`durationMinutes`) 신규, `TourCourseService.updateCourse()`/`TourCourseServiceImpl.updateCourse()` 신규, `TourCourseController`에 `@PatchMapping("/{courseId}")` 추가

### Testing

#### 코스 수정 단위 테스트 + 컨트롤러 슬라이스 테스트 추가

`TourCourseServiceImpl.updateCourse()`에 JUnit5+Mockito 단위 테스트 7건, `TourCourseController`에 `@WebMvcTest` 기반 컨트롤러 슬라이스 테스트 4건을 추가했다.

- `TourCourseServiceImplTest`: 성공(기존 일정 삭제+재삽입+재조립 응답 검증), 코스/사용자 미존재(404), 타인 코스(403), 날짜 범위 초과·잘못된 type·존재하지 않는 contentId(400)
- `TourCourseControllerTest`: 성공 200, `schedule` 누락 시 400, 코스 미존재 404, 비즈니스 검증 실패 400 — `addFilters=false`로 서블릿 필터 체인을 생략하는 만큼 403(`AccessDeniedException`)은 서비스 단위 테스트에서만 검증(슬라이스 테스트에서는 Security의 예외 변환이 빠져 일반 `RuntimeException` 핸들러로 흘러 500이 되므로 대상 밖 — 기존 `PoiControllerTest`와 동일한 관례)

### Files Created (3 files)

- `src/main/java/com/eodegano/cocobackend/dto/TourCourseUpdateRequestDto.java`
- `src/test/java/com/eodegano/cocobackend/service/TourCourseServiceImplTest.java`
- `src/test/java/com/eodegano/cocobackend/controller/TourCourseControllerTest.java`

### Files Changed (6 files)

- `src/main/java/com/eodegano/cocobackend/config/SecurityConfig.java`
- `src/main/java/com/eodegano/cocobackend/controller/TourCourseController.java`
- `src/main/java/com/eodegano/cocobackend/service/TourCourseService.java`
- `src/main/java/com/eodegano/cocobackend/service/TourCourseServiceImpl.java`
- `docs/FEATURES_BACK.md`
- `docs/PRD_BACK.md`

## [0.5.4] - 2026-08-15

### Fixed

#### 여행 코스 생성 응답에 관광지명(`contentName`) 필드 누락 수정

`POST /api/v1/tour-course` 응답의 `PlaceInfo`는 썸네일·운영시간·비용은 포함하면서 정작 관광지 제목이 빠져 있었다. 공유뷰(`GET /{courseId}`)의 `PlaceInfo.placeName`에서 이미 쓰던 `titleOf(summaryMap, contentId)` 헬퍼(`PoiSummary.title()` 반환)를 그대로 재사용해 생성 응답에도 채운다.

- `TourCourseGenerateResponseDto.PlaceInfo`에 `contentName` 필드 추가
- `TourCourseServiceImpl.buildGenerateResponse()`에서 `titleOf(...)`로 값 채움

### Files Changed (2 files)

- `dto/TourCourseGenerateResponseDto.java`
- `service/TourCourseServiceImpl.java`

## [0.5.3] - 2026-08-15

### Added

#### `GET /api/v1/poi/{contentId}` — POI 상세 통합 조회 (GBC018)

`contentId` 기반으로 TourAPI `detailCommon2`(공통 정보) + `detailInfo2`(유형별 반복정보)를 라이브 호출해 단일 응답으로 통합 반환한다. 인증 불필요(비로그인 허용).

- 원 설계는 로컬 `DetailCommon`/`DetailInfo` 엔티티 조인 및 `food_avg_price` 조인을 전제했으나, 두 테이블 모두 v0.5.0에서 공모전 규정(관광정보 로컬 DB 미저장)에 따라 삭제된 상태 — `TourApiClient.detailCommon`/`detailInfo` 라이브 호출로 대체
- `avgPrice`는 근거 테이블 소실(BOQ14 미확정)로 `GET /api/v1/poi`(PO2)와 동일하게 항상 `null` 반환
- 신규 `poiFullDetail` Caffeine 캐시(TTL 6h) 추가 — 기존 `poiDetail` 캐시(운영시간/비용 2필드 전용)와 별도로 분리
- TourAPI에 존재하지 않는 contentId → `NoSuchElementException` → `GlobalExceptionHandler`가 404로 매핑
- `PoiDetailService`/`PoiDetailServiceImpl` 신규, `TourLiveDataService.getFullDetail()` 신규, `PoiController`에 `@GetMapping("/{contentId}")` 추가
- `PoiDetailResponseDto`(`contentId`·`contentTypeId`·`title`·`tel`·`homepage`·`overview`·`firstimage`·`firstimage2`·`addr1`·`addr2`·`mapx`·`mapy`·`avgPrice`·`infoList`), `PoiInfoItemDto`(`infoname`·`infotext`) 신규

### Testing

#### POI 기능 단위 테스트 + 컨트롤러 슬라이스 테스트 추가

`PoiController`/`PoiCurationServiceImpl`/`PoiDetailServiceImpl`/`PoiLikeServiceImpl`/`TourLiveDataService`에 JUnit5+Mockito 단위 테스트 25건, `@WebMvcTest` 기반 컨트롤러 슬라이스 테스트 7건을 추가했다. 기존 25건 포함 전체 57건 통과.

- `PoiCurationServiceImplTest`: 필수 파라미터 검증, sigunguCode 정규화, contentTypeId 필터, 빈 결과 처리, avgPrice null 고정(BOQ14)
- `PoiDetailServiceImplTest`: GBC018 응답 매핑, 404(`NoSuchElementException`), avgPrice null 고정(BOQ14)
- `PoiLikeServiceImplTest`: 좋아요 추가(신규/기존 poi_rating)·취소·사용자 미존재
- `TourLiveDataServiceTest`: `TourApiClient`를 spy로 감싸 HTTP 호출 메서드만 스텁하고 실제 JSON 파싱 로직(`getFullDetail`/`getDetail`/`getAllCandidates`)은 그대로 실행 — 기존에 전혀 테스트되지 않던 핵심 파싱 로직 회귀 안전망 확보
- `PoiControllerTest`: `@WebMvcTest` + `addFilters=false`로 서블릿 필터 체인은 생략하고 Controller→GlobalExceptionHandler→JSON 응답까지 검증. Filter 빈(`JwtAuthenticationFilter`)이 컨텍스트에 함께 로드되어 `JwtProvider`를 `@MockitoBean`으로 채워 해결
- `docs/PRD_BACK.md` BOQ15, `docs/FEATURES_BACK.md` INF6 신규: DB(Testcontainers)·TourAPI(WireMock) 연동 실통합 테스트는 인프라 부재로 보류, TODO로 기록 (`TourApiClient`가 `RestClient`를 필드에서 직접 생성해 현재는 가로챌 수 없음)

### Files Created (5 files)

- `src/test/java/com/eodegano/cocobackend/controller/PoiControllerTest.java`
- `src/test/java/com/eodegano/cocobackend/service/PoiCurationServiceImplTest.java`
- `src/test/java/com/eodegano/cocobackend/service/PoiDetailServiceImplTest.java`
- `src/test/java/com/eodegano/cocobackend/service/PoiLikeServiceImplTest.java`
- `src/test/java/com/eodegano/cocobackend/service/TourLiveDataServiceTest.java`

### Files Changed (2 files)

- `docs/FEATURES_BACK.md`
- `docs/PRD_BACK.md`

## [0.5.2] - 2026-08-08

### Added

#### GitHub Actions + systemd 기반 CI/CD 배포 파이프라인

오라클 클라우드 Free Tier 백엔드 인스턴스(Ubuntu, E2.1.Micro)에 대한 자동 배포 파이프라인을 구축했다. DB 서버와는 동일 VCN 내 프라이빗 IP로 통신하며, 공인 IP·별도 인증서 교환 없이 OCI Security List + 계정 기반 인증만으로 연결한다.

- `main` 브랜치 push 시 Gradle 빌드 → jar를 SCP로 서버 전송 → 서버의 `.env`를 GitHub Secrets 값으로 재생성 → `systemctl restart`로 재기동
- 앱 런타임 시크릿(`DB_HOST`·`DB_PASSWORD`·`JWT_SECRET` 등)은 서버에 수동으로 두지 않고 GitHub Secrets를 원본(source of truth)으로 삼아 배포 시마다 서버 `.env`(`chmod 600`)를 덮어씀 — 값 교체 시 서버 SSH 접속 없이 Secrets만 수정하면 반영됨
- 서버 SSH 접속용 키는 인스턴스 관리용 오라클 발급 키와 분리된 전용 배포 키(ed25519) 사용
- 1GB RAM 환경 고려해 JVM 힙 제한(`-Xmx400m`) 및 스왑 2GB 추가

### Files Created (2 files)

- `.github/workflows/deploy.yml` — CI/CD 워크플로우 (build → scp jar → write env → restart)
- `deploy/cocobackend.service` — systemd 유닛 템플릿

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

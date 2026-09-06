# 백엔드 코드 리뷰 — 할 일 (2026-09-05)

`/code-review high src/main/java/com/eodegano/cocobackend` 결과. 버그·보안·효율성
관점에서 발견된 항목을 우선순위별로 정리. **상태는 각 항목 작업 시 직접 갱신할 것.**

상태 표기: 🔲 미착수 / 🔄 진행중 / ✅ 완료

---

## 우선순위 1 — 보안 (인증/인가 우회)

### ✅ 1. 카카오 OAuth 이메일 일치만으로 계정 자동 연결 (계정 탈취 가능) — 2026-09-06 수정 완료

- **파일**: `src/main/java/com/eodegano/cocobackend/service/KakaoOAuthService.java:48`
- **문제**: 카카오 로그인 시 카카오 프로필 이메일과 일치하는 로컬 계정이 있으면, 이메일
  인증 여부·비밀번호 확인 없이 그대로 `linkKakao(providerId)`로 연결하고 JWT를 발급함.
- **실패 시나리오**: 공격자가 피해자의 로컬 계정 이메일과 동일한 이메일을 가진 카카오
  계정으로 로그인 콜백을 호출하면, `registerKakaoUser`가 이메일만으로 피해자 계정을
  찾아 공격자의 카카오 providerId를 연결하고, 이후 공격자에게 피해자 계정의 유효한
  JWT가 발급됨.
- **수정 방향**: 카카오 계정 정보에서 이메일 인증 여부(`is_email_verified` 등)를 함께
  확인하거나, 자동 연결 대신 로컬 계정 비밀번호 확인 단계를 거치도록 변경. 또는 이메일
  일치 자동 연결 자체를 제거하고 신규 계정으로만 가입 처리.
- **적용한 수정**: `is_email_valid && is_email_verified`가 모두 true일 때만 이메일 일치로
  자동 연결(`KakaoOAuthService.registerKakaoUser`). 미인증/미제공 이메일은 기존 계정 조회
  자체를 하지 않고 합성 이메일(`kakao_<providerId>@kakao.local`)로 항상 별도 신규 계정
  생성. 정상 사용자의 미인증 이메일 케이스는 계정이 영구 분리되는 트레이드오프가
  있음(BOQ19로 고도화 TODO 기록, 당장 착수 안 함). 테스트:
  `KakaoApiClientTest`, `KakaoOAuthServiceTest`. 상세: `docs/PRD_BACK.md` BOQ6/BOQ19.

### ✅ 2. 회원 정보 API에 소유권(본인 확인) 검증 누락 (IDOR) — 2026-09-06 수정 완료

- **파일**: `src/main/java/com/eodegano/cocobackend/controller/UserController.java:34`
  (조회/닉네임 수정/비밀번호 변경/탈퇴 전체)
- **문제**: `SecurityConfig`는 `/api/v1/user/{userId}`에 대해 USER/ADMIN 역할만
  검사하고, 경로의 `userId`가 인증된 본인인지는 검증하지 않음. 서비스 계층
  (`UserServiceImpl`)도 대상 유저 존재 여부(`findActiveUser`)만 확인하고 호출자와
  대조하지 않음.
- **실패 시나리오**: USER 권한을 가진 아무 로그인 사용자나 자신의 유효한 JWT로
  `GET /api/v1/user/{다른유저ID}`(정보 조회), `PATCH .../nickname`(개명),
  `DELETE /api/v1/user/{다른유저ID}`(탈퇴)를 호출하면 검증 없이 그대로 실행됨.
  **단, `PATCH .../password`는 예외** — `UserUpdatePasswordRequestDto.currentPassword`가
  필수값이고 `updatePassword()`가 대상 계정의 현재 비밀번호와 일치하는지 검증하므로,
  피해자의 현재 비밀번호를 이미 알고 있지 않은 한 이 엔드포인트로는 비밀번호를 바꿀
  수 없음(조회·개명·탈퇴 3개만 순수 IDOR로 뚫림).
- **수정 방향**: 컨트롤러 또는 서비스 계층에서 `authentication.getName()`(또는 인증
  주체의 userId)과 경로 파라미터 `userId`가 일치하는지 검증 후 불일치 시 403 반환.
  ADMIN 역할은 예외 허용 여부를 별도로 결정.
- **적용한 수정**: `UserController` 4개 메서드 모두 `Authentication` 파라미터를 받아
  `authentication.getName()`(JWT로 검증된 이메일)과 ROLE_ADMIN 여부를 서비스로 전달.
  `UserServiceImpl.verifyOwnership()`이 `findActiveUser()`로 조회한 대상 유저의
  이메일과 대조해 불일치 시(ADMIN 제외) `AccessDeniedException` → 403. `TourCourseServiceImpl`의
  기존 소유권 검증 패턴과 동일한 방식이되, User 엔티티 자체에 이메일이 있어 추가 조회
  없이 비교. `updatePassword`는 원래도 `currentPassword` 검증으로 실질적 방어가
  됐지만 일관성을 위해 동일하게 적용. 테스트: `UserServiceTest`(15건, 성공/실패/공격
  시나리오 포함).

---

## 우선순위 2 — 정확성 버그

### 🔲 3. TourAPI 재시도 소진 시 장애와 "결과 없음"이 구분 안 됨

- **파일**: `src/main/java/com/eodegano/cocobackend/dataMig/service/TourApiClient.java:246`
- **문제**: `MAX_API_RETRIES` 소진 후 `callApi`가 로그만 남기고 빈 `ObjectNode`를
  반환. 호출부(`extractItems`/`getTotalCount`/`getAllCandidates`)는 이를 "0건"과
  동일하게 처리.
- **실패 시나리오**: TourAPI가 5xx/429를 연속 반환하는 장애 상황에서, 사용자에게는
  실제로는 장애인데 "해당 지역/타입 데이터 없음"으로 보임 — 장애 감지·알림이 불가능.
- **수정 방향**: 재시도 소진 시 빈 결과 대신 전용 예외(예: `TourApiUnavailableException`)를
  던지거나, 결과 DTO에 `success`/`degraded` 플래그를 추가해 호출부가 장애와 빈 결과를
  구분할 수 있게 함.

### 🔲 4. POI 상세 조회 시 실제 contentTypeId 대신 AI/클라이언트가 준 type 라벨로 추정

- **파일**: `src/main/java/com/eodegano/cocobackend/service/TourCourseServiceImpl.java:372-381`
  (`buildDetailMap`), 실제 변환 호출은 375줄, 변환 로직 정의는
  `resolveContentTypeId()`(383-389줄). 검증 로직: `validateAiResponse`/`validateUpdateRequest`
- **문제**: `contentId`의 실제(권위 있는) contentTypeId를 조회하지 않고, AI 응답 또는
  클라이언트 PATCH 요청의 `type` 필드를 `resolveContentTypeId()`로 변환해 그대로
  상세 조회에 사용함. 검증은 `contentId`가 알려진 id 집합에 있는지만 확인하고
  `type`이 실제 POI 타입과 일치하는지는 확인하지 않음.
- **실패 시나리오**: Groq가 식당(contentId=555, 실제 FOOD)을 CULTURE로 잘못
  라벨링하거나, 클라이언트가 PATCH로 `contentId=555, type=CULTURE`를 보내면,
  `getDetail(555, CULTURE용 contentTypeId)`가 호출되어 잘못된/누락된
  운영시간·기본 비용이 응답에 노출됨.
- **수정 방향**: `contentId` 기준으로 실제 contentTypeId를 조회(또는 캐시)해서
  요청/응답의 `type`과 대조 검증하고, 불일치 시 검증 실패로 처리하거나 실제
  contentTypeId를 우선 사용하도록 변경.

### 🔲 5. `TourLiveDataService.getDetail` 캐시 키가 contentTypeId를 반영하지 않음

- **파일**: `src/main/java/com/eodegano/cocobackend/service/TourLiveDataService.java:68`
- **문제**: `@Cacheable(key = "#p0")`로 `contentId`만 캐시 키로 사용하지만, 실제 반환값은
  `contentTypeId`에 따라 달라짐(위 4번 이슈와 결합 시 특히 위험).
- **실패 시나리오**: 동일 `contentId`가 한 번은 contentTypeId=12(관광지)로, 이후
  (4번 버그 등으로) contentTypeId=39(음식점)로 조회되면, 두 번째 호출이 TourAPI를
  재조회하지 않고 첫 번째 캐시값을 그대로 반환해 TTL(6시간) 동안 잘못된
  운영시간/비용이 서빙됨.
- **수정 방향**: 캐시 키를 `#p0 + '_' + #p1`(contentId+contentTypeId 조합)으로 변경.

### 🔲 6. `assignCourse` 동시 요청 시 소유권 배정 유실 가능 (lost update)

- **파일**: `src/main/java/com/eodegano/cocobackend/service/TourCourseServiceImpl.java:173`
- **문제**: `userId == null` 체크 후 `course.assignUser(...)`를 호출하는데, 낙관적 락
  (`@Version`)도 DB 제약도 없어 동시 트랜잭션 간 경합을 막지 못함.
- **실패 시나리오**: 동일한 비로그인(익명) 코스에 대해 두 요청이 거의 동시에
  `PATCH /{courseId}/assign`을 호출하면 둘 다 `userId == null` 체크를 통과하고,
  나중에 커밋되는 트랜잭션이 앞선 배정을 아무 에러 없이 덮어씀.
- **수정 방향**: `TourCourseUserDefined`에 `@Version` 컬럼 추가(낙관적 락)로 두 번째
  커밋 시 `OptimisticLockException` 유발 → 409 등으로 응답. 또는 `UPDATE ... WHERE
  user_id IS NULL` 조건부 업데이트로 영향 행 수 확인 후 0이면 실패 처리.

### 🔲 7. Groq 응답에 `type` 누락 시 미처리 NPE (의도된 예외 우회)

- **파일**: `src/main/java/com/eodegano/cocobackend/service/TourCourseServiceImpl.java:727`
- **문제**: `PlaceType.valueOf(place.getType())`은 `type`이 `null`이면
  `IllegalArgumentException`이 아니라 `NullPointerException`을 던지는데, 주변
  catch는 `IllegalArgumentException`만 잡음.
- **실패 시나리오**: Groq JSON 응답에서 특정 place의 `type` 필드가 누락/null이면
  (DTO 레벨 검증 없음) NPE가 catch를 벗어나 의도된
  `AiCourseGenerationException(RESPONSE_VALIDATION_FAILED)`(재시도 가능) 대신
  미처리 500으로 노출됨.
- **수정 방향**: catch 절에 `NullPointerException`도 포함하거나, `valueOf` 호출 전
  `place.getType() == null` 사전 체크로 명시적 검증 실패 처리.

### 🔲 8. Groq 응답에 `date` 누락 시 미처리 NPE

- **파일**: `src/main/java/com/eodegano/cocobackend/service/TourCourseServiceImpl.java:721`
- **문제**: `day.getDate().isBefore(startDate)` 호출에 null 체크나 try/catch가 없음.
- **실패 시나리오**: Groq 응답의 특정 `DailyPlan`에서 `date` 필드가 누락되면
  `day.getDate()`가 `null`을 반환하고 `.isBefore(...)` 호출에서 미처리 NPE로 500이
  발생함(7번과 동일하게 의도된 검증 실패 경로를 우회).
- **수정 방향**: 7번과 함께 처리 — `date == null`을 명시적으로 검증 실패로 처리하거나
  이 블록 전체를 검증 전용 try/catch로 감싸 `RESPONSE_VALIDATION_FAILED`로 통일.

### 🔲 9. Groq 응답 `message` null 시 파싱 try 블록 밖에서 NPE

- **파일**: `src/main/java/com/eodegano/cocobackend/client/GroqApiClient.java:198`
- **문제**: `choice.getMessage().getContent()` 호출이 `AiCourseGenerationException`으로
  감싸는 try 블록보다 앞/밖에 있어, `message`가 null이면 그 예외 처리를 거치지 못함.
- **실패 시나리오**: Groq가 콘텐츠 필터링 등으로 `choices[0].message`가 null인 응답을
  반환하면, 기존 코드는 `choices`가 비어있지 않은지만 확인하므로 이 라인에서 바로
  NPE가 발생해 의도된 `RESPONSE_PARSE_FAILED` 대신 미처리 크래시로 이어짐.
- **수정 방향**: `message`/`content`에 대한 null 체크를 `choices` 빈 값 체크와 같은
  위치(예외 처리 진입 이전)에서 함께 수행.

---

## 우선순위 3 — 효율성

### 🔲 10. `buildDetailMap`이 장소별 POI 상세를 순차 호출 (배치/병렬 미적용)

- **파일**: `src/main/java/com/eodegano/cocobackend/service/TourCourseServiceImpl.java:372`
- **문제**: 코스에 포함된 장소 각각에 대해 `tourLiveDataService.getDetail(...)`을
  순차 루프로 호출함. `TourApiClient.areaBasedListAllByTypes`가 이미 사용 중인
  동시 호출(가상 스레드 + `Semaphore(4)`) 패턴을 여기서는 활용하지 않음.
- **실패 시나리오**: 장소 20~28개짜리 코스에서 캐시가 비어있으면
  `generateTourCourse`/`getCourseDetail`/`getShareView`/`updateCourse` 호출마다
  그만큼의 순차 블로킹 HTTP 요청이 발생 — 한 번의 코스 조회가 병렬 배치 1회 대신
  N회 왕복으로 늘어남.
- **수정 방향**: `TourApiClient`의 가상 스레드 + `Semaphore` 패턴을 재사용하거나,
  `CompletableFuture`로 장소별 `getDetail` 호출을 병렬화. TourAPI 일일 호출한도
  보호를 위해 동시성 제한(세마포어)은 유지.

---

## 작업 순서 제안

1. 보안 1·2번 (계정 탈취/IDOR) — 최우선
2. 정확성 7·8·9번 (미처리 NPE, 수정 난이도 낮고 리스크 높음)
3. 정확성 4·5번 (contentTypeId 불일치, 서로 연관되어 있어 함께 수정 권장)
4. 정확성 3·6번 (장애 구분, 동시성 락)
5. 효율성 10번

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

경북 CoCo 백엔드 - Spring Boot 4.0 기반 여행 정보 REST API 서버
- Java 25, Gradle 빌드 시스템
- MariaDB + JPA/Hibernate, MyBatis 혼용
- JWT 기반 Stateless 인증/인가
- 한국관광공사 TourAPI **라이브 연동** (v0.5.0부터 로컬 DB 미저장 원칙 — 공모전 의무사항)

> **⚠️ v0.5.0 아키텍처 전환**: 공모전 규정상 관광 정보를 로컬 DB에 적재해 사용할 수 없어, TourAPI를 매 요청 라이브 호출하는 구조로 전환했다. 관광지 원본 데이터(`tour`/`attraction`/`culture`/`event`/`tour_course`/`leports`/`accommodation`/`shopping`/`food`/`detail_common`/`detail_info` 및 하위 상세 테이블)는 모두 DB에서 제거됨. 응답 속도·TourAPI 호출한도 보호를 위해 Caffeine 인메모리 캐시(TTL 6시간)를 경유한다. 별점(`stars`)·좋아요(`likes`)만 앱 자체 데이터라 `poi_rating` 테이블에 남아 있다. 상세: [PRD_BACK.md](PRD_BACK.md), [FEATURES_BACK.md](FEATURES_BACK.md).

## Build & Run Commands

### 빌드 및 실행
```bash
# 빌드
./gradlew build

# 빌드 without 테스트
./gradlew build -x test

# 애플리케이션 실행
./gradlew bootRun

# JAR 생성 후 실행
./gradlew bootJar
java -jar build/libs/cocoBackend-0.0.1-SNAPSHOT.jar
```

### 테스트
```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "com.eodegano.cocobackend.service.UserServiceTest"

# 특정 테스트 메서드 실행
./gradlew test --tests "com.eodegano.cocobackend.service.UserServiceTest.join_성공"

# 테스트 결과 보고서: build/reports/tests/test/index.html
```

### 개발 도구
```bash
# 의존성 트리 확인
./gradlew dependencies

# 빌드 캐시 클리어
./gradlew clean
```

## Architecture

### 계층 구조 (Layered Architecture)

```
Controller (REST API)
    ↓
Service (비즈니스 로직)
    ↓
Repository (JPA) / MyBatis Mapper (SQL)
    ↓
Database (MariaDB)
```

**중요**: Controller와 Service 사이에 DTO로 변환, Entity는 외부 노출 금지

### 패키지 구조

```
com.eodegano.cocobackend/
├── config/          - Spring Security, 빈 설정
├── controller/      - REST 엔드포인트 (@RestController)
├── service/         - 비즈니스 로직, 트랜잭션 관리
├── domain/          - JPA 엔티티 (@Entity)
├── dto/             - 요청/응답 데이터 전송 객체
├── repository/      - JPA Repository 인터페이스
├── security/        - JWT 인증/인가 필터, Provider
├── cache/           - Caffeine 캐시 설정 (지역 POI 후보 리스트·POI 상세, TTL 6h)
└── dataMig/         - TourAPI 라이브 연동 클라이언트 (`TourApiClient`). v0.5.0부터 로컬 DB 적재용 배치(`DataMigrationService`/`DataMigrationController`)는 제거되고, 요청 시점 라이브 호출 전용으로 재사용됨
```

### 인증/인가 플로우

**JWT Stateless Authentication**:
1. 로그인 → `AuthController.login()` → `AuthService.login()`
2. `AuthenticationManager`로 자격증명 검증
3. AccessToken(15분) + RefreshToken(7일) 발급
4. RefreshToken은 DB에 저장 (User별, Provider별 1개씩)
5. 이후 요청마다 `JwtAuthenticationFilter`에서 Bearer 토큰 검증
6. 토큰 갱신 시 RefreshToken 로테이션 (기존 토큰 삭제 후 새 토큰 발급)

**Spring Security 구성** (`SecurityConfig.java`):
- SessionCreationPolicy: `STATELESS`
- Password Encoder: `BCryptPasswordEncoder`
- `JwtAuthenticationFilter` → `UsernamePasswordAuthenticationFilter` 앞에 등록
- 권한 규칙:
  - `/api/v1/auth/**`: permitAll (로그인/로그아웃/토큰갱신)
  - `/api/v1/user/join`: permitAll (회원가입)
  - `/api/v1/user/{userId}`: USER 또는 ADMIN 역할 필요
  - `/api/admin/migration/**`: v0.5.0에서 컨트롤러 자체 삭제 (더 이상 존재하지 않음)

### Soft Delete Pattern

**User 엔티티**:
- `deletedAt` 필드로 논리 삭제 (물리 삭제 X)
- 모든 Repository 쿼리에 `deletedAtIsNull` 조건 포함
- 재가입 시 `deletedAt`을 null로 재설정 (`rejoin()` 메서드)
- 이메일 중복 체크: 삭제되지 않은 유저만 확인

### 환경 변수 설정

`.env` 파일 (루트 디렉토리, Git 제외):
```
DB_HOST=localhost
DB_PORT=3306
DB_NAME=coco_db
DB_USERNAME=your_username
DB_PASSWORD=your_password
JWT_SECRET=your-256bit-random-secret-key-here
TOURAPI_SERVICE_KEY=your-tourapi-key
```

**중요**: JWT secret은 256bit(32자) 이상 랜덤 문자열 필수

### 테스트 환경

`src/test/resources/application.yaml`:
- JPA/Hibernate 자동 설정 제외 (DB 없이 단위 테스트)
- JWT secret은 테스트용 고정 값 사용
- `@MockBean`, `@Mock`으로 의존성 모킹

**테스트 작성 시**:
- Service 계층: 비즈니스 로직 검증, Repository는 Mock
- Controller 계층: `@WebMvcTest` + MockMvc 사용
- Security 계층: `@WithMockUser` 또는 실제 토큰 생성

## Key Design Patterns

### 1. Service Interface + Implementation
```java
UserService (인터페이스)
UserServiceImpl (구현체, @Service)
```
- 테스트 용이성, 의존성 역전 원칙

### 2. DTO 분리 전략
- Request DTO와 Response DTO 명확히 분리
- Validation 어노테이션: `@Valid` + `@NotBlank`, `@Email`, `@Size` 등
- Entity → DTO 변환은 DTO 생성자에서 처리

### 3. Constructor Injection
- `@RequiredArgsConstructor` (Lombok) 사용
- 필드 주입 대신 생성자 주입 권장

### 4. Token Rotation (보안)
- RefreshToken 갱신 시 기존 토큰 삭제 후 새 토큰 발급
- `RefreshToken.rotateToken()` 메서드 사용

## Database Schema

**JPA 설정**:
- `ddl-auto: validate` (스키마 자동 생성 금지, 검증만)
- Physical Naming Strategy: `PhysicalNamingStrategyStandardImpl`
  - `@Column(name="컬럼명")` 명시한 이름 그대로 사용 (snake_case 자동 변환 안함)

**주요 엔티티** (v0.5.0 기준):
- `User`: 회원 (로컬 로그인, 카카오 OAuth 지원)
- `RefreshToken`: JWT Refresh 토큰 (User 1:N)
- `TourCourseUserDefined` / `TourCourseUserDefinedDetail`: 사용자 생성 코스 헤더/일정 상세 (`contentId`는 TourAPI 원본을 가리키는 단순 컬럼, JPA 연관관계 없음)
- `UserPoiLike`: 좋아요 중계 테이블 (userId + contentId 복합키)
- `PoiRating`: **신규(v0.5.0)** — `contentId` PK, `stars`(BigDecimal), `likes`(Integer). 좋아요/평점 액션 시 on-demand 생성. 기존 `Tour.stars`/`Tour.likes`를 대체
- `MstSigungu` / `MstTheme`: 경북 시군구·테마 기준정보 (레포지토리·서비스 미연결이지만 기준정보 목적으로 유지)

**⚠️ 제거된 엔티티 (v0.5.0, 공모전 규정 — 관광 정보 로컬 DB 미저장)**:
`Tour`, `DetailCommon`, `DetailInfo`, `Attraction`, `Culture`, `Event`, `TourCourse`, `TourCourseDetailInfo`, `Leports`, `Accommodation`, `AccommodationDetailInfo`, `Shopping`, `Food`, `FoodAvgPrice`
→ 전부 `TourApiClient` 라이브 호출(`areaBasedList2`/`detailCommon2`/`detailIntro2`/`detailInfo2`) + Caffeine 캐시(TTL 6h)로 대체. DB 테이블은 마이그레이션 시 삭제됨 (백업: 별도 SQL 덤프 보관).

## API Endpoints

### 인증 (`AuthController`)
- `POST /api/v1/auth/login`: 로그인 (AccessToken + RefreshToken 발급)
- `POST /api/v1/auth/logout`: 로그아웃 (RefreshToken DB 삭제)
- `POST /api/v1/auth/reissue`: 토큰 갱신 (RefreshToken으로 새 AccessToken 발급)

### 회원 (`UserController`)
- `POST /api/v1/user/join`: 회원가입
- `GET /api/v1/user/{userId}`: 회원 정보 조회
- `PATCH /api/v1/user/{userId}/nickname`: 닉네임 수정
- `PATCH /api/v1/user/{userId}/password`: 비밀번호 변경
- `DELETE /api/v1/user/{userId}`: 회원 탈퇴 (Soft Delete)

### 데이터 마이그레이션
- **v0.5.0에서 제거됨**: `DataMigrationController`/`DataMigrationService` 삭제. 관광 정보를 로컬 DB에 적재하는 배치 자체가 공모전 규정 위반이라 더 이상 존재하지 않음. `TourApiClient`는 남아 있으나 라이브 조회 서비스의 기반으로만 재사용됨.

## Common Gotchas

1. **Spring Boot 4.0 변경사항**:
   - `spring-boot-starter-webmvc-test` 대신 `spring-boot-starter-webmvc-test` 사용
   - Jakarta EE 네임스페이스 (`jakarta.*`) 사용

2. **JWT 토큰 갱신 시 주의**:
   - RefreshToken은 body에서만 받음 (Authorization 헤더 X)
   - 갱신 성공 시 기존 RefreshToken은 DB에서 삭제됨

3. **Soft Delete 쿼리**:
   - 모든 User 조회는 `findByIdAndDeletedAtIsNull()` 사용
   - 존재 여부 확인: `existsByEmailAndDeletedAtIsNull()`

4. **비밀번호 변경**:
   - 현재 비밀번호 검증 필수 (`passwordEncoder.matches()`)
   - 새 비밀번호는 암호화 후 저장

5. **Hibernate Naming Strategy**:
   - DB 컬럼명이 camelCase라면 `@Column(name="정확한이름")` 명시 필수
   - 자동 변환되지 않음

6. **환경변수 누락 시 에러**:
   - `application.yaml`에서 `${DB_HOST}` 등 참조
   - 로컬 개발: `.env` 파일 생성 필수

7. **TourAPI 라이브 호출 + Caffeine 캐시 (v0.5.0)**:
   - 관광 정보는 DB에 없음 — 매 요청 `TourApiClient`를 통해 TourAPI를 호출하되, Caffeine 캐시(지역 후보 리스트·POI 상세 각각 TTL 6h)를 우선 조회
   - 캐시는 인스턴스 로컬(비영속) — 서버 재시작 시 초기화됨, 다중 인스턴스 배포 시 인스턴스별로 별도 캐시
   - 좋아요/별점은 `poi_rating`에서 관리하며 TourAPI 존재 여부를 라이브로 재검증하지 않음 (프론트가 이미 API로 확인한 contentId를 신뢰)

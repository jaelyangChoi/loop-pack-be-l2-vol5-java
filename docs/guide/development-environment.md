# 개발 환경 가이드

로컬 실행 방법, 빌드·테스트 명령어, 모듈 구조를 정리한 개발자 참고 문서입니다. AI 작업 규칙과 아키텍처 규칙은 루트 `CLAUDE.md`를 따릅니다.

## 1. 로컬 인프라 실행

`local` 프로필로 애플리케이션을 실행하려면 먼저 인프라(MySQL, Redis)를 띄워야 합니다.

```shell
docker-compose -f ./docker/infra-compose.yml up
```

작업이 끝나면 종료합니다.

```shell
docker-compose -f ./docker/infra-compose.yml down
```

모니터링(Prometheus + Grafana)이 필요하면 별도로 실행합니다. 실행 후 http://localhost:3000 (admin/admin) 으로 접속합니다.

```shell
docker-compose -f ./docker/monitoring-compose.yml up
```

모니터링도 사용이 끝나면 종료합니다.

```shell
docker-compose -f ./docker/monitoring-compose.yml down
```

## 2. 빌드 및 테스트 명령어

| 목적 | 명령어 |
|---|---|
| 전체 빌드 | `./gradlew build` |
| 특정 앱 실행 | `./gradlew :apps:commerce-api:bootRun` (`commerce-batch`, `commerce-streamer`도 동일한 패턴) |
| 전체 테스트 | `./gradlew test` |
| 특정 모듈 테스트 | `./gradlew :apps:commerce-api:test` |
| 특정 테스트 클래스 | `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ErrorTypeHttpStatusMapperTest"` |
| 특정 테스트 메소드 | `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ErrorTypeHttpStatusMapperTest.<메소드명>"` |
| 최종 검사 (Checkstyle + 테스트) | `./gradlew :apps:commerce-api:check` |
| 아키텍처(ArchUnit) 테스트만 | `./gradlew :apps:commerce-api:test --tests '*ArchitectureTest'` |
| Jacoco 커버리지 리포트 | `./gradlew :apps:commerce-api:jacocoTestReport` (XML만 생성, HTML/CSV는 비활성화) |

## 3. 멀티 모듈 구조

```
Root
├── apps      (실행 가능한 SpringBootApplication, 서로 독립적)
│   ├── commerce-api        - REST API. domain/application/infrastructure 4계층(user·point 기능)이 구현되어 있음
│   ├── commerce-batch      - 배치 작업(spring-boot-starter-batch). 현재는 데모 Job만 있고 도메인 레이어는 비어있음
│   └── commerce-streamer   - Kafka Consumer 애플리케이션. 현재는 데모 Consumer만 있고 도메인 레이어는 비어있음
├── modules   (특정 구현/도메인에 의존하지 않는 재사용 가능한 설정 + 테스트 환경)
│   ├── jpa    - DataSource/JPA/QueryDSL 설정, BaseEntity
│   ├── redis  - Redis 설정
│   └── kafka  - Kafka 설정
└── supports  (부가 기능 add-on)
    ├── jackson    - Jackson(Kotlin/JSR310) 모듈 설정
    ├── logging    - 로깅, Slack Appender
    └── monitoring - Actuator/Micrometer(Prometheus)
```

## 4. 앱 실행과 포트

세 앱은 서로 독립된 프로세스이지만 포트가 자동으로 분리되어 있지 않아 동시에 띄울 때 주의가 필요합니다.

- `commerce-api`: 웹 서버 기본 포트 `8080`, Actuator(관리) 포트 `8081`(`supports/monitoring`의 `monitoring.yml`에서 고정).
- `commerce-streamer`: 웹 서버 `8080`, Actuator `8081`로 `commerce-api`와 동일합니다. 함께 띄우려면 한쪽에 `--server.port`/`--management.server.port`를 오버라이드합니다.
- `commerce-batch`: `spring.main.web-application-type: none`이라 웹 서버가 없습니다. 배치 Job 하나를 실행하고 종료됩니다.

## 5. 테스트 환경 재사용 (`java-test-fixtures`)

`modules`, `supports`는 `java-library` + `java-test-fixtures` 플러그인으로 테스트 유틸(Testcontainers 부트스트랩, 데이터 클린업 등)을 `src/testFixtures`로 노출하고, `apps`에서 `testImplementation(testFixtures(project(":modules:jpa")))` 형태로 가져다 씁니다.

- `modules:jpa`: `MySqlTestContainersConfig`, `DatabaseCleanUp`(모든 테이블 truncate) 구현 완료.
- `modules:redis`: `RedisTestContainersConfig`, `RedisCleanUp` 구현 완료.
- `modules:kafka`: 플러그인과 의존성만 선언되어 있고 `src/testFixtures`가 없어 Kafka Testcontainers 설정은 직접 구현해야 합니다.

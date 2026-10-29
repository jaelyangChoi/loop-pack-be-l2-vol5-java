# 주문 확정 이벤트와 구매 활동 상세 (W6, AI용)

사람용 [order-event-design.md](order-event-design.md)의 구현 상세다. 코드를 보면 알 수 있는 내용은 적지 않는다.

## 1. 현재 코드 사실 (변경 전)

- CONFIRMED 전이는 `PaymentStatusUpdater.succeed()` 한 곳이다. 진입은 `PaymentResultLookupFacade`(callback, reconcile)의 `updateByStatusLookupResult`뿐이다. `OrderFacade.confirm`의 TX B는 CONFIRMED를 만들지 않는다.
- 종결된 시도의 반복 적용은 `applyToTerminated`로 빠져 `succeed`에 닿지 않는다. 그래서 `succeed`에서 낸 이벤트는 최초 확정에서만 나간다.
- `PaymentResultLookupFacade.syncWithPg`는 `@Transactional` 없이 `writeTransaction.execute(...)`로 `updateByStatusLookupResult`를 감싼다(현재 반환은 `PaymentAttemptInfo`). `execute`가 반환되면 commit이 끝난 상태다.
- 주문 상태는 `DRAFT/PAYMENT_PENDING/CONFIRMED/PAYMENT_FAILED`. `UNKNOWN`은 결제 시도 상태다. `Order.confirmedAt`과 `Clock` 빈은 없다(`BaseEntity`가 `ZonedDateTime.now()` 직접 호출).
- `OrderItem`은 LAZY이고 OSIV는 꺼져 있다(`jpa.yml`). `OrderLines`가 같은 상품 중복을 이미 거절한다.
- 재고는 TX A에서 차감하고 성공 때 다시 건드리지 않는다. consumer는 재고·결제를 건드리지 않는다.
- POINT 확정 경로는 제거됐다. `Order.confirm()`은 테스트만 쓴다. 이전 POINT 확정 주문은 `ConfirmedOrders` fixture가 SQL로 만든다.
- `commerce-api`에는 `modules:kafka` 의존과 kafka 설정이 없다.
- `modules/kafka` 문제:
  - `kafka.yml` consumer의 `value-serializer`는 오기다(`value-deserializer`여야 한다).
  - `properties.enable-auto-commit`은 Kafka가 읽지 않는 키다.
  - `admin.properties.bootstrap.servers`가 `kafka:9092` 고정이다. test·local의 `spring.kafka.bootstrap-servers`는 `localhost:19092`다.
  - producer `acks`·`enable.idempotence` 미지정(`retries=3`).
  - 팩토리 `BATCH_LISTENER`: 배치, MANUAL, 동시성 3, `fetch.min.bytes=1MiB`, `fetch.max.wait.ms=5000`.
  - `src/testFixtures` 소스가 없다.
- `commerce-streamer`: demo consumer만 있고 테스트가 없다. `spring.application.name`이 `commerce-api`다. 도메인 레이어가 비어 있다.
- 버전: spring-kafka 3.3.4, testcontainers kafka 1.20.6, Spring Boot 3.4.4.

## 2. 이벤트 계약

| 항목 | 값 |
|---|---|
| topic | `order-events` (테스트는 고유 suffix) |
| key | `String.valueOf(orderId)`. payload `orderId`와 일치해야 한다 |
| value | JSON bytes (`byte[]`) |
| eventType / schemaVersion | `OrderConfirmed` / `1` |
| eventId | `order-confirmed:v1:{orderId}` (유도값, 재발행·재전달에서 불변) |
| confirmedAt | `Order.confirmedAt` (UTC ISO-8601) |
| items | `[{productId, quantity}]`, quantity ≥ 1, productId 중복 없음 |

```json
{"eventType":"OrderConfirmed","schemaVersion":1,"eventId":"order-confirmed:v1:42",
 "orderId":42,"confirmedAt":"2026-09-07T01:00:00Z",
 "items":[{"productId":7,"quantity":2},{"productId":8,"quantity":1}]}
```

- 카드·PG 비밀·사용자 개인정보를 넣지 않는다. 고객이 호출하는 발행 endpoint를 만들지 않는다.
- 사실 비교는 속성 순서와 items 순서를 무시한다. items를 productId 오름차순으로 정규화(같은 productId는 합산)해 비교한다.
- payload는 `succeed` 안에서 저장된 주문의 품목·수량·`confirmedAt`으로 만든다. 상품 테이블을 다시 조회하지 않는다.

## 3. 발행 조건

| 상황 | 발행 |
|---|---|
| 최초 PG SUCCESS → 주문 CONFIRMED commit | 발행 |
| DRAFT, PAYMENT_PENDING, 시도 UNKNOWN, 시도·주문 FAILED | 안 함 |
| `succeed` 뒤 같은 트랜잭션 rollback | 안 함 (`execute`가 예외를 던져 발행 줄에 닿지 않음) |
| 종결된 시도의 같은 결과 반복 (callback 중복, reconcile 반복) | 안 함 |
| 종결된 시도에 다른 결과 (`CONFLICTING_RESULT`) | 안 함 |
| 이전 POINT 확정 주문 | 안 함 (소급 없음) |

- 발행 흐름: `succeed`가 최초 확정일 때 payload를 만들고 → `updateByStatusLookupResult`가 `PaymentAttemptInfo`와 payload(없을 수 있음)를 결과로 돌려주고 → Facade가 `writeTransaction.execute` 반환 뒤 payload가 있으면 `OrderEventPublisher.publish`를 부른다.
- Spring 이벤트·`@TransactionalEventListener`는 쓰지 않는다. 재조회 없이 트랜잭션 안에서 만든 payload만 쓴다.
- send 실패(동기 예외, 비동기 콜백 오류)는 로그로 남기고 삼킨다. 주문 상태를 바꾸지 않고 PG create를 다시 부르지 않는다. 성공 콜백에서는 topic·partition·offset을 로그로 남긴다.

## 4. commerce-api 변경

| 대상 | 내용 |
|---|---|
| `build.gradle.kts` | `implementation(project(":modules:kafka"))` 추가 |
| 설정 | kafka 설정 import. producer value는 JSON bytes로 직렬화(같은 직렬화·key 사용) |
| `Clock` 빈 | `config`에 `Clock.systemUTC()`. 테스트는 고정 clock으로 교체 |
| `Order.confirmedAt` | `completePayment`에서 최초 전이 때만 설정. DDL은 `ddl-auto` 따름 |
| `PaymentStatusUpdater` | `succeed`가 `confirmedAt` 설정과 payload 생성. `updateByStatusLookupResult`는 `PaymentAttemptInfo`와 payload를 담은 `Result` 타입(`application.payment.dto`)을 반환 |
| `PaymentResultLookupFacade` | `syncWithPg`가 `execute` 반환 뒤 payload가 있으면 포트를 호출. callback·reconcile이 모두 이 메서드를 지난다 |
| application | payload 타입 `OrderConfirmedInfo`(`application.order.dto`), 포트 `OrderEventPublisher` |
| infrastructure | `OrderEventKafkaPublisher`(포트 구현. 직렬화·key·결과 콜백을 숨긴다) |

- ArchUnit: `infrastructure → application`은 application 포트 구현에 한해 허용된다(기존 `ProductListRedisCache`와 같다).
- 반환 타입이 바뀌므로 `updateByStatusLookupResult`를 부르는 기존 테스트(`PaymentIntegrationTest` 등)를 먼저 세어 보고, 10곳을 넘으면 고치기 전에 알린다.

## 5. commerce-streamer 구성

계층은 4계층을 따른다(`interfaces.consumer` → `application` → `domain` ← `infrastructure`).

| 테이블 | 컬럼·제약 |
|---|---|
| `processed_order_confirmation` | `event_id` 유일, `order_id` 유일, `confirmed_at` |
| `product_purchase_activity` | `order_id`, `product_id`, `quantity`, `confirmed_at`, 유일 `(order_id, product_id)` |

- 상품 테이블과 FK를 두지 않는다. productId는 과거 사실의 식별자다. 삭제·비노출 상품을 노출하지 않는다.
- 두 엔티티는 `@Table(name=...)`을 명시한다(`DatabaseCleanUp`이 이름을 읽는다).
- querydsl 5.1.0은 `@Embeddable` record를 처리하지 못한다. 필요하면 `@Embedded` record로 매핑한다.

### 소비 검사 순서

1. 파싱·검증: JSON 형식, `eventType=OrderConfirmed`, `schemaVersion=1`, `quantity ≥ 1`, items 비어 있지 않음, key == `String.valueOf(orderId)`, eventId 형식 `order-confirmed:v1:{orderId}`. 위반이면 예외 → 중단.
2. items 정규화(productId 정렬·합산).
3. DB TX:
   1. eventId 또는 orderId의 완료 기록을 조회한다.
   2. 기록이 있으면 저장된 사실과 비교한다. 같으면 효과 없이 정상 반환, 다르면 충돌 예외.
   3. 없으면 완료 기록 + 모든 품목 활동을 저장한다.
4. 3에서 유일 제약 위반(`DataIntegrityViolationException`)이면 TX를 rollback하고, TX 밖에서 저장된 사실을 조회해 3-2와 같이 비교한다. 다른 예외는 그대로 던진다.
5. handler가 정상 반환한 뒤에만 `ack.acknowledge()`.

비교 대상은 `eventId`, `orderId`, `confirmedAt`, 품목별 `quantity`다. 충돌 종류는 다음과 같다.

| 상황 | 결과 |
|---|---|
| 같은 eventId, 같은 사실 | no-op |
| 같은 eventId, 다른 orderId·수량·확정 시각 | 충돌 |
| 같은 orderId, 다른 eventId | 충돌 (추가 구매 없음) |
| 품목 저장 중 오류 | 전체 rollback, ack 없음 |

- handler는 listener와 **다른 Bean**에 둔다(자기 호출로 트랜잭션을 우회하지 않는다). 트랜잭션 경계는 Facade의 `@Transactional`이다.

## 6. Kafka 설정

| 항목 | 값 |
|---|---|
| 별도 factory | `orderActivityKafkaListenerContainerFactory`. 기존 `BATCH_LISTENER`는 바꾸지 않는다 |
| listener | `batchListener=false`, `concurrency=1`, `AckMode.MANUAL_IMMEDIATE` |
| error handler | `CommonContainerStoppingErrorHandler` (충돌·스키마 위반·DB 오류 모두 중단) |
| consumer value | `ByteArrayDeserializer`, key는 `StringDeserializer` |
| `enable.auto.commit` | 실제 `ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG` 값 false를 테스트로 확인 |
| `auto.offset.reset` | 실험 topic은 `earliest`로 시작 위치를 명시한다 |
| group | `purchase-activity` |
| bootstrap | producer·consumer·admin이 같은 주소 (컨테이너 내부 `kafka:9092`와 호스트 `localhost:19092`를 섞지 않는다) |
| producer | `acks=all`, `enable.idempotence=true`를 명시한다. `retries`와의 호환을 확인한다 |
| 테스트 프로필 | `fetch.min.bytes=1`, `fetch.max.wait.ms`를 작게(예 100ms). 작은 메시지가 5초 늦게 보이는 것을 피하는 이유를 설정 옆에 적는다 |

- `kafka.yml`의 `value-serializer` 오기는 `value-deserializer`로 고쳤다. 기존 `BATCH_LISTENER`의 `ByteArrayJsonMessageConverter`가 `byte[]` 값을 전제하므로 의도한 설정이 이제 적용된다.
- 중단형 handler가 기본이다. 다른 handler를 쓰면 재시도 소진·recover·`ackAfterHandle`까지 설명한다. `commitRecovered=false`만으로 commit이 막힌다고 결론 내리지 않는다.
- streamer의 `spring.application.name`을 `commerce-streamer`로 바로잡았다(client-id 중복 방지).

## 7. 테스트 인프라

- `modules/kafka/src/testFixtures`에 `KafkaTestContainersConfig`를 추가한다. `RedisTestContainersConfig` 패턴을 따라 static 블록에서 컨테이너를 띄우고 `spring.kafka.bootstrap-servers`와 admin bootstrap을 실제 주소로 `System.setProperty` 한다.
- 이미지와 API는 testcontainers 1.20.6과 호환되는 것을 쓴다.
- 고유 topic·group을 쓰고, 준비·배정·소비 대기는 유한 deadline으로 제한한다. 긴 `sleep`으로 덮지 않는다. 다른 실행의 topic·group·DB는 지우지 않는다.
- `commerce-streamer`에는 `testImplementation(testFixtures(project(":modules:kafka")))`, 필요한 Kafka 테스트 의존을 연결한다.
- 테스트 0건, skip, broker 연결 실패는 성공이 아니다.

## 8. 실험

### 8-1. 누락 구간
- 실제 PG 결과 적용 경로(`FakePgServer` + 기존 주문 API)로 주문 2건을 확정한다.
  - 사례 A (미발행): commit 뒤 send 직전 hook에서 중단 또는 실패를 주입한다.
  - 사례 B (정상): 실제 broker·consumer까지 처리한다.
- 확인: 사례 A는 주문 CONFIRMED·재고 차감은 남고, 해당 eventId의 broker record와 구매 활동이 없다. 사례 B는 활동이 있다.
- 고정 clock·품목·수량을 쓰고, 실제 `orderId, providerOrderId, transactionKey, eventId, confirmedAt, items`와 주문·재고·broker·활동 상태를 기록한다. 카드 값·비밀은 제외한다.
- W6 직접 발행 경로를 다시 실행할 수 있게 재현 코드를 보존한다. 실패 주입을 OS 강제 종료라고 표현하지 않는다.

### 8-2. partition·group (partition 2개 격리 topic)
1. 같은 group의 consumer를 1 → 2 → 3개로 바꾸며 실제 assignment가 목표에 도달할 때까지 유한하게 poll하며 기다린다.
2. 기대 배정: 1개 → 두 partition 모두, 2개 → 하나씩, 3개 → 하나 이상은 배정 없음.
3. 두 partition에 명시적으로 test record를 보내 읽히는지 확인한다. 업무 topic과 분리해 가짜 활동을 만들지 않는다.
4. producer가 partition을 고르게 하고 같은 key의 `RecordMetadata`를 비교한다. 같은 key는 같은 partition이어야 한다. 다른 key가 다른 partition으로 간다고 assert하지 않는다.
5. `acks` 선택과 단일 broker로 확인하지 못하는 복제 장애 보장을 글로 남긴다.

### 8-3. 재전달 (low-level KafkaConsumer, 실제 handler 사용)

| 순서 | 행동 | 확인 |
|---|---|---|
| 준비 | 격리 topic에 target 발행, send metadata 확보 | partition, offset `n` |
| 시작 위치 | 첫 consumer 배정 뒤 `n` commit | group committed `n` |
| 첫 처리 | poll → 실제 handler DB commit | 활동·완료 기록 존재 |
| 종료 전 | `n+1` commit을 요청하지 않는다 | committed 여전히 `n` |
| 종료 | auto commit false, 대기 중 commit·종료 callback 없이 bounded close | 종료 |
| 재시작 | 새 consumer, 같은 group. **seek·offset reset 금지** | 같은 위치 `n` 재전달 |
| 두 번째 처리 | handler no-op, 그 뒤 `n+1` commit | 활동 행 수·수량 불변, committed `n+1` |

- 관찰값: 전달 2회, 같은 record 위치, committed offset `n → n → n+1`. committed offset은 admin(`listConsumerGroupOffsets`) 또는 `committed()`로 확인한다. position만으로 판정하지 않는다.
- `n`은 producer metadata에서 구한다. 41·42 같은 값을 고정하지 않는다.

### 8-4. 실제 Spring listener 실패
- 별도 격리 topic·group에서 DB commit 전 오류를 주입한다.
- 확인: 활동·완료 기록 전체 rollback, 실패 record 이후로 committed offset이 진행하지 않음, 컨테이너 중단과 오류 표시. 오류를 제거하고 같은 group으로 컨테이너를 재시작하면 재전달 → 정상 저장 → ack.
- 8-3은 "DB 효과 뒤 offset 공백", 8-4는 "listener 실패·ack 정책"을 본다. handler를 두 번 직접 호출하는 단위 테스트로 둘을 대체하지 않는다.

## 9. 테스트 시나리오

규칙은 가장 낮은 레이어에서 검증하고, 통합·E2E는 연결 여부(성공 하나, 대표 실패 하나)만 본다. 유스케이스당 3~5개를 넘기지 않는다.

### 발행 `OrderConfirmedPublicationTest` (commerce-api)
| 시나리오 | 기대 |
|---|---|
| callback으로 최초 SUCCESS | 이벤트 1건(`OrderEventPublisher` 호출 1회가 아니라 broker에 쌓인 record로 확인). 필드가 저장된 주문의 품목·수량·`confirmedAt`과 같다 |
| reconcile로 최초 SUCCESS | 같은 조건에서 발행 |
| 같은 SUCCESS 반복 (callback + reconcile) | 새 이벤트·추가 재고 차감 없음 |
| PENDING·UNKNOWN·FAILED, 확정 TX rollback | 발행 없음 |
| send 실패 주입 | 주문은 CONFIRMED로 남고 PG create를 다시 부르지 않는다 |

도메인 단위: `Order.completePayment`가 `confirmedAt`을 한 번만 설정한다.

### 소비 `PurchaseActivityIntegrationTest` (commerce-streamer)
| 시나리오 | 기대 |
|---|---|
| 주문 42 (상품 7×2, 8×1) | 활동 2행. 같은 이벤트 재수신 후에도 2행. 주문 43 (상품 7×3) 추가 시 총 3행, 상품 7 합계 5 |
| 같은 eventId에 다른 수량·시각 / 같은 orderId에 다른 eventId | 충돌, 추가 구매 없음 |
| 같은 이벤트 동시 소비 | 유일 제약으로 활동 한 벌만 남는다 |
| 중간 품목 저장 실패 | 완료 기록·앞선 품목 모두 rollback |
| 스키마 위반 (수량 0, key 불일치) | 예외, 저장 없음 |

### 전달 `KafkaDeliveryTest` (commerce-streamer)
8-2, 8-3, 8-4를 각각 한 테스트로 둔다. 전체 흐름은 "실제 PG 확정 → Kafka → streamer"를 한 건 확인한다.

### 회귀
`./gradlew :apps:commerce-api:check :apps:commerce-streamer:check`(Checkstyle, ArchUnit 포함). PG·재고·이전 POINT 사실·소유권·삭제 비노출이 그대로인지 기존 테스트로 확인한다.

## 10. 결정 기록

| 결정 | 대안과 버림 이유 |
|---|---|
| 충돌·스키마 위반도 중단 | 충돌 기록 후 ack는 partition이 막히지 않지만 분기와 테이블이 늘어난다. 과제 예시와 같고 정책이 하나라 중단을 택했다 |
| Facade가 commit 뒤 명시적으로 호출 | Spring 이벤트 + `AFTER_COMMIT`은 캐시 무효화와 일관되지만, 발행 지점과 수신자가 각각 하나라 이득이 작고 commit~send 구간이 코드에서 보이지 않는다. 대가는 확정 경로가 늘면 그 Facade도 발행을 불러야 한다는 점이고, callback·reconcile 각각의 E2E 테스트로 막는다 |
| fingerprint를 저장하지 않음 | 완료 기록과 활동 행을 직접 비교할 수 있어 저장할 값이 늘지 않는다 |
| eventId를 유도값으로 | 따로 저장할 컬럼이 필요 없다. 계약 버전이 바뀌면 `v2`로 구분한다 |

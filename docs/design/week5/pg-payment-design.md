# 주문 결제의 PG 교체 설계 (W5)

> **PG를 기다리는 시간과 PG에서 실제로 생긴 결과를 구분한다. 결과는 PG에 다시 물어 확인한 뒤 주문·재고에 한 번만 반영한다.**

주문 확정의 결제를 포인트에서 로컬 테스트 PG(HTTP)로 교체한다. 구현 상세(상태 전이, 오류 우선순위, 시나리오, 회복 수치)는 [AI용 상세](pg-payment-design.ai.md)에 있다.

## 이번 주의 핵심과 교훈

- **외부 시스템에서는 "모른다"가 하나의 결과다.** timeout은 "내 대기가 끝났다"일 뿐 거래가 생겼는지는 알려 주지 않는다. 실패로 단정하지 않고 `UNKNOWN`으로 두고, 확인될 때까지 재고를 쥔다.
- **수락(202)과 성공은 다르다.** 외부 `PENDING`을 `CONFIRMED`로 바꾸지 않고, 알림(callback)의 문자열도 믿지 않는다. PG에 다시 물어 확인한 사실만 반영한다.
- **다시 해도 안전한 호출만 다시 한다.** 조회는 재시도·회로 차단을 걸고, 결제 요청은 거래를 또 만들 수 있어 한 번만 보낸다.
- **입구가 여러 개여도 반영은 한 곳이다.** confirm·reconcile·callback이 같은 코드로 수렴해 순서가 바뀌거나 중복돼도 결과가 같다.

## 핵심 결정

| 결정 | 이유 |
|---|---|
| 확정을 세 단계로 나눈다: 준비(TX A) → PG 요청 → 결과 반영(TX B) | PG 호출 동안 DB 연결과 재고 잠금을 쥐지 않기 위해서 |
| PG 요청 전에 재고를 차감하고, 실패가 확정될 때만 돌려준다 | PG 성공 뒤 재고 부족을 만나지 않기 위해서. 중복 반환은 주문 상태 전이가 막는다 |
| 결제 시도(`PaymentAttempt`)를 별도로 저장하고, 주문 잠금과 유일 제약으로 PG 요청 권한을 하나만 준다 | 재시작·동시 요청에도 PG 요청이 한 번만 나가게 하려고 |
| 결제 요청은 재시도하지 않고 결과를 모르면 `UNKNOWN`. 복구는 조회로만 한다 | 테스트 PG는 멱등 키를 받지 않아 다시 보내면 이중 결제가 생길 수 있어서 |
| 결과 반영은 한 곳(`PaymentStatusUpdater`)으로 모으고, callback은 확인 요청의 신호로만 쓴다 | 위조·중복·순서 뒤바뀜이 있어도 같은 결과로 수렴시키려고 |

## 흐름

```
POST /orders/{id}/confirm
 ├─ TX A  (prepareConfirm) 주문 잠금 → 재고 차감 → 시도(REQUESTED) 저장 → 주문 PAYMENT_PENDING
 ├─ PG    결제 요청 (재시도 없음, TX 밖)
 └─ TX B  PG 응답을 시도·주문에 반영 (응답 유실이면 UNKNOWN)
POST /orders/{id}/payment/reconcile ┐  PG에 결제 상태를 묻고 TX B와 같은 곳
POST /payments/callback             ┘  (PaymentStatusUpdater)에서 반영
```

## 호출 구조와 복원력

```
Facade ─ PaymentGateway(포트) ─ PaymentGatewayImpl ─ PgHttpClient ─ PgResponseParser
          조회 실패·회로 차단을 LOOKUP_UNAVAILABLE 결과로 변환 │ HTTP 전송, 조회에만 @Retry→@CircuitBreaker │ 본문 해석
```

| | 결제 요청 | 조회 |
|---|---|---|
| timeout | connect 1초, read 3초 | connect 1초, read 1초 |
| 재시도 | 없음 | 최대 3회(100ms, 200ms 간격), 한 번 최대 약 3.3초 |
| 회로 차단 | 없음 | 최근 10건 중 5건 이상, 실패율 50% 이상이면 10초 차단, 이후 2건으로 시험 |
| 실패하면 | `UNKNOWN` | 예외 → `LOOKUP_UNAVAILABLE`(상태 유지) |

- **Retry:** 일시적 실패(연결·timeout, 5xx, 해석 불가 응답)만 다시 시도한다. 회로가 막은 호출은 다시 두드리지 않는다.
- **CircuitBreaker:** PG가 계속 실패하면 호출 자체를 막아 대기와 부하를 줄인다. 404·빈 목록 같은 정상 응답은 실패로 세지 않고, OPEN이어도 결제 결과를 정하지 않는다.
- **TimeLimiter를 쓰지 않은 이유:** 비동기 호출(Future)용이라 동기 `RestClient`에 쓰려면 별도 스레드풀이 필요하고, 호출자의 대기만 끊을 뿐 PG로 나간 요청은 계속 진행된다. 대기 상한은 HTTP read timeout이 연결 수준에서 이미 보장한다.

## Resilience4j에서 배운 것

- 기본 적용 순서는 `Retry( CircuitBreaker( 호출 ) )`라 시도 한 번이 회로 표본 한 건이다. 순서에 따라 표본 수가 달라지므로 호출 수·회로 지표를 테스트로 고정했다.
- annotation은 프록시를 거칠 때만 동작한다. 같은 클래스 안에서 부르면 재시도·회로가 조용히 빠져서, 예외를 결과로 바꾸는 일은 호출하는 다른 빈이 하고 `fallbackMethod`는 쓰지 않았다.
- `maxAttempts`는 최초 호출을 포함한다. 라이브러리 계약은 가정하지 않고 테스트로 확인하고, 스타터·버전(2.2.0)을 직접 명시했다.
- 무엇을 실패로 셀지(`record-exceptions`)와 무엇을 재시도할지(`ignore-exceptions`)를 설정으로 분리해야 한다.

## 상태와 API

```
주문   DRAFT → PAYMENT_PENDING → CONFIRMED | PAYMENT_FAILED
시도   REQUESTED → PENDING | UNKNOWN → SUCCEEDED | FAILED
```

성공 확인 시 재고 유지, 실패 확인 시 재고 한 번 반환. 응답을 모르면 확인될 때까지 재고를 확보한 채 둔다.

| 요청 | 결과 |
|---|---|
| `POST /orders/{id}/confirm` | PG 결과로 끝났으면 200, 처리 중이거나 결과를 모르면 202 |
| `GET /orders/{id}` | 저장된 상태만 보여 준다. `paymentMethod`(PG, 이전 포인트 확정 주문은 POINT), `paymentStatus` 포함 |
| `POST /orders/{id}/payment/reconcile` | 같은 응답 모양. PG를 못 물었으면 저장된 상태와 `lookupUnavailable` |
| `POST /payments/callback` | 본문의 상태는 쓰지 않고 PG에 다시 물어 확인한 사실만 반영 |

## 알려진 한계

- 오래된 `UNKNOWN`·`REQUESTED`는 자동으로 종결하지 않아 확인 전까지 재고가 점유된다. 기록만 하고 보내지 못한 시도는 영구 미완료일 수 있다.
- callback 서명은 검증하지 않고 PG 조회로 검증한다(로컬 실습 한정). 회로 수치는 실측하지 않은 가설이다.

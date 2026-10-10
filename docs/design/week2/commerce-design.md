# 커머스 기본 기능 설계 (W2)

> **설계의 의도를 객체의 책임과 실행 가능한 규칙으로 표현한다.**

고객·관리자의 요청을 `HTTP → application → domain → repository·DB → 응답`까지 연결하는 기본 기능(상품·브랜드·좋아요, 포인트·주문, 관리자)의 설계 문서.
여러 태스크로 나눠 함께 채운다. `TBD`는 토론 후 확정할 칸이고, 확정된 내용만 단정적으로 적는다.

출처: `docs/note/week2/발제.md`, `docs/note/week2/실습 과제.md`

## week2 범위

week2의 목표는 **기본 API를 간단하게 구현하고 TDD를 적용**하는 것이다.

| 구분 | 내용 |
|---|---|
| 포함 | 과제 3장의 기본 API를 단순하게 구현, 대표 도메인 규칙에 TDD(Red → Green → Refactor), lint·ArchUnit 통과 |
| week3로 이관 | 동시성 제어(잠금·조건부 갱신), 브랜드-상품 일괄 삭제와 중간 실패 rollback, 삭제 시 주문 참조 보존 설계 |

삭제는 가급적 soft delete로 한다 (D-5). 삭제 관련 API는 과제의 "기본 삭제 조건" 동작만 단순하게 맞추고, 일괄 삭제·참조 보존 설계는 week3에서 한다.

## 작성 진행 상태

| 장 | 내용 | 상태 |
|---|---|---|
| 1 | 버드뷰 | 완료 |
| 2 | 구조와 의존 (ArchUnit 일치) | 완료 |
| 3 | 도메인 관계 | 완료 |
| 4 | 대표 흐름 | 완료 |
| 5 | 기본 기능 설계 (API 계약) | 골격 (P-1 결정, 나머지 TBD) |
| 6 | 설계 결정 기록 | 진행 중 (D-1 ~ D-13) |

**작업 순서**

1. 3장 도메인 관계 (P-10, P-5 포함)
2. 4장 대표 흐름
3. 기능 구현 직전에 해당 TBD를 결정: P-4(상품 목록 구현 전), P-8(DTO 작성 시), P-9(첫 관리자 Controller 작성 시)

구현하며 더 나은 판단이 나오면 이 문서를 고친다.

---

## 1. 버드뷰

고객과 관리자는 하나의 `commerce-api` 서버를 통해 하나의 MySQL을 사용한다. 두 주체는 **경로 접두사로 구분**한다.

```
고객   ── 상품·브랜드 조회 / 좋아요 / 포인트 / 주문 ──→ /api/v1/**       ─┐
                                                                         ├─→ commerce-api ──→ MySQL
관리자 ── 브랜드·상품 CRUD / 재고 변경 / 주문 조회 ───→ /api-admin/v1/** ─┘   (단일 서버)       (단일 DB)
```

| 구성 요소 | 역할 | 비고 |
|---|---|---|
| 고객 | 상품·브랜드 조회, 좋아요, 포인트 충전·조회, 주문 생성·확정·조회 | `X-USER-ID` 헤더로 요청자 식별 (로컬 실습·테스트용, 2-7 참고) |
| 관리자 | 브랜드·상품 CRUD, 재고 변경, 구매자별 주문 조회 | `/api-admin/**`에 관리자 경계 적용 예정 (`AdminBoundaryConfig`, Spring Security) |
| commerce-api | 요청 검증·유스케이스 조율·업무 규칙 실행 | 단일 Spring Boot 앱 |
| MySQL | 브랜드·상품·재고·좋아요·포인트·주문 저장 | 트랜잭션 경계는 application의 Facade |

- 같은 상품을 다뤄도 고객의 조회와 관리자의 변경은 **입력·권한·응답 필드가 다른 별개의 계약**이다.
- Redis·Kafka는 인프라에 구성돼 있으나 이번 기본 기능에서는 사용하지 않는다.
- 로컬 실습 전제: `server.address=127.0.0.1`, 공개 배포와 실제 개인정보 사용 없음. 고정 관리자 헤더는 운영 인증 수단이 아니다.

---

## 2. 구조와 의존

### 2-1. 패키지 배치 (layer-first)

`apps/commerce-api/src/main/java/com/loopers/` 아래에 계층별 패키지를 두고, 그 안에 기능(feature)을 배치한다.

```
com.loopers
├── interfaces.api.{feature}        고객 API (Controller, ApiSpec, Dto)
├── interfaces.api.admin.{feature}  관리자 API (TBD: 경로/패키지 분리 방식)
├── application.{feature}           Facade, Info
├── domain.{feature}                Model(Entity), Service, Repository(인터페이스)
├── infrastructure.{feature}        RepositoryImpl, JpaRepository
├── support.error                   CoreException, ErrorType
└── config                          설정 (예: AdminBoundaryConfig)
```

feature: `brand`, `product`, `like`, `order`, `user`. 포인트 API는 `interfaces.api.point`·`application.point`에 두고, 모델은 `domain.user`(User의 `PointBalance`)를 사용한다.

### 2-2. 계층의 역할

| 계층 | 맡는 일 | 현재 코드의 예 (user·point) | 직접 의존하지 않을 것 |
|---|---|---|---|
| **interfaces** | 요청 파싱·입력 변환, 응답 변환, 에러의 HTTP 매핑 | `PointController`, `PointApiSpec`, `PointDto`, `ApiControllerAdvice` | infrastructure |
| **application** | 유스케이스 순서 조율, 트랜잭션 경계(`@Transactional`), 도메인 → `Info` 변환, 조회 결과 조합 | `PointFacade`, `PointInfo` | interfaces, infrastructure |
| **domain** | 상태·업무 규칙(생성자·행동에서 `CoreException`), Repository 인터페이스 | `User`, `UserService`, `UserRepository` | interfaces, application, infrastructure |
| **infrastructure** | domain Repository의 JPA 구현 (위임 어댑터) | `UserRepositoryImpl`, `UserJpaRepository` | 업무 규칙 중복 |

### 2-3. 의존 방향과 ArchUnit 규칙

화살표는 "소스 코드가 의존해도 되는 방향"이다.

```
interfaces ──→ application ──→ domain ←── infrastructure
     └───────────────────────────→ ┘
```

| from \ to | interfaces | application | domain | infrastructure |
|---|---|---|---|---|
| **interfaces** | - | ✅ | ✅ | ❌ |
| **application** | ❌ | - | ✅ | ❌ |
| **domain** | ❌ | ❌ | - | ❌ |
| **infrastructure** | 검사 안 함 | 검사 안 함 | ✅ | - |

❌ 칸은 `ArchitectureTest`(`apps/commerce-api/src/test/java/com/loopers/architecture/ArchitectureTest.java`)가 검사한다.

| 금지 의존 | 검사 메서드 |
|---|---|
| domain → interfaces · application · infrastructure | `domainDoesNotDependOnOuterLayers` |
| application → interfaces · infrastructure | `applicationDoesNotDependOnInterfacesOrInfrastructure` |
| interfaces → infrastructure | `interfacesDoesNotDependOnInfrastructure` |

- **interfaces → domain은 허용**한다 (설계 결정 D-1). 다만 응답 모델은 domain Entity가 아닌 `Info`/`Dto`로 변환해 노출하는 것을 관례로 한다.
- **infrastructure → application · interfaces는 ArchUnit 규칙이 없다.** 관례상 하지 않으며, 필요해지면 규칙을 추가한다 (규칙 변경은 확인 후).
- ArchUnit 패턴이 `..domain..`처럼 패키지명 포함 방식이므로, 계층 이름(`domain`, `application`, `interfaces`, `infrastructure`)을 다른 의미의 패키지명에 쓰지 않는다.

### 2-4. DIP: 저장 기술과 업무 규칙의 분리

```
domain.{feature}.{Feature}Repository (인터페이스)   ← 정의: domain
infrastructure.{feature}.{Feature}RepositoryImpl    ← 구현: JpaRepository에 위임
```

- application·domain은 구현체 이름을 모른다. 런타임에는 JPA 구현이 호출되지만 소스 의존은 domain 쪽으로 향한다.
- Repository 메서드의 입출력에 interfaces의 DTO를 쓰지 않는다.

### 2-5. 계층 밖 공통 코드

| 대상 | 위치 | 규칙 |
|---|---|---|
| `CoreException`, `ErrorType` | `support.error` | 모든 계층에서 사용 가능. `ErrorType`은 HTTP 상태를 모른다. message는 사용자가 판단해야 하는 경우 다음 행동을 안내한다 (D-3) |
| ErrorType → HTTP 상태 | `interfaces.api.ErrorTypeHttpStatusMapper` | 이 한 곳에서만 변환. `ErrorType` 추가 시 switch 매핑도 추가(컴파일 에러로 누락 방지) |
| `BaseEntity` | `modules:jpa`의 `com.loopers.domain` | 패키지상 domain으로 검사됨 (domain만 상속) |

### 2-6. 검사로 강제되지 않는 관례

문서·리뷰로 지키는 규칙이다.

- 트랜잭션 경계는 Facade의 `@Transactional` (조회는 `readOnly = true`), 도메인 Service에는 붙이지 않는다.
- 비즈니스 규칙 검증은 Model 생성자·행동 안에서 `CoreException`으로 던진다.
- Controller는 Facade만 호출하고 응답은 `ApiResponse<T>`로 감싼다.
- soft delete 대상 Entity의 조회 쿼리는 `deletedAt IS NULL` 조건을 빠뜨리지 않는다 (D-5).

### 2-7. 요청자 식별 (로컬 실습용)

고객 API는 `X-USER-ID` 헤더로 요청자를 밝힌다.

```bash
curl -H "X-USER-ID: 1" http://localhost:8080/api/v1/points
```

- Controller가 `@RequestHeader("X-USER-ID") Long userId`로 받아 Facade에 `userId`만 넘긴다 (헤더 읽기 = interfaces).
- 사용자 존재 확인과 소유권 확인(내 주문·내 좋아요)은 application에서 한다. 헤더·`HttpServletRequest`는 application 이하로 넘기지 않는다.
- 헤더 값은 위조할 수 있으므로 **로컬 실습·테스트 전용**이다. 운영 인증 방식(JWT 등)은 정하지 않았고 이번 범위 밖이다.
- 사용자 데이터는 fixture로 준비한다 (회원가입 API 없음).
- 관리자 경계는 `/api-admin/**`에 `SecurityFilterChain`을 적용하고 MockMvc의 관리자·일반·식별 없는 요청으로 검증한다 (로그인 기능 없음).

---

## 3. 도메인 관계

### 3-1. 업무 관계

```
Brand 1 ── N Product
User  1 ── N Like N ── 1 Product
User  1 ── N Order 1 ── N OrderItem ── 상품 식별자·수량·단가
User  1 ── 포인트 잔액
Order ── 품목 금액 합계 / 포인트 결제액·결제 결과
```

이 선은 **업무 관계**이다. 객체끼리 참조할지(객체 참조), 식별자만 보관할지(id 참조)는 조회와 변경에 필요한 정보를 보고 3-2에서 따로 정한다.

### 3-2. 모델 구성

발제의 기준을 따른다. **Entity**는 식별자와 이어지는 생명주기가 중요한 객체, **VO**는 값의 의미와 동등성이 중요한 객체이다. 모든 값을 VO로 감싸지는 않는다.

| 모델 | 분류 | 담는 것 / 행동 | 근거 |
|---|---|---|---|
| **Brand** | Entity (soft delete) | 이름(앞뒤 공백 제거 후 1~50자, 공백뿐인 문자열 불가), 수정 | 식별자와 생명주기(수정·삭제)가 있다 |
| **Product** | Entity (soft delete) | `brandId`, 이름(1~100자, 공백뿐인 문자열 불가), 가격 `Money`(1원 이상 1,000,000,000원 이하), 재고 `Stock`. `decreaseStock(q)`, `changeStock(n)` | 가격이 바뀌어도 같은 상품이다 |
| **Stock** | **VO** (Product에 내장) | 재고 수량(`int`). `decrease(q)`는 새 `Stock`을 반환. 0 이상, 0 이하 수량·보유량 초과 차감 거절 | 값의 규칙을 한 곳에 모은다. `PointBalance`와 같은 방식으로 일관 |
| **Like** | Entity (**복합키** `(userId, productId)`, `BaseEntity` 미상속) | `LikeId`(`@EmbeddedId`), 좋아요 시각 `likedAt`. hard delete (D-5) | 자체 생명주기(등록·취소)와 독립 조회(내 목록, 상품별 count)가 있다. PK가 곧 유일성이라 안 쓰는 컬럼이 없다 |
| **User** | Entity (최소) | 식별자, `PointBalance`. fixture로 생성. `chargePoint(m)`, `payPoint(m)` | 요청자 존재 확인(없는 사용자 `NOT_FOUND`)과 잔액 보유에 필요 |
| **PointBalance** | **VO** (User에 내장) | 잔액 `Money`. `charge(m)`, `pay(m)`는 새 `PointBalance`를 반환. 잔액 0 허용, 충전액 양수, 합산 범위 초과·잔액 초과 결제 거절 | 값 규칙을 한 곳에 모은다 (D-8) |
| **Money** | **VO** | 0 이상 정수 원(`long`). 연산은 새 값을 반환 | 단가·합계·잔액·결제액이 모두 사용 (D-7) |
| **Order** | Entity (Aggregate root) | `userId`, 상태, 품목 목록, 합계 `Money`, 결제 결과. `confirm(...)` | 상태가 바뀌는 Entity. 품목 중복 금지(D-6)와 상태 전이 규칙을 가진다 |
| **OrderItem** | Entity (Order의 하위, Order를 통해서만 접근) | `productId`, 수량, 주문 당시 단가 `Money`, 품목 금액 | 주문 당시 값의 스냅샷. 별도 Repository 없이 Order와 함께 저장 |
| **OrderStatus** | enum | `DRAFT`, `CONFIRMED` | 값 목록이므로 별도 클래스가 필요 없다 |
| **PaymentResult** | **VO** (Order에 내장, DRAFT 동안 비어 있음) | 결제액 `Money`, 결제 후 잔액 `Money` | 확정 시점에 함께 생기고 함께 읽히는 값 (D-10) |

**VO로 만들지 않는 것**: 상품·브랜드 이름(문자열 검증은 Entity에서, 범위는 D-12), 수량(`int`, 양수 검증은 `OrderItem`·`Stock`), 식별자. 의미가 혼동되지 않고 검증 반복이 두 곳 이하이기 때문이다. 반복이 늘면 `Quantity` VO를 재검토한다.

### 3-3. 책임 배치 원칙 (안)

| 구분 | 위치 | 예 |
|---|---|---|
| 자기 상태의 규칙 | domain 모델의 생성자·행동 | 재고 차감 검증, 충전액 양수, 주문 상태 전이, 품목 중복 검증 |
| 다른 객체를 조회해야 하는 검증 | application(Facade) | 브랜드 존재 확인, 상품 삭제 여부, 사용자 존재·소유권 |
| 조회 결과 조합 | application(Facade) → `Info` | 상품 + 브랜드 + 좋아요 수 응답 |

조회 결과 조합을 Product가 아니라 application에 두는 이유: 브랜드 응답이 바뀔 때 Product 모델이 영향을 받지 않고, Product는 자신의 상태와 규칙에 집중한다. 비용은 조합 코드가 application에 필요하다는 점이다.

### 3-4. 결정 현황

| # | 항목 | 상태 |
|---|---|---|
| 1 | Money VO 도입 | **결정됨 (D-7)** |
| 2 | Like의 분류 | **결정됨 (D-9)**: Entity, 복합키 |
| 3 | PointBalance 구조 | **결정됨 (D-8)**: User에 내장된 VO |
| 4 | Stock 구조 | **결정됨 (D-9)**: Product에 내장된 VO |
| 5 | Order·OrderItem·결제 결과 | **결정됨 (D-9)**: 세부 필드는 4장에서 확정 |
| 6 | 참조 방식 | **결정됨 (D-9)**: Aggregate 사이는 id 참조 |
| 7 | User 모델 (P-10) | **결정됨 (D-8)**: 최소 Entity + `PointBalance` |
| 8 | 좋아요 수 조회 방식과 `likes_desc` 정렬 비용, 이름·가격 허용 범위 (P-5) | **결정됨 (D-12)** |

분류(Entity/VO)와 참조 방식(id/연관관계)은 별개의 결정이다. 분류를 먼저 정했다.

### 3-5. 참조 방식과 모델 구성도

**Aggregate 사이는 id(`Long`)로 참조한다** (D-9).

```
Brand ◁── Product[brandId] ── Stock(VO), Money(VO)
                ▲
Like[userId, productId] ──▷ User ── PointBalance(VO) ── Money(VO)
                              ▲
Order[userId] ── OrderItem[productId, qty, unitPrice:Money] ── PaymentResult(VO)
```

▲/▷는 id 참조, 선은 내장 또는 소유를 뜻한다.

| 구분 | 방식 | 예 |
|---|---|---|
| Aggregate 사이 | id 참조 | `Product.brandId`, `Like.userId·productId`, `Order.userId`, `OrderItem.productId` |
| Aggregate 안 | 객체 참조 | `Order` → `OrderItem` 컬렉션 |

- Repository는 Aggregate 단위이다: `BrandRepository`, `ProductRepository`, `LikeRepository`, `OrderRepository`, `UserRepository`. `Stock`, `PointBalance`, `OrderItem`, `PaymentResult`는 자체 Repository가 없다.
- domain Service는 `UserService`와 같은 방식으로 Aggregate별 저장 위임과 단순 조율을 맡는다. 여러 Aggregate를 엮는 유스케이스는 Facade가 맡는다.
- 상품 응답(상품 + 브랜드 + 좋아요 수)의 조합은 application이 한다. 목록에서는 `brandIds`를 모아 일괄 조회해 N+1을 피한다.
- 좋아요 수는 Like 행에서 집계한다 (D-12). 상세는 `count`, 목록은 `productIds`로 `group by` 일괄 집계, `likes_desc` 정렬은 Product 쿼리에서 Like 집계를 join해 수행한다. 카운터 컬럼은 두지 않는다.

---

## 4. 대표 흐름

대표 흐름은 **참고용 설계 산출물**이다. 책임 배치, 트랜잭션 경계, 오류가 나는 순서를 구현 전에 확인하고 테스트 케이스를 뽑는 지도로 쓴다. 코드나 테스트로 직접 옮기지 않는다.

표기: `요청 → Controller → Facade(@Transactional) → Service → Model → Repository`. **요청 하나가 트랜잭션 하나**이다.

### 4-1. 포인트 충전 → 주문 생성 → 주문 확정

시나리오: 잔액 0원 → 10,000원 충전 → 상품 A(3,000원) × 1 + 상품 B(2,000원) × 2 = 7,000원 주문 → 확정 → 잔액 3,000원. **세 요청은 각각 별도 트랜잭션**이다.

**① 충전** `POST /api/v1/points/charge`

`PointFacade.charge(userId, amount)` → `UserService.get`(없으면 `NOT_FOUND`) → `user.chargePoint(Money)`(0 이하·표현 범위 초과 `INVALID_INPUT`) → 저장 → 충전 후 잔액을 반환한다.

**② 주문 생성** `POST /api/v1/orders`

`OrderFacade.create(userId, items)` → 사용자 확인 → `ProductService`로 상품 일괄 조회(없는·삭제된 상품 `NOT_FOUND`) → `Order.create(userId, 품목 + 현재 단가)`(중복 품목·수량 0 이하 `INVALID_INPUT`, 합계 계산) → 저장 → DRAFT 주문을 반환한다. 재고·잔액은 차감하지 않는다.

**③ 확정** `POST /api/v1/orders/{orderId}/confirm`

| 단계 | 하는 일 | 책임 | 실패 시 errorCode |
|---|---|---|---|
| 1 | 본인 주문 조회 (`userId`로 한정) | OrderService | `NOT_FOUND` |
| 2 | 사용자 조회 | UserService | `NOT_FOUND` |
| 3 | 주문이 DRAFT인지 확인 | `Order.ensureConfirmable()` | `ORDER_ALREADY_CONFIRMED` |
| 4 | 품목 상품 일괄 조회, 삭제·없음 확인 | ProductService | `PRODUCT_UNAVAILABLE` |
| 5 | 품목마다 `product.decreaseStock(qty)` | Product → Stock | `OUT_OF_STOCK` |
| 6 | `user.payPoint(order.total)` | User → PointBalance | `INSUFFICIENT_POINT` |
| 7 | `order.confirm(PaymentResult)`로 CONFIRMED 전환 | Order | (상태 재검증) |
| 8 | 변경된 Entity 저장 (변경 감지) | JPA | 예외 시 전체 rollback |

```
Controller → OrderFacade.confirm(userId, orderId)   ← @Transactional 시작
              ├ OrderService  : 본인 주문 조회
              ├ UserService   : 사용자 조회
              ├ Order         : ensureConfirmable()
              ├ ProductService: 상품 일괄 조회 (삭제·없음 확인)
              ├ Product/Stock : decreaseStock(qty) × 품목 수
              ├ User/PointBalance : payPoint(total)
              └ Order         : confirm(PaymentResult(결제액, 결제 후 잔액))
            ← 정상 반환 시 commit, 예외 시 rollback
```

- 오류 우선순위는 단계 번호 순서이다. 3단계를 먼저 두는 이유는 이미 확정된 주문이 재고 부족 오류로 보이지 않게 하기 위해서이다. 7단계의 `confirm`이 상태를 다시 검증하는 것은 불변식 보호용이라 역할이 다르다.
- 트랜잭션 경계는 Facade이다. 5·6단계에서 예외가 나면 앞 단계의 변경이 모두 취소된다 (D-10).
- `PaymentResult`는 결제액과 결제 후 잔액이다. 예: 결제액 7,000원, 결제 후 잔액 3,000원.

### 4-2. 관리자 상품 수정 → 고객 상품 상세 조회

두 요청은 별도이다.

1. **관리자** `PUT /api-admin/v1/products/{productId}`: `AdminProductFacade.update` → 상품 조회(삭제·없음 `NOT_FOUND`) → `product.update(이름, 가격)`(브랜드는 바뀌지 않음, 검증 실패 `INVALID_INPUT`) → 저장.
2. **고객** `GET /api/v1/products/{productId}`: `ProductFacade.get`(`readOnly`) → 상품 조회 → `brandId`로 브랜드 조회 → 좋아요 수 집계 → `ProductInfo`로 조합한다. 수정한 가격이 반영된다.

- 상품·브랜드·좋아요 수의 조합은 application이 한다 (D-9). 응답 필드의 고객/관리자 구분은 P-8에서 DTO를 쓸 때 정한다.
- 상품이 남은 브랜드는 삭제할 수 없으므로 week2에서는 삭제된 브랜드의 상품이 생기지 않는다. 일괄 삭제는 week3에서 다룬다.

### 4-3. 좋아요 등록·취소

- **등록** `POST /api/v1/products/{productId}/likes`: `LikeFacade.like` → 사용자 확인 → 상품 조회(삭제·없음 `NOT_FOUND`) → 이미 있으면 그대로 성공, 없으면 `Like(userId, productId, likedAt)` 저장.
- **취소** `DELETE /api/v1/products/{productId}/likes`: `LikeFacade.unlike` → 사용자 확인 → `delete(userId, productId)`. 행이 없어도 성공한다. 상품을 조회하지 않으므로 **삭제된 상품이어도 취소할 수 있다**.
- **내 목록** `GET /api/v1/users/{userId}/likes`: path `userId`가 요청자와 다르면 `FORBIDDEN`(조회 없이 비교만으로 판단하므로 사용자 존재 여부를 노출하지 않는다). 같으면 좋아요의 `productId`들을 일괄 조회(삭제된 상품 제외)해 조합한다.
- "타인" 판단 기준: 소유자가 URL에 드러나 있으면 `FORBIDDEN`, 조회로만 알 수 있으면(타인 주문) `NOT_FOUND`이다 (D-11).

---

## 5. 기본 기능 설계 (API 계약)

고객 API는 `X-USER-ID` 헤더가 필수다 (2-7). 식별 오류는 기존 `ErrorType`으로 단순하게 처리한다 (D-2).

| 상황 | ErrorType (안) |
|---|---|
| 헤더 누락·숫자가 아닌 값 | `INVALID_INPUT` |
| 존재하지 않는 사용자 | `NOT_FOUND` |
| path `userId`가 요청자와 다름 (소유자가 URL에 드러난 타인 리소스) | `FORBIDDEN` |

- 구현 메모: 현재 `ApiControllerAdvice`에 `MissingRequestHeaderException` 처리가 없다. 구현 시 `INVALID_INPUT`으로 매핑을 추가해야 한다.
- 표의 `TBD`는 5-5의 미정 정책에서 결정한다.

**성공 응답 원칙 (D-13)**: 돌려줄 데이터가 없는 성공도 HTTP 200과 `ApiResponse.success()`로 응답한다 (204 No Content를 쓰지 않는다). 아래 표의 "성공 (본문 없음)"은 이 응답을 뜻한다.

**오류 응답 원칙 (D-3)**

1. `errorCode`: 클라이언트가 **동작을 달리해야 하는 경우**마다 별도 코드를 둔다. 같은 코드면 같은 동작이다.
2. `message`: 사용자에게 보여 줄 수 있는 문장이다. 시스템이 처리할 수 없어 사용자가 판단해야 하는 경우에는 **다음 행동을 포함**한다. 예: "재고가 부족합니다. 수량을 줄여 다시 주문해 주세요."
3. HTTP 상태는 큰 분류(400/404/409/500)만 맡고, 세부 분기는 `errorCode`가 맡는다. 변환은 `ErrorTypeHttpStatusMapper` 한 곳에서 한다.
4. 클라이언트 분기가 필요 없는 오류는 범용 코드(`INVALID_INPUT` 등)를 쓰고 메시지로만 설명한다. 코드를 늘리지 않는다.

**주문 오류 계약**

| 상황 | errorCode | HTTP | 클라이언트 동작 | 메시지 방향 |
|---|---|---|---|---|
| 수량 0 이하, 품목 없음, 같은 상품이 중복된 품목 | `INVALID_INPUT` | 400 | 입력 수정 (중복은 병합 후 재요청) | 잘못된 값 설명. 예: "같은 상품이 중복되었습니다. 수량을 합쳐 다시 요청해 주세요." |
| 없는 주문, **타인 주문** | `NOT_FOUND` | 404 | 주문 목록으로 이동 | 존재 여부를 숨김 ("주문을 찾을 수 없습니다.") |
| 주문 생성 시 없는·삭제된 상품 | `NOT_FOUND` | 404 | 해당 상품 제거 후 재시도 | 어떤 상품인지 |
| 이미 확정된 주문 | `ORDER_ALREADY_CONFIRMED` (신규) | 409 | 주문 상세 조회로 전환 | "이미 결제가 완료된 주문입니다." |
| 재고 부족 | `OUT_OF_STOCK` (신규) | 409 | 수량 조정 안내 | "재고가 부족합니다. 수량을 줄여 다시 주문해 주세요." |
| 잔액 부족 | `INSUFFICIENT_POINT` (신규) | 409 | 충전 화면으로 유도 | "포인트가 부족합니다. 충전 후 다시 시도해 주세요." |
| 확정 시 상품 삭제됨 | `PRODUCT_UNAVAILABLE` (신규) | 409 | 주문 폐기·재주문 안내 | "구매할 수 없는 상품이 포함되어 있습니다." |

- 신규 4개 코드는 모두 409로 매핑한다. 기존 `CONFLICT`는 **현재 상태와 충돌하는 범용 코드**로 두고, 사용처는 메시지를 항상 직접 지정한다 (D-13).
- 구현 시 `ErrorType` 4개, `ErrorTypeHttpStatusMapper` switch, `ErrorTypeHttpStatusMapperTest`를 함께 갱신한다.

### 5-1. 상품·브랜드·좋아요 (고객)

| 기능 | method · path | 입력 | 성공 | 대표 오류 |
|---|---|---|---|---|
| 브랜드 상세 | `GET /api/v1/brands/{brandId}` | path `brandId` | 브랜드 정보 | 없거나 삭제된 브랜드 → `NOT_FOUND` |
| 상품 목록 | `GET /api/v1/products` | 브랜드 필터, 페이지, 정렬(`latest`·`price_asc`·`likes_desc`) | 상품 목록 (브랜드 정보·좋아요 수 포함, 삭제 상품 제외) | 잘못된 정렬·페이지 → `INVALID_INPUT` (상세 TBD) |
| 상품 상세 | `GET /api/v1/products/{productId}` | path `productId` | 상품 정보 (브랜드 정보·좋아요 수 포함) | 없거나 삭제된 상품 → `NOT_FOUND` |
| 좋아요 등록 | `POST /api/v1/products/{productId}/likes` | path `productId`, 요청자 | 성공 (200, 본문 없음). 이미 좋아요한 상품이어도 성공(멱등), 관계는 하나만 유지 | 없거나 삭제된 상품 → `NOT_FOUND` |
| 좋아요 취소 | `DELETE /api/v1/products/{productId}/likes` | path `productId`, 요청자 | 성공 (200, 본문 없음). 관계가 없어도 성공(멱등). 관계는 hard delete (D-5) | 없는 상품 → `NOT_FOUND` (삭제된 상품이라도 자신의 관계는 취소 가능) |
| 내 좋아요 목록 | `GET /api/v1/users/{userId}/likes` | path `userId`, 요청자 | 내 좋아요 상품 목록 (삭제 상품 제외) | path `userId`가 헤더의 요청자와 다르면 → `FORBIDDEN` |

### 5-2. 포인트·주문 (고객)

| 기능 | method · path | 입력 | 성공 | 대표 오류 |
|---|---|---|---|---|
| 포인트 충전 | `POST /api/v1/points/charge` | body `amount` (양의 정수) | 충전 후 잔액 | 누락·잘못된 타입·0 이하·표현 범위 초과 → `INVALID_INPUT` (기존 잔액 유지), 충전 후 잔액이 표현 범위 초과 → `INVALID_INPUT` (기존 잔액 유지) |
| 잔액 조회 | `GET /api/v1/points` | 요청자 | 저장된 잔액 | 헤더 누락 → `INVALID_INPUT`, 없는 사용자 → `NOT_FOUND` |
| 주문 생성 | `POST /api/v1/orders` | body 품목 목록 (상품 id, 수량) | DRAFT 주문 (품목·수량·단가·합계), 차감 없음 | 없거나 삭제된 상품 → `NOT_FOUND`, 수량 0 이하·품목 없음·같은 상품 중복 → `INVALID_INPUT` |
| 주문 확정 | `POST /api/v1/orders/{orderId}/confirm` | path `orderId` | CONFIRMED 주문 (결제액·결과), 재고·포인트 차감 | 재고 부족 → `OUT_OF_STOCK`, 잔액 부족 → `INSUFFICIENT_POINT`, 이미 확정 → `ORDER_ALREADY_CONFIRMED`, 상품 삭제됨 → `PRODUCT_UNAVAILABLE`, 없는·타인 주문 → `NOT_FOUND` |
| 내 주문 목록 | `GET /api/v1/orders` | 요청자 | 내 주문 목록 | 페이징 TBD |
| 내 주문 상세 | `GET /api/v1/orders/{orderId}` | path `orderId` | 품목·수량·금액·상태·결제액 | 없거나 타인 주문 → `NOT_FOUND` |

### 5-3. 관리자

| 기능 | method · path | 입력 | 성공 | 대표 오류 |
|---|---|---|---|---|
| 브랜드 목록·상세 | `GET /api-admin/v1/brands`, `GET /api-admin/v1/brands/{brandId}` | (목록 조건 TBD) | 브랜드 정보 | 없는 브랜드 → `NOT_FOUND` |
| 브랜드 등록 | `POST /api-admin/v1/brands` | body 이름 (1~50자) | 저장된 브랜드 | 유효하지 않은 입력 → `INVALID_INPUT` |
| 브랜드 수정 | `PUT /api-admin/v1/brands/{brandId}` | body 이름 (1~50자) | 수정된 브랜드 | 없거나 삭제된 브랜드 → `NOT_FOUND`, 입력 오류 → `INVALID_INPUT` |
| 브랜드 삭제 | `DELETE /api-admin/v1/brands/{brandId}` | path `brandId` | 성공 (200, 본문 없음) | 삭제되지 않은 상품 연결(재고 0 포함) → `CONFLICT` ("연결된 상품이 있어 브랜드를 삭제할 수 없습니다. 상품을 먼저 삭제해 주세요."), 이미 삭제됐거나 없는 브랜드 → `NOT_FOUND` |
| 상품 목록·상세 | `GET /api-admin/v1/products`, `GET /api-admin/v1/products/{productId}` | (목록 조건 TBD) | 상품 정보 (관리자용 필드 TBD) | 없는 상품 → `NOT_FOUND` |
| 상품 등록 | `POST /api-admin/v1/products` | body 브랜드 id, 이름(1~100자), 가격(1~1,000,000,000), 초기 재고(0 이상) | 저장된 상품 | 없거나 삭제된 브랜드 참조 → `NOT_FOUND`, 이름·가격 범위 위반 → `INVALID_INPUT` |
| 상품 수정 | `PUT /api-admin/v1/products/{productId}` | body 이름(1~100자), 가격(1~1,000,000,000) (브랜드는 변경 불가) | 수정된 상품 | 삭제된 상품 수정 거절 → `NOT_FOUND`(안), 입력 오류 → `INVALID_INPUT` |
| 상품 삭제 | `DELETE /api-admin/v1/products/{productId}` | path `productId` | 성공 (200, 본문 없음) | 이미 삭제됐거나 없는 상품 → `NOT_FOUND` |
| 재고 변경 | `PUT /api-admin/v1/products/{productId}/stock` | body 최종 수량 (0 이상) | 변경된 재고 | 음수 → `INVALID_INPUT`, 삭제된 상품 거절 |
| 주문 목록·상세 | `GET /api-admin/v1/orders`, `GET /api-admin/v1/orders/{orderId}` | (구매자별 조건 TBD) | 구매자별 주문의 품목·상태·금액·결제 결과 | 없는 주문 → `NOT_FOUND` |

- 관리자 API 접근: 관리자 허용, 일반 사용자·식별 없는 요청은 거절(403). 거절 시 DB를 바꾸지 않는다.
- 삭제 정책(기본 삭제 조건): 삭제되지 않은 상품이 연결된 브랜드의 삭제는 거절(재고 0 포함). 삭제된 브랜드·상품은 고객 조회·새 주문에서 제외하고 수정·재고 변경 대상으로도 사용하지 않는다. 삭제는 soft delete(`BaseEntity.delete()`, `deletedAt`)로 처리한다 (D-5).

### 5-4. 주요 규칙의 기대값

과제에 명시된 기대값이다. 테스트와 연결한다.

| 규칙 | 입력 | 기대 결과 |
|---|---|---|
| 재고 차감 거절 | 재고 5에서 6 차감 | 거절, 재고 5 유지 |
| 재고 차감 | 재고 5에서 2 차감 | 재고 3 |
| 재고 전량 차감 | 재고 5에서 5 차감 | 재고 0 |
| 차감 수량 검증 | 0 이하 수량 | 거절 |
| 잔액 | 잔액 0 | 유효 (허용) |
| 충전 입력 | 충전액 0 이하 | 거절, 기존 잔액 유지 |
| 충전 후 잔액 | 잔액 + 충전액이 표현 범위 초과 | 거절, 기존 잔액 유지 |
| 충전 → 결제 | 잔액 0에서 10,000 충전 후 7,000 결제 | 잔액 3,000, 결제액 7,000 저장 |
| 주문 생성 | 품목 목록으로 생성 | DRAFT, 재고·잔액 차감 없음 |
| 이름 검증 | 공백뿐인 이름 / 브랜드 50자 초과 / 상품 100자 초과 | `INVALID_INPUT`, 저장 없음 |
| 가격 검증 | 가격 0원 / 1,000,000,000원 초과 | `INVALID_INPUT`, 저장 없음 |
| 주문 품목 중복 | 같은 상품이 두 품목으로 들어옴 | `INVALID_INPUT`으로 거절, 주문 저장 없음 |
| 주문 확정 | 본인 DRAFT 주문 확정 | 재고·포인트 차감, CONFIRMED, 결제액·결과 저장 |
| 연결 시나리오 | 충전 API → 여러 품목 주문 확정 → 내 주문·잔액 조회 (잔액 0에서 10,000 충전, 7,000 결제) | 잔액 3,000, 결제액 7,000, 주문 CONFIRMED로 조회 |
| 좋아요 중복 등록 | 같은 사용자가 같은 상품에 좋아요 2회 | 둘 다 성공, 관계 1개, 좋아요 수는 1만 반영 |
| 좋아요 없이 취소 | 관계 없는 상품에 취소 | 성공, 좋아요 수 변화 없음 (0 미만 불가) |
| 삭제된 상품의 좋아요 | 삭제된 상품에 새 좋아요 / 자신의 기존 좋아요 취소 | `NOT_FOUND` / 취소 성공 |
| 확정 시 검증 | 상품 삭제됨 / 재고 부족 / 잔액 부족 | 각각 `PRODUCT_UNAVAILABLE` / `OUT_OF_STOCK` / `INSUFFICIENT_POINT`로 거절, 상태·재고·잔액 변경 없음 |

### 5-5. 미정 정책 (토론 필요)

| # | 질문 | 영향 받는 것 |
|---|---|---|
| P-1 | ~~로컬 실습용 사용자 식별 방식~~ | **결정됨 (D-2)**: `X-USER-ID` 헤더, 오류는 기존 `ErrorType` |
| P-2 | ~~브랜드·상품 삭제는 물리/논리 중 무엇인가~~ | **결정됨 (D-5)**: 가급적 soft delete. 일괄 삭제·참조 보존 설계는 week3 |
| P-3 | ~~주문의 중복 상품 품목은 합산하는가 거절하는가~~ | **결정됨 (D-6)**: 거절 (`INVALID_INPUT`) |
| P-4 | 상품 목록: 정렬 동률 보조 기준, 페이지 기본값·최대 크기, 잘못된 정렬값 처리는? | 목록 쿼리, 입력 오류. **상품 목록 구현 직전에 결정** |
| P-5 | ~~상품 이름·가격, 브랜드 이름의 허용 범위~~ | **결정됨 (D-12)**: 브랜드 이름 1~50자, 상품 이름 1~100자, 가격 1~1,000,000,000원 |
| P-6 | ~~좋아요 중복 등록·없는 관계 취소, 타인 `userId` 목록 접근~~ | **결정됨 (D-4, D-11)**: 멱등 성공, 타인 `userId`의 목록 접근은 `FORBIDDEN`. 동시 요청 경쟁은 week3 이관 |
| P-7 | ~~이미 CONFIRMED인 주문 확정, 타인 주문, 재고·잔액 부족의 `ErrorType`~~ | **결정됨 (D-3)**: 의미 단위 errorCode + 행동 안내 메시지 |
| P-8 | 고객/관리자 응답 필드 구분(예: 관리자에게만 재고 노출)? | 응답 Dto 분리. **해당 API의 DTO를 작성할 때 결정** |
| P-9 | 관리자 controller의 패키지·클래스 분리 방식(`interfaces.api.admin.*`)? | 2-1 패키지 배치. **첫 관리자 Controller 작성 시 결정** |
| P-10 | 사용자(User)를 도메인 모델로 둘지, 식별자만 쓸지? fixture 범위는? | **결정됨 (D-8)**: 최소 User Entity + `PointBalance` VO |

---

## 6. 설계 결정 기록

### D-1. interfaces → domain 직접 의존을 허용한다

- **상황**: 발제 예시의 금지 규칙에 `interfaces → domain`이 없다. Controller가 domain 타입(enum 등)을 쓸 수 있는지 정해야 했다.
- **결정**: 허용한다. 바깥 계층이 안쪽 계층(domain)에 의존하는 것은 레이어드 방향에 어긋나지 않고, 문서와 `ArchitectureTest`를 일치시킨다.
- **비용**: Controller가 domain 변경에 영향받을 수 있다. 이를 줄이기 위해 응답은 `Info`/`Dto`로 변환해 노출하는 관례를 둔다.
- **재검토 조건**: Controller·Dto에서 domain Entity를 직접 노출하는 사례가 생기면 `interfaces ↛ domain` 규칙 추가를 검토한다.

### D-2. 고객 식별은 로컬 실습·테스트용 `X-USER-ID` 헤더로 단순 구현한다

- **상황**: 과제는 로컬 실습용 식별만 요구하고, week2의 목표는 기본 API와 TDD다.
- **결정**: Controller가 헤더를 받아 `userId`를 Facade에 전달한다. 별도 추상화(ArgumentResolver 등)는 두지 않는다. 오류는 기존 `ErrorType`을 쓴다 (누락·형식 오류 `INVALID_INPUT`, 없는 사용자 `NOT_FOUND`).
- **비용**: 헤더는 위조할 수 있어 로컬 전용이다. 운영 인증을 도입하면 Controller 시그니처와 오류 계약을 바꿔야 한다.
- **재검토 조건**: 운영 인증 방식(JWT 등)을 정할 때.

### D-3. 오류는 의미 단위 errorCode와 행동 안내 메시지로 설계한다

- **상황**: 주문 확정 실패(재고 부족, 잔액 부족, 이미 확정 등)를 `CONFLICT` 하나로 두면 클라이언트가 원인을 구분하려고 메시지를 파싱해야 한다.
- **결정**: 클라이언트 동작이 달라지는 경우마다 errorCode를 둔다 (`ORDER_ALREADY_CONFIRMED`, `OUT_OF_STOCK`, `INSUFFICIENT_POINT`, `PRODUCT_UNAVAILABLE`은 409, 소유자가 URL에 드러난 타인 리소스 접근은 `FORBIDDEN` 403). 사용자가 판단해야 하는 경우의 메시지에는 다음 행동을 포함한다. 조회로만 알 수 있는 타인 주문은 존재 여부를 숨기려고 `NOT_FOUND`로 처리한다 (D-11).
- **비용**: 코드가 늘어나 `ErrorType`, 매퍼, 테스트를 함께 추가해야 한다. `CONFLICT`는 현재 상태와 충돌하는 범용 코드이며, 구현 시 기본 메시지를 "요청이 현재 상태와 충돌합니다."로 바꾸고 사용처는 메시지를 직접 지정한다 (D-13).
- **재검토 조건**: 코드가 10개를 넘거나, 클라이언트가 분기하지 않는 코드가 생기면 통합을 검토한다.
- **기각한 대안**: `CONFLICT` 하나 + 메시지로 구분. 문구가 바뀌면 클라이언트가 깨진다.

### D-4. 좋아요 등록·취소는 멱등 성공으로 처리한다

- **상황**: 같은 사용자의 중복 등록이나 없는 관계 취소를 오류로 볼지 정해야 했다. 좋아요 버튼은 더블 클릭·재시도가 흔하다.
- **결정**: 두 경우 모두 성공으로 처리하고 관계는 하나만 유지한다. 클라이언트 동작이 같으므로 별도 errorCode를 두지 않는다 (D-3). 삭제된 상품은 새 등록만 `NOT_FOUND`, 자신의 기존 관계 취소는 허용한다. 타인 `userId`의 목록 조회는 `FORBIDDEN`으로 처리한다 (D-11).
- **비용**: 클라이언트가 중복 요청이었는지 응답으로 알 수 없다. 순차 중복을 판단하려고 존재 확인 조회가 한 번 더 필요하다. `(user_id, product_id)` 복합 PK가 유일성을 보장한다 (좋아요 취소는 hard delete라 재등록 때 충돌하지 않는다, D-5, D-9).
- **재검토 조건**: 클라이언트가 "이미 좋아요함"을 구분해야 하면 별도 errorCode를 검토한다. 동시에 들어온 요청이 PK 제약에 걸리는 경우의 오류 계약은 week3에서 동시성 설계와 함께 정한다.
- **기각한 대안**: 중복 등록을 `CONFLICT`로 처리. 더블 클릭·재시도에서 사용자에게 불필요한 오류가 노출된다.

### D-5. 삭제는 가급적 soft delete로 하고, hard delete는 기준에 맞는 경우로 제한한다

- **상황**: 브랜드·상품은 주문 등이 참조하고, 삭제된 대상을 고객 조회와 새 주문에서 걸러야 한다.
- **결정**: soft delete(`BaseEntity.delete()`, `deletedAt`)를 기본으로 한다. hard delete는 다음 경우로 제한한다.
  1. 이력이 필요 없고 다른 데이터가 참조하지 않는 관계 데이터
  2. 법적으로 파기해야 하는 개인정보
  3. 보존 기간이 지난 임시·로그성 데이터

  이 프로젝트의 예외는 **좋아요 취소**뿐이다.
- **비용**: 조회마다 `deletedAt IS NULL` 조건이 필요하고, 빠뜨리면 삭제된 데이터가 노출된다. 삭제 행이 누적되고, 유니크 제약과 충돌할 수 있다.
- **재검토 조건**: 개인정보 파기 요구가 생기거나 삭제 행 누적이 성능 문제가 될 때. week3에서 브랜드-상품 일괄 삭제·주문 참조 보존과 함께 다시 본다.
- **기각한 대안**: 좋아요도 soft delete로 통일. 유니크 제약이 취소된 행과 충돌해 `deletedAt` 포함 제약과 `restore()` 재등록 처리가 필요한데, 보존할 이력 가치가 없다.

### D-6. 주문의 중복 상품 품목은 거절한다

- **상황**: 주문 생성 요청에 같은 상품이 두 품목으로 들어올 수 있다. 과제는 합산 또는 거절을 허용한다.
- **결정**: `INVALID_INPUT`으로 거절한다. "한 주문 안에서 상품은 한 번만 나온다"는 규칙을 `Order`가 품목 목록을 받는 생성 시점에 지킨다. 품목 수량이 곧 상품별 총수량이므로 재고 확인에 별도 합산이 필요 없다.
- **비용**: 클라이언트가 같은 상품을 병합해서 보내야 한다.
- **재검토 조건**: 클라이언트를 통제할 수 없어 병합 부담이 문제가 되면 합산으로 바꾼다. 그때는 병합 규칙(응답 품목 구성, 단가 기준)을 함께 정한다.
- **기각한 대안**: 서버가 합산. 병합 로직과 응답 구조 변화가 생기고, 중복 전송 같은 클라이언트 버그가 가려진다.

### D-7. 금액은 Money VO로 표현한다

- **상황**: 상품 단가, 포인트 잔액, 주문 합계·결제액이 모두 금액이고, 음수 방지 같은 검증이 여러 곳에 반복될 수 있다.
- **결정**: `Money`를 불변 값 객체로 도입한다. 단위는 `long`(원)이며 1포인트는 1원이다. 연산은 기존 값을 바꾸지 않고 새 `Money`를 반환한다.
  - `Money`가 지키는 것은 **값 하나의 유효성**(0 이상)이다. 0원은 유효하다.
  - **행동의 입력 조건**은 `Money`가 아니라 사용하는 객체가 지킨다. 충전액은 양수여야 한다는 규칙은 `PointBalance.charge()`가, 결제액이 잔액 이하여야 한다는 관계 규칙은 `PointBalance.pay()`가 맡는다. 그래서 잔액 0원은 유효하지만 충전 0원은 거절된다.
- **비용**: JPA 매핑 설정(`@Embeddable` 등)이 필요하다. 재고 수량처럼 금액이 아닌 값에는 쓰지 않는다.
- **재검토 조건**: 통화나 소수 단위가 필요해지면 타입을 바꾼다.
- **기각한 대안**: `long`을 그대로 쓰고 `PointBalance`·`Product` 등에서 각자 검증. 검증이 반복되고 빠뜨릴 위험이 있다.

### D-8. 포인트 잔액은 User에 내장된 PointBalance VO로 둔다

- **상황**: 포인트 잔액을 어디에 두고, User를 Entity로 둘지 정해야 했다. 충전은 잔액을 늘리고 결제는 잔액 이하만 차감하는 규칙이 있다.
- **결정**: `User`(Entity)가 `PointBalance`(VO)를 내장한다. `PointBalance`는 잔액 `Money`를 갖고 `charge(Money)`, `pay(Money)`가 새 `PointBalance`를 반환한다. 충전액이 양수여야 한다는 입력 조건, 합산 범위 초과, 잔액 초과 결제 거절은 `PointBalance`가 지킨다. `User`는 `chargePoint`, `payPoint`로 위임하고 결과로 필드를 교체한다.
- **근거**: 테이블이 `users` 하나이고, 규칙이 값 객체에 모이며, 요청자 존재 확인 때문에 User Entity가 어차피 필요하다.
- **비용**: 포인트가 바뀔 때마다 users 행이 갱신되므로, 나중에 User에 다른 변경 필드가 생기면 같은 행에서 경합할 수 있다. `Money`를 내장한 `@Embedded` 중첩의 컬럼명 지정이 필요하다.
- **재검토 조건**: 포인트 이력·적립·만료 요구가 생기면 별도 Entity로 분리한다. week3에서 users 행 잠금이 문제가 될 때도 재검토한다.
- **기각한 대안**: 별도 `PointBalance` Entity(1:1). 테이블과 조회가 늘고 현재는 이점이 없다.

### D-9. Entity·VO 구성과 Aggregate 사이 id 참조

- **상황**: 도메인 모델의 분류와 참조 방식을 한 번에 정해야 했다.
- **결정**: 3-2의 표대로 정한다. VO는 `Money`, `Stock`, `PointBalance`, `PaymentResult`, Entity는 `Brand`, `Product`, `Like`, `User`, `Order`, `OrderItem`이다. `Like`는 `(userId, productId)` 복합키를 쓰고 `BaseEntity`를 상속하지 않는다. Aggregate 사이는 id로 참조하고, 조회 결과 조합은 application이 한다.
- **근거 (id 참조)**:
  1. `@ManyToOne`은 기본이 EAGER라 `Order → User`, `OrderItem → Product → Brand`가 연쇄 로딩된다. LAZY는 설정 누락과 `LazyInitializationException` 위험이 있고, 목록은 브랜드 N+1이 생긴다.
  2. `Product`가 `List<Like>`를 가지면 `getLikes().size()`가 좋아요 행을 전부 로딩한다. 연관관계를 써도 집계 쿼리와 조합 코드는 필요하다.
  3. soft delete(D-5) 필터가 걸린 Brand를 `product.getBrand()`로 따라가면 null이나 `EntityNotFoundException`이 날 수 있다. id 참조는 삭제된 브랜드 취급을 코드가 명시한다.
  4. week3에서 `Product`를 `FOR UPDATE`로 잠글 때 조인된 Brand 행까지 영향을 줄 수 있다 (DB·쿼리에 따라). id 참조는 읽는 행이 분명하다.
  5. `new Like(1L, 10L)`처럼 연관 객체 없이 단위 테스트를 쓸 수 있다.
  6. 응답이 바뀌어도 모델은 바뀌지 않고 조합 코드만 바뀐다.
- **근거 (Like 복합키)**: PK가 곧 유일성이라 대리키, `updatedAt`, `deletedAt` 같은 안 쓰는 컬럼이 없다. 검증 흐름(등록은 존재 확인 후 저장, 취소는 삭제하고 영향 행 0이어도 성공, 재등록은 새 행 INSERT)은 대리키와 같다.
- **비용**: 조합 코드를 직접 써야 하고 목록 조회에서 일괄 조회가 필요하다. `likes_desc` 정렬은 Like 집계 쿼리가 필요하다. `Like`는 `BaseEntity`를 벗어나 `likedAt`을 직접 관리하고, `LikeId`의 `equals`/`hashCode` 보일러플레이트와 `product_id` 보조 인덱스가 필요하다.
- **재검토 조건**: 수량 검증이 반복되면 `Quantity` VO를 도입한다. 재고 경합이 문제가 되면 `Stock` 분리를 week3에서 검토한다.
- **기각한 대안**: `@ManyToOne` 연관관계, 읽기 전용 단방향 LAZY만 허용하는 절충안(규칙이 섞이고 soft delete 문제가 남음), `Like`의 대리키(`BaseEntity` 상속).

### D-10. 주문 확정은 단일 트랜잭션에서 도메인 행동이 각자 검증하고, 예외 시 전체 rollback한다

- **상황**: 확정은 재고, 포인트, 주문 상태를 함께 바꾸고 중간에 실패할 수 있다. 변경 전에 전체를 먼저 검사할지, 각 행동이 검증하고 실패 시 취소할지 정해야 했다.
- **결정**: Facade의 `@Transactional` 안에서 4-1의 순서로 도메인 행동이 각자 규칙을 검증한다. 예외가 나면 앞 단계의 변경이 모두 취소된다. 오류 우선순위는 4-1 표의 단계 순서이다. `PaymentResult`는 결제액과 결제 후 잔액이다.
- **비용**: fake Repository 기반 application 테스트에서는 실패 후 메모리 객체가 이미 변경돼 있을 수 있다. 그래서 실패 시 재고·잔액 유지는 실제 DB 통합 테스트에서 flush/clear 후 재조회로 확인한다.
- **재검토 조건**: 메모리에서도 부분 변경을 막아야 하는 요구가 생기거나, week3의 동시성 설계에서 검사 순서와 잠금 순서를 바꿀 때.
- **기각한 대안**: 변경 전에 전체 재고·잔액을 먼저 일괄 검사. 도메인 행동의 검증과 중복되어 규칙이 두 곳에 생긴다.

### D-11. 타인 리소스 접근은 소유자가 URL에 드러나는지에 따라 구분한다

- **상황**: 타인의 `userId`로 좋아요 목록을 요청하는 경우와, 타인의 주문을 조회·확정하는 경우를 어떤 오류로 처리할지 정해야 했다.
- **결정**: 소유자가 URL에 드러나 있으면(`/users/{userId}/likes`) `FORBIDDEN`(403), 조회를 해야만 알 수 있으면(타인 주문) `NOT_FOUND`(404)로 처리한다.
- **근거**:
  - `userId ≠ 요청자`는 조회 없이 비교만으로 판단되므로 사용자 존재 여부가 노출되지 않는다. 이때 404를 주면 "그 사용자가 없다"는 거짓 정보가 된다.
  - 타인 주문은 조회가 있어야 판단되므로, 404로 숨겨야 순차 ID로 주문 존재 여부를 열거하지 못한다.
  - 클라이언트 처리도 다르다. 타인 주문은 없는 주문과 같이 목록으로 보내면 되고, 타인 `userId` 호출은 재시도해도 소용없는 호출 오류이다.
- **비용**: `ErrorType.FORBIDDEN`을 추가하고 `ErrorTypeHttpStatusMapper`에 403 매핑과 테스트를 갱신해야 한다. "타인" 처리가 두 가지가 된다.
- **재검토 조건**: 주문도 403으로 통일해야 하는 요구가 생기거나 운영 인증을 도입할 때.
- **기각한 대안**: 모두 `NOT_FOUND`는 목록 요청에서 의미가 부정확하다. 모두 `FORBIDDEN`은 순차 ID로 주문 존재 여부를 알 수 있다.
- **구현 메모**: `ErrorType` 추가 항목은 `ORDER_ALREADY_CONFIRMED`, `OUT_OF_STOCK`, `INSUFFICIENT_POINT`, `PRODUCT_UNAVAILABLE`, `FORBIDDEN` 5개이다.

### D-12. 좋아요 수는 관계에서 집계하고, 이름·가격 범위는 정해진 값으로 검증한다

- **상황**: 상품 목록·상세의 좋아요 수와 `likes_desc` 정렬을 어떻게 구할지, 그리고 이름·가격의 허용 범위를 정해야 했다.
- **결정 (좋아요 수)**: Like 행에서 집계한다. 상세는 `count(*) ... where product_id = ?`(`product_id` 보조 인덱스), 목록은 페이지의 `productIds`로 `group by product_id`를 일괄 집계한다. `likes_desc` 정렬은 Product 쿼리에서 Like 집계를 left join하고 `order by coalesce(cnt, 0) desc, id desc`로 수행한다. 이 쿼리는 Product만 반환해 domain 타입을 유지하고, 표시용 수는 일괄 집계로 따로 조회한다. `Product.likeCount` 같은 카운터는 두지 않는다.
- **결정 (범위)**: 값은 아래와 같다.

  | 대상 | 규칙 | 위반 시 |
  |---|---|---|
  | Brand 이름 | 앞뒤 공백 제거 후 1~50자, 공백뿐인 문자열 불가 | `INVALID_INPUT` |
  | Product 이름 | 앞뒤 공백 제거 후 1~100자, 공백뿐인 문자열 불가 | `INVALID_INPUT` |
  | Product 가격 | 1원 이상 1,000,000,000원 이하 (0원 상품 불가) | `INVALID_INPUT` |
  | 재고 | 0 이상 정수(`int`) | `INVALID_INPUT` |
  | 브랜드 이름 중복 | 허용 (유일성 제약 없음) | - |

  브랜드·상품은 설명 같은 부가 필드 없이 최소 필드로 시작한다.
- **근거**: 관계에서 집계하면 관계와 카운터를 일치시킬 책임이 없고, 과제도 관계에서 조회하라고 한다. 가격 상한은 `단가 × 수량` 합계가 `long`을 넘지 않게 하려는 것이다. 이름 중복을 허용하면 `CONFLICT` 처리와 유니크 제약이 필요 없다.
- **비용**: `likes_desc`는 Like 행 수에 비례해 느려질 수 있고, 집계가 정렬용과 표시용으로 두 번 일어난다.
- **재검토 조건**: `likes_desc` 목록이 느려지면 카운터 비정규화를 week3 동시성 설계와 함께 검토한다. 이름 유일성이 필요해지면 `CONFLICT`와 유니크 제약을 추가한다.
- **기각한 대안**: 카운터 비정규화(경합과 일치 책임이 생김), 가격 0원 허용(무료 상품 요구가 없음), 브랜드 이름 유일성(과제 요구 없음).

### D-13. 브랜드 삭제 거절은 CONFLICT + 메시지로, 삭제 API 성공은 200 + ApiResponse.success()로 처리한다

- **상황**: 삭제되지 않은 상품이 연결된 브랜드의 삭제 거절에 어떤 코드를 쓸지, 삭제 API의 성공 응답을 어떻게 할지 정해야 했다.
- **결정 (거절)**: `CONFLICT`에 메시지를 직접 지정한다. 예: "연결된 상품이 있어 브랜드를 삭제할 수 없습니다. 상품을 먼저 삭제해 주세요." `CONFLICT`는 현재 상태와 충돌하는 범용 코드로 정의한다.
- **결정 (성공)**: HTTP 200과 `ApiResponse.success()`로 응답한다. 이미 삭제됐거나 없는 대상의 삭제는 `NOT_FOUND`이다. 좋아요 취소는 멱등 성공이지만, 관리자 삭제는 존재하는 대상을 지우는 명시적 행위이고 삭제된 대상은 없는 것으로 취급한다 (D-5).
- **근거**: 관리자 화면이 이 오류에서 프로그램적으로 다르게 동작할 필요가 없고 메시지가 다음 행동을 안내하면 충분하다 (D-3 원칙 4). week3에서 이 거절이 "브랜드와 상품을 함께 삭제"로 바뀌어 사라질 동작이라 전용 코드를 추가하지 않는다. 모든 응답이 `ApiResponse` 형식이므로 204 No Content는 형식과 맞지 않는다.
- **비용**: `CONFLICT`의 기본 메시지가 맞지 않아 기본 메시지 변경과 "항상 메시지를 직접 지정" 규칙이 필요하다. 클라이언트가 원인을 코드가 아니라 메시지로 구분한다.
- **재검토 조건**: week3에서 일괄 삭제로 바뀌면 이 거절을 제거한다. 클라이언트가 이 오류에서 분기해야 하면 전용 코드를 둔다.
- **기각한 대안**: `BRAND_HAS_PRODUCTS` 전용 코드(사라질 동작에 코드를 추가), 204 No Content(`ApiResponse` 형식 불일치).

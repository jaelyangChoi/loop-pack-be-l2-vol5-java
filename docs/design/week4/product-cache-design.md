# 상품 목록 캐시 설계 (W4)

> 튜닝이 끝난 상품 목록 조회 한 경로에 Redis cache-aside를 붙인다. 무엇을 재사용하고, 무엇이 바뀌면 지우며, Redis가 틀리거나 멈추면 어떻게 동작하는지를 구현 전에 정한다.

근거 자료: 조회 계약·개선 결과는 [`product-query-design.md`](product-query-design.md), 측정 절차는 [`product-list-baseline.md`](product-list-baseline.md), 개념·반례는 `docs/note/week4/캐시-설계-참고.md`, 과제 요구는 `docs/note/week4/실습 과제.md` 3·4장.

## 1. 범위와 전제

| 항목 | 내용 |
|---|---|
| 캐시 대상 | 고객 `GET /api/v1/products`의 `sort=latest`·`price_asc` (브랜드 필터 유무, `page`, `size` 모두) |
| 제외 | `likes_desc`(좋아요마다 순서가 바뀌고, 튜닝으로 이미 약 1.9ms), 상품 상세, 인기상품 top 100, stampede 완화 |
| 계약 | 조회 계약은 `product-query-design.md` 2장 그대로다(정렬·동점·삭제 제외·없는 브랜드는 빈 목록·오류). 캐시는 이를 바꾸지 않는다 |
| 전제 | 튜닝 뒤 이 요청의 SQL 합계는 이미 약 1.2ms(브랜드 7 `price_asc`)다. 캐시가 줄이는 것은 SQL 4개 중 상품 페이지·총 개수·브랜드 조회 3개이고, 절대 시간 이득은 작다. 이득과 새 운영 책임(무효화·장애 처리)을 함께 측정해 기록한다 |

## 2. 캐시 후보 표

| 캐시 후보 | 응답 필드 | 영향을 주는 변경 | 무효화 |
|---|---|---|---|
| 목록 페이지 | 상품 `id·name·price`, 브랜드 `id·name`, `page·size·totalElements` | 상품 생성·수정·삭제, 브랜드 수정·삭제 | 목록 영역 전체 삭제 |
| (캐시 밖) `likeCount` | 상품별 좋아요 수 | 좋아요 등록·취소, 상품 삭제 | 캐시에 넣지 않고 hit마다 `product_like_counts`에서 읽는다 |

- **`likeCount`를 payload에서 뺀 이유**: 좋아요는 가장 잦은 쓰기다. 넣으면 좋아요마다 영역을 비워야 해 hit율이 무너진다. 읽는 쪽은 PK `IN` 조회 한 번이라 싸다.
- **사용자별 필드는 없다**: 이 응답은 요청자를 식별하지 않는 카탈로그 조회(`CLAUDE.md` 보안 설정 절)라 공용 캐시에 섞일 개인 정보가 없다. 키에 사용자를 넣지 않는다.
- **재고**: 고객 응답에 없으므로 재고 변경·주문 확정은 캐시에 영향이 없다. 주문 확정은 캐시를 읽지 않고 기존 DB 규칙(금액 보존, 재고, 결제)을 그대로 쓴다.
- **주문·결제**: 주문·결제 결과는 캐시 대상이 아니다. 결제 수단 변경(포인트 → PG 결제, week2 D-44)은 캐시 필드·무효화에 영향이 없다.

## 3. 키와 TTL

```
product:list:v1:brand={brandId|all}:sort={latest|price_asc}:page={n}:size={n}
```

- `v1`은 payload 형식 버전이다. 직렬화 계약을 바꾸면 올린다(DB 변경 감지 수단이 아니다).
- 영역 비우기 대상은 접두사 `product:list:`뿐이다. `FLUSHALL`은 쓰지 않는다.
- **TTL**: 기본 60초(설정값). 영역 비우기가 정상 동작하면 TTL은 마지막 방어선이다. 실패 시나리오 테스트는 짧은 TTL로 만료를 확인한다. 값은 측정 뒤 조정할 수 있다.
- 읽을 때 TTL을 연장하지 않는다(TTI 아님). 자주 읽히는 낡은 값이 남지 않게 한다.
- 기본 페이지 크기(20)와 깊은 페이지가 모두 키로 들어가므로, 재사용이 적은 깊은 페이지가 키를 늘릴 수 있다. TTL이 정리 수단이며, 필요하면 `page` 상한을 두는 것은 측정 뒤 판단한다(이번엔 두지 않는다).

## 4. 흐름

```
조회(latest|price_asc)
 ├ 캐시 조회 (Redis 오류 → 로그·메트릭, miss로 취급)
 │   ├ hit : 상품 id들로 product_like_counts 조회 (PK IN, SQL 1개)
 │   │        ├ 모든 id에 행이 있음 → 캐시 payload + likeCount 조합해 반환
 │   │        └ 빠진 id 있음(삭제됨) → 이 캐시를 버리고 아래 miss 경로로
 │   └ miss: 기존 경로(SQL 4개) → 결과 반환 직전 캐시 저장(실패는 무시)
 └ likes_desc → 기존 경로 그대로
```

- **삭제 보호(권위 확인)**: `product_like_counts` 행은 노출 중인 상품만 가지며 삭제와 같은 트랜잭션에서 지워진다(D-33·D-36). hit 때 이 조회로 "아직 노출 중인가"를 DB에서 다시 확인한다. 무효화가 실패해도 삭제된 상품이 응답에 나가지 않는다. hit의 SQL이 0개가 아니라 1개인 이유이며 이 비용을 측정에 포함한다.
- **보호 범위의 한계**: 이 확인은 삭제를 잡는다. 이름·가격·브랜드명·`totalElements`·순서는 잡지 못해 TTL 안에서 낡을 수 있다(6장). 삭제된 상품이 빠져 페이지가 `size`보다 적게 보이는 일은 없다. 빠진 id가 있으면 캐시를 버리고 DB로 다시 읽기 때문이다.
- **브랜드 삭제**: 브랜드가 삭제되면 연결 상품이 함께 삭제되어 카운터 행도 지워진다. 그 브랜드의 목록 캐시는 빠진 id 확인에 걸리거나 빈 목록이다. 브랜드가 삭제된 뒤 캐시된 "빈 목록"은 계약(없는 브랜드는 빈 목록)과 같다.
- **읽기·쓰기 연결**: `redisTemplateMaster`(`ReadFrom.MASTER`)로 읽고 쓴다. 기본 템플릿은 `REPLICA_PREFERRED`라 무효화 직후 replica의 옛 값을 읽을 수 있다. master로 읽어도 DB와 Redis의 원자성은 생기지 않으며 복제 지연이라는 한 가지 원인만 없앤다. 테스트 Redis는 master와 replica가 같은 컨테이너다.

## 5. 변경 책임: 무효화 경계

### 5-1. 쓰기 경로

| 쓰기 | 목록 payload 영향 | 무효화 |
|---|---|---|
| `AdminProductFacade.create` | 목록 구성·`totalElements` | 한다 |
| `AdminProductFacade.update` | 이름·가격, 가격순 위치 | 한다 |
| `AdminProductFacade.delete` | 목록 구성·`totalElements` | 한다 |
| `AdminBrandFacade.update` | 브랜드명 | 한다 |
| `AdminBrandFacade.delete` | 연결 상품 전체 삭제 | 한다 |
| `AdminProductFacade.changeStock`, `OrderFacade.create/confirm` | 없음(재고는 응답에 없음) | 하지 않는다 |
| `LikeFacade.like/unlike` | 없음(`likeCount`는 캐시 밖) | 하지 않는다 |
| `AdminBrandFacade.create` | 없음(상품이 없는 브랜드는 필터 결과가 빈 목록 그대로) | 하지 않는다 |

### 5-2. 방식: 영역 전체 비우기, commit 뒤

- 조합(브랜드×정렬×페이지×크기)이 많아 관련 키만 지우면 빠뜨리기 쉽다. 쓰기가 관리자 작업이라 드물므로 `product:list:` 영역 전체를 지운다(접두사 `SCAN` + `UNLINK`). 비운 직후 모든 목록이 miss가 되는 비용은 7장에서 측정한다.
- `@Transactional` 안에서 직접 지우면 commit 전에 다른 조회가 옛 값을 채울 수 있다(참고 노트 2-1). 그래서 Facade는 트랜잭션 안에서 이벤트를 발행하고, `@TransactionalEventListener(phase = AFTER_COMMIT)`이 commit 뒤에 `evictAll()`을 호출한다. rollback이면 이벤트가 전달되지 않아 캐시를 건드리지 않는다.
- Redis 예외는 어댑터가 잡아 로그·메트릭으로 남기고 삼킨다. DB는 이미 commit되었으므로 관리자 응답은 성공으로 둔다.

### 5-4. 포트 위치와 모양

포트는 그것을 쓰는 계층이 소유한다(DIP). 캐시를 쓰는 것은 유스케이스(`ProductFacade`)와 무효화 리스너뿐이고 domain Service는 캐시를 모르므로, 포트를 application에 두고 infrastructure가 구현한다(헥사고날의 출력 포트와 같은 배치). `Repository`가 domain에 있는 것은 domain Service가 쓰기 때문이며 같은 원칙이다.

```
application.product
  ProductListCache           포트: find(ProductListQuery) / save(ProductListQuery, ProductListSnapshot) / evictAll()
  ProductListQuery           조회 조건 record (brandId|null, sort, page, size)
  ProductListSnapshot        payload record (상품 id·name·price·brand{id,name}, page, size, totalElements). likeCount 없음
  ProductCatalogChanged      관리자 쓰기가 발행하는 이벤트
  ProductListCacheEvictor    @TransactionalEventListener(AFTER_COMMIT) → evictAll()
infrastructure.product
  ProductListRedisCache      구현: 키 형식·JSON 직렬화·TTL·SCAN+UNLINK·Redis 예외 처리
```

- 포트의 입출력은 application 타입뿐이다. 키 문자열·Redis 타입·TTL은 구현의 세부라 포트에 드러나지 않는다.
- 어댑터는 Redis 접근·역직렬화 실패를 잡아 로그·메트릭을 남기고 "없음"(miss)으로 돌려준다. Facade는 Redis 예외 타입을 모른다. DB 예외는 이 어댑터를 지나지 않는다.
- payload는 likeCount가 없어 `ProductDetailInfo`를 재사용하지 않고 전용 record를 둔다. hit 때 Facade가 카운터와 합쳐 `ProductDetailInfo`를 만든다.
- 이 배치로 `infrastructure → application` 의존이 처음 생긴다. week2 `commerce-design.md` 2-3에 "포트 구현에 한해 허용"으로 반영했다. ArchUnit의 기존 금지 규칙은 그대로다.

### 5-3. 남는 실패 창과 정책

| 장면 | 결과 | 정책 |
|---|---|---|
| commit 뒤 `evictAll` 실패 | 옛 payload가 TTL까지 남음 | 이름·가격·브랜드명·`totalElements`·순서의 낡음을 **캐시 채움 시점부터 TTL까지** 허용한다. 삭제된 상품은 4장 권위 확인으로 노출되지 않는다 |
| 이전 조회의 늦은 채움(이전 DB 읽기 → 변경 commit·무효화 → 이전 결과 저장) | 옛 payload가 새로 채워짐 | 위와 같다. 낡음의 상한은 (이전 조회의 지연) + TTL이다(참고 노트 1-3). 이 상한을 줄이려는 revision 방식은 쓰지 않는다(hit마다 DB 확인이 늘고 쓰기가 revision 행에서 경합) |
| Redis 장애 중 변경 | 무효화 호출이 실패해 옛 키가 부활 | 복구 조건: TTL 만료 또는 다음 성공한 무효화. 복구 완료 판정은 "변경 뒤 조회가 DB와 같은 payload를 돌려줌"이다 |

- 허용 범위는 **표시용 필드**로 한정한다. 삭제 비노출, 좋아요 소유권, 주문 금액·재고·결제 판단은 낡음 허용 대상이 아니다.
- 정상 변경(장애·동시 채움 없음) 뒤 시작한 순차 조회는 반드시 새 결과를 받는다. 이 계약과 위 장애 정책은 섞어 주장하지 않는다.
- 단순 TTL이 모든 요청의 시간 상한이라고 주장하지 않는다.

## 6. 장애 계약

- **대기 제한**: 현재 `RedisConfig`는 timeout을 정하지 않아 Lettuce 기본값(명령 60초)을 쓴다. `modules/redis`에 `connect`·`command` timeout 설정을 추가한다. 값은 상품 목록이 DB 우회로 응답하는 데 허용할 수 있는 수준(예: connect 200ms, command 300ms, 구현 때 확정)으로 둔다. 공용 모듈이라 다른 앱에도 같은 기본값이 적용된다는 영향을 결과에 적는다.
- **miss와 오류 구분**: miss는 정상, Redis 오류는 장애다. 둘을 같은 `null`로 다루지 않고 오류는 별도로 로그·메트릭(카운터)을 남긴다.
- **우회는 원본 조회**: Redis 오류는 DB 경로로 가서 정상 결과를 반환한다. 빈 목록·가격 0으로 바꾸지 않는다. 잡는 예외는 Redis 접근과 payload 역직렬화에 한정하고, DB 예외는 기존 오류 계약 그대로 전파한다(전체 `catch (Exception)` 금지).
- **주문**: Redis 장애로 주문의 결제·재고 처리를 우회하지 않는다. 주문 경로는 캐시를 쓰지 않는다.
- **복구**: Redis가 돌아오면 다음 조회부터 정상 miss → 저장으로 재채움된다. 장애 중 무효화가 실패한 옛 키가 남아 있으면 5-3의 복구 조건을 따른다.
- **DB 부하**: Redis가 멈추면 hit이던 요청이 모두 원본으로 간다. 이번에는 작은 통제 부하에서 우회 응답의 동일성과 지연 증가만 확인하고, 동시 요청 제한은 다루지 않는다.

## 7. 비용과 확인할 것

- 줄이는 비용: 상품 페이지·총 개수·브랜드 조회(SQL 4 → 1).
- 늘어나는 비용: hit 때 카운터 `IN` 조회 1개, Redis 왕복, JSON 직렬화, 관리자 쓰기 뒤 영역 비우기(`SCAN`), 비운 직후의 전체 miss, 장애·무효화 실패 처리 책임.
- 확인: 같은 입력의 DB-only·miss·hit 결과 일치, hit 요청의 SQL 수 1(테스트로 고정), 지연 분포(준비·측정 표본 수 기록), 영역 비운 직후의 요청 지연, Redis 중단 중 지연.
- DB 쿼리 개선만으로 충분한지 함께 판단해 `product-list-baseline.md` 5장에 기록한다.

## 8. 검증 시나리오

실제 MySQL·Redis(Testcontainers)를 쓴다. Redis 동작을 전부 Mock으로 대체한 테스트로 끝내지 않는다.

| 장면 | 확인 |
|---|---|
| DB-only·miss·hit | 정렬 id·가격·좋아요 수·페이지 정보 일치, hit의 SQL 1개 |
| 관리자 가격 수정 후 재조회 | 새 가격·가격순 위치 반영, 과거 주문 금액 유지 |
| 캐시에 담긴 상품 삭제·브랜드 삭제 | 고객 결과에서 제외, 다른 브랜드·과거 주문 유지 |
| 상품 생성·이름 수정·브랜드명 수정 | 반영 |
| 좋아요 등록·취소 | hit에서도 `likeCount` 반영 |
| DB 변경 rollback | 이전 결과 유지, 새 데이터로 오인할 캐시 없음 |
| commit 뒤 무효화 실패 | 관리자 응답 성공, DB commit 유지, 삭제 상품 비노출, 짧은 TTL 만료 뒤 DB와 일치 |
| 이전 조회의 늦은 채움 | 래치로 순서를 고정해 옛 재삽입을 재현, 삭제 비노출 유지, 만료 뒤 복구 |
| Redis 중단·재시작 | 제한된 대기 뒤 DB 결과, 재시작 뒤 정확한 재채움 |

기다림은 모두 유한한 timeout을 두고 worker를 정리한다. Redis 중단 실험은 테스트가 실제로 연결한 Testcontainer만 대상으로 하며, 공유 Redis에 `FLUSHALL`을 실행하지 않는다.

## 9. 결정 기록

| 번호 | 결정 | 상태 |
|---|---|---|
| D-37 | 캐시 대상은 `latest`·`price_asc` 목록이다. `likes_desc`·상세는 제외한다 | 확정 |
| D-38 | `likeCount`는 payload에 넣지 않고 hit마다 카운터 테이블에서 읽는다. 이 조회가 삭제된 상품을 걸러내는 권위 확인이다 | 확정 |
| D-39 | 무효화는 관리자 쓰기 commit 뒤 영역 전체 삭제이며, Redis 실패는 삼키고 TTL이 방어한다. revision 방식은 쓰지 않는다 | 확정 |
| D-40 | 표시 필드의 낡음은 TTL(기본 60초)까지 허용하고, 삭제 비노출·주문 판단은 허용하지 않는다 | 확정 |
| D-41 | 캐시는 `redisTemplateMaster`로 읽고 쓴다 | 확정 |
| D-42 | `modules/redis`에 connect/command timeout을 추가한다(공용 모듈 영향 있음) | 확정 |
| D-43 | 캐시 포트는 이를 쓰는 application에 두고 infrastructure가 구현한다. `infrastructure → application`은 포트 구현에 한해 허용한다 | 확정 |

## 10. 구현 결과

구현 후 채운다.

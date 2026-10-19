# 상품 목록 조회 성능 기준선과 개선 전후 비교 (W4)

개선 전 기준선을 기록하고, 인덱스·쿼리 튜닝 후와 캐시 도입 후를 같은 절차로 비교하는 문서다. 개선 설계와 선택 이유는 [`product-query-design.md`](product-query-design.md)에 있다.

## 1. 요약

SQL 합계 p50 기준이며 HTTP 시간이 아니다. 응답 체크섬 8개(4절)는 개선 전후 모두 같다.

| 요청 | 기준선 | 인덱스·쿼리 튜닝 후 | 캐시 도입 후 |
|---|---|---|---|
| **B. 브랜드 없는 `likes_desc`** | 약 **14.6~16.3ms** | 약 **1.9ms** (약 8배) | 미적용 |
| A. 브랜드 7 `price_asc` (균등 seed, 브랜드당 100개) | 약 1.2ms | 약 1.15ms (편차 안) | 미적용 |
| A, 한 브랜드에 5,000개 쏠림 | 약 3.6ms | 약 1.5~1.7ms (약 2.3배) | 미적용 |

- **B의 원인**: 후보 상품 1만 개마다 `likes`를 조회해 좋아요 55,640행을 집계하고 임시 테이블에서 전체를 정렬한 뒤 20개만 골랐다. 요청 시간의 약 85%가 이 목록 쿼리 하나다(N+1은 아니다).
- **B의 개선**: 상품별 좋아요 수를 `product_like_counts`에 두고 `(like_count DESC, product_id DESC)` 인덱스에서 상위 20개 id만 읽은 뒤 상품을 조회한다. 읽는 좋아요 행이 55,640 → 20이다.
- **A의 원인**: 인덱스가 `brand_id`뿐이라 브랜드의 상품을 모두 읽고 `filesort`로 정렬했다. 브랜드당 100개에서는 차이가 안 보이고 쏠리면 드러난다.
- **A의 개선**: `(brand_id, deleted_at, price, id DESC)`로 교체해 인덱스 순서대로 20행만 읽고 멈춘다.
- **새로 생긴 비용**: 좋아요 등록·취소가 카운터 행을 함께 갱신한다(같은 상품 동시 처리량 약 3,100~3,900/s → 1,300~1,400/s). 브랜드 없는 `likes_desc`의 SELECT는 4 → 5개다.

## 2. 측정 방법

| 항목 | 값 |
|---|---|
| DB | 로컬 docker MySQL 8.0.46 (`docker/infra-compose.yml`), 스키마 `loopers`. `local` 프로필 앱이 `ddl-auto: create`로 생성 |
| 데이터 | `src/test/resources/sql/perf-seed.sql` (난수 없음) |
| 지연 | SQL만 순차 실행, 준비 20회·측정 100회, 3라운드 (`ProductQueryLatencyBenchmarkTest`) |
| 한계 | 단일 로컬 DB, 동시 요청 없음, HTTP 지연 미측정, 읽기·쓰기를 섞은 부하 미측정. p95·max는 로컬 잡음이 크다 |

**seed**: 브랜드 100개, 사용자 1,000명, 노출 상품 10,000개(브랜드 `(i%100)+1`, 가격 `((i*37)%100000)+100`), 삭제 상품 200개. 좋아요 55,808건(`i%11=0`이면 0개, `i%97=0`이면 100~300개, 그 외 `1+(i%7)`개로 동점 다수). 검산: 전체 10,000건·가격 합 472,685,000, 브랜드 7은 100건·4,647,200. 가격 동점은 없다(동점 규칙은 단위 테스트가 검증).

**측정 요청** (`GET /api/v1/products?`)

| 구분 | 요청 | 이유 |
|---|---|---|
| **A. 과제 필수** | `brandId=7&sort=price_asc&page=0&size=20` | 브랜드 필터 + 가격순, 기본 page·size |
| **B. 병목** | `sort=likes_desc&page=0&size=20` (브랜드 없음) | 후보 전체의 좋아요를 집계·정렬한 뒤 20개를 고르는 가장 비싼 요청 |
| 참고 | `brandId=7&sort=likes_desc` | 비교용 |

기준선의 요청당 SELECT는 4개다: 상품 페이지, 총 개수, 브랜드 `IN`, 좋아요 수 `IN … group by`.

**재현 절차**

```bash
# 1) local 프로필 앱 기동(스키마 생성) 후 seed 적재
./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local'
docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < apps/commerce-api/src/test/resources/sql/perf-seed.sql

# 2) 응답 정규화 후 MD5 (brandId 가 없는 요청은 해당 파라미터를 뺀다)
curl -s 'http://localhost:8080/api/v1/products?sort=likes_desc&page=0&size=20' \
  | jq -S -c '.data' | python3 -c 'import sys,hashlib;print(hashlib.md5(sys.stdin.buffer.read()).hexdigest())'

# 3) 지연
PERF_JDBC_URL='jdbc:mysql://localhost:3306/loopers' PERF_JDBC_USER=application PERF_JDBC_PASSWORD=application \
  ./gradlew :apps:commerce-api:test --tests '*ProductQueryLatencyBenchmarkTest' -i
```

## 3. 기준선 (개선 전)

`EXPLAIN ANALYZE`는 계측 오버헤드가 있어 JDBC 지연보다 크게 나온다. 읽은 행과 접근 경로를 비교하는 용도이며 key 이름은 정답으로 고정하지 않는다.

| 요청·쿼리 | 접근 | 읽은 행 | 정렬 | 시간 |
|---|---|---|---|---|
| A 목록 | `idx_products_brand_id` ref → `deleted_at` 필터 | 102 → 100 | `Using filesort` | 약 0.12ms |
| A count | 같은 인덱스 ref → 필터 → 집계 | 102 → 100 | 없음 | 약 0.09ms |
| **B 목록** | `products` 인덱스 스캔, 상품마다 `likes` 기본키 접두 조회(10,000회) | 상품 10,200 → 10,000, 좋아요 **55,640** | `Using temporary; Using filesort` (전체 정렬 후 top 20) | 약 **18~25ms** |
| B count | `products` 전체 스캔 | 10,200 → 10,000 | 없음 | 약 1.7ms |
| B 좋아요 수 | `likes` 기본키 range (인기 상품 20개) | 5,950 | 없음 | 약 0.9~1.0ms |

## 4. 인덱스·쿼리 튜닝 후

변경: ① `idx_products_brand_id` → `(brand_id, deleted_at, price, id DESC)` ② `product_like_counts` 테이블 + `(like_count DESC, product_id DESC)` 인덱스, 브랜드 없는 `likes_desc`는 id 선택 → 상품 조회 2단계.

**실행 계획**

| 요청·쿼리 | 접근 | 읽은 행 | 정렬 | 시간 |
|---|---|---|---|---|
| A 목록 | `idx_products_brand_price_id` lookup (brand_id=7) | 20 | 없음(인덱스 순서) | 약 0.03ms |
| A count | 같은 인덱스 커버링 | 100 | 없음 | 약 0.02ms |
| **B ① id 선택** | `idx_product_like_counts_ranking` 커버링 스캔 | **20** | 없음(인덱스 순서) | 약 **0.007ms** |
| B ② 상품 조회 | `products` PK range | 20 | 없음 | 약 0.3ms |

B의 합계에서 가장 큰 비용은 변하지 않은 브랜드 없는 count(약 1.15ms)다.

**지연 (SQL 합계, ms)** 라운드 1 / 2 / 3

| 요청 | 구분 | p50 | p95 | max |
|---|---|---|---|---|
| A 브랜드 7 `price_asc` | 전 | 1.220 / 1.209 / 1.181 | 2.086 / 2.276 / 1.910 | 2.795 / 3.539 / 2.871 |
| | 후 | 1.094 / 1.144 / 1.244 | 2.439 / 2.092 / 2.080 | 3.686 / 3.219 / 2.352 |
| 브랜드 7 `likes_desc` | 전 | 1.024 / 0.947 / 0.945 | 1.186 / 1.071 / 1.089 | 1.394 / 1.126 / 2.625 |
| | 후 | 0.802 / 0.832 / 0.848 | 1.106 / 1.135 / 0.977 | 1.438 / 3.980 / 1.088 |
| **B 브랜드 없는 `likes_desc`** | 전 | **14.700 / 15.085 / 14.643** | 16.554 / 15.614 / 15.441 | 22.569 / 17.939 / 15.965 |
| | 후 | **1.861 / 1.888 / 2.982** | 2.402 / 2.141 / 4.276 | 3.392 / 3.592 / 4.952 |
| A, 브랜드 7에 5,000개 쏠림 | 전 → 후 | 3.61~3.69 → 1.46~1.72 | 미측정 | 미측정 |

- B 후 3라운드는 네 구간이 모두 1.5~2배 느려 로컬 환경 잡음으로 보인다. 같은 환경의 다른 측정(1.81~1.94ms)은 1·2라운드와 같고, 개선 전과의 차이는 이 잡음보다 훨씬 크다.
- A는 균등 seed(브랜드당 100행)에서 전후 분포가 겹친다. 쏠린 브랜드에서 `filesort`가 사라지고 count가 커버링이 되어 차이가 난다.
- 쏠린 브랜드의 `likes_desc`는 브랜드 크기에 비례한다(합계 약 4.2~4.9ms, 개선 전 값은 측정하지 않음).

**체크섬** 앱 응답의 `data`를 키 정렬·압축 JSON으로 정규화한 MD5. 개선 전후 8개가 모두 같다. 다르면 JSON을 `diff`해 다른 행·필드를 찾고, 달라지면 성능 개선으로 세지 않는다.

| 요청 (`/api/v1/products?`) | MD5 |
|---|---|
| `brandId=7&sort=price_asc&page=0&size=20` (A) | `4e83a284bbea1bb53364ce1520419cb9` |
| `brandId=7&sort=price_asc&page=4&size=20` | `2cfd7c4d0c847ee77e398ed0a2c7343d` |
| `brandId=7&sort=likes_desc&page=0&size=20` | `e1c02f0f9f3f52dabcfe9d03eba594f1` |
| `brandId=7&sort=likes_desc&page=2&size=20` | `8b1b6337e24aa1f02992fbcc7a4eaebc` |
| `brandId=7&sort=latest&page=0&size=20` | `ab077b2afa82dde6658aadca05026354` |
| `sort=likes_desc&page=0&size=20` (B) | `3593d398ea053bd8d6a2312d8208be4c` |
| `sort=likes_desc&page=2&size=20` | `58cd6dff94bc2b1b61977123ff372151` |
| `sort=likes_desc&page=499&size=20` (마지막 페이지) | `1651eaed56555e7303c5a40f5843dad6` |

## 5. 캐시 도입 후

미적용. 캐시를 설계·구현한 뒤 같은 seed·요청·절차로 miss/hit 지연과 원본 쿼리 감소를 이 절에 추가한다.

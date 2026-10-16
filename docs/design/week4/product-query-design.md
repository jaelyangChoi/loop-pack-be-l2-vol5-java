# 상품 조회 SQL·인덱스 개선 설계 (W4)

> 기존 상품 목록 조회의 병목을 **조회 계약을 바꾸지 않고** 줄이고, 방법은 측정으로 고른다. 캐시는 이 문서의 범위 밖이며 이 개선이 끝난 뒤 별도로 설계한다.

근거 자료: 측정 원자료와 재현 절차는 [`product-list-baseline.md`](product-list-baseline.md). 과제 요구는 `docs/note/week4/실습 과제.md` 1·2장, 개념은 `발제.md`.

## 1. 범위

| 구분 | 내용 |
|---|---|
| 포함 | **A** 브랜드 필터 + `price_asc` 목록의 인덱스(과제 필수) / **B** 브랜드 없는 `likes_desc` 목록의 비용 |
| 제외 | 캐시, 커서·`limit` 계약 교체, 옵티마이저 힌트 |
| 이미 반영 | `likes` 기본키를 `(product_id, user_id)`로 고정(`legacy` 컬럼 정렬 전략), `idx_likes_product_id` 제거, `idx_likes_user_id` 추가. week2 D-12·D-17·D-18의 서술을 정정함 |

## 2. 조회 계약 (개선 중 바꾸지 않는다)

| 항목 | 계약 |
|---|---|
| 입력 | `brandId`(선택), `sort`=`latest`(기본)\|`price_asc`\|`likes_desc`(소문자 정확 일치), `page`(0 이상, 기본 0), `size`(1~100, 기본 20) |
| 정렬 | `latest`: id 내림차순 / `price_asc`: 가격 오름차순, 동점은 id 내림차순 / `likes_desc`: 좋아요 수 내림차순, 동점은 id 내림차순. **후보 전체를 정렬한 뒤 페이지를 자른다** |
| 필터 | 삭제 상품 제외. 없거나 삭제된 브랜드는 오류가 아니라 빈 목록(200) |
| 응답 | `{id, name, price, brand:{id,name}, likeCount}` + `PageResponse`(`content, page, size, totalElements, totalPages`). 재고·시각은 없음. `totalElements`는 좋아요 행이 아니라 상품 수 |
| 오류 | 잘못된 `sort`·`page`·`size` → `INVALID_INPUT` |

## 3. 기준선

환경: 로컬 docker MySQL 8.0.46, `local` 프로필 앱이 만든 스키마, SQL만 순차 측정(준비 20·측정 100, 3라운드).

- **seed**(`perf-seed.sql`, 난수 없음): 상품 10,000개(브랜드 `(i%100)+1`, 가격 `((i*37)%100000)+100`), 브랜드 100개, 사용자 1,000명, 삭제 상품 200개. 좋아요 55,808건: 상품 i의 좋아요 수는 `i%11=0`이면 0개, `i%97=0`이면 100~300개(인기), 그 외 `1+(i%7)`개(동점 다수). 검산: 전체 10,000건·가격 합 472,685,000, 브랜드 7은 100건·4,647,200. 가격은 서로 달라 동점이 없다(동점 규칙은 기존 단위 테스트가 검증).
- **요청당 SELECT 4개**: ① 상품 페이지 ② 총 개수 ③ 브랜드 `IN` ④ 좋아요 수 `IN … group by`. 목록 단위 일괄 조회라 N+1은 없다.

| 요청 | 합계 p50 | 읽은 양 / 계획 |
|---|---|---|
| **A** `brandId=7&sort=price_asc` | 약 1.2ms (목록 쿼리 0.35ms) | `idx_products_brand_id` ref 102행 → 필터 100행 → `Using filesort` |
| **B** `sort=likes_desc` (브랜드 없음) | 약 14.6~15.1ms (목록 쿼리 12.4~12.7ms) | `products` 10,000행을 돌며 `likes` 10,000회 조회 → 좋아요 55,640행 집계 → `Using temporary; Using filesort` |
| 참고 브랜드 없는 `price_asc` | `EXPLAIN ANALYZE` 약 2.5~2.8ms | `products` 전체 스캔 + 1만 행 `Using filesort` |

- **A는 브랜드 하나가 100행이라 개선 여지가 작다.** 요청 시간은 쿼리 비용보다 SELECT 4회의 왕복에 가깝다. 그래도 과제 필수 대상이므로 인덱스를 설계한다.
- **B가 실제 병목**이다. 비용 대부분이 후보 전체의 좋아요를 집계하고 정렬하는 데 있다.
- **결과 비교**: 응답을 정규화(`jq -S`)해 MD5를 비교하는 체크섬 8개(baseline 6장). 하나라도 다르면 개선으로 세지 않는다.

## 4. 개선 설계

### 4-A. products 인덱스

쿼리 모양: `where deleted_at is null and brand_id = ? order by price, id desc limit 20`, count는 같은 `where`.

- `brand_id`는 동등 조건이라 맨 앞, `deleted_at is null`은 상수라 그 다음에 두어도 뒤의 `price, id` 정렬 순서가 유지된다.
- 정렬이 `price asc, id desc`로 방향이 섞여 있다. 보조 인덱스에는 PK `id`가 오름차순으로 붙으므로 `id desc`를 인덱스만으로 얻으려면 `id DESC`를 명시한 내림차순 인덱스(MySQL 8)가 필요하다.
- 한 브랜드의 상품이 100행이라 정렬을 인덱스로 받는 이득은 작다. **실제로 읽은 행과 지연이 줄어드는지로 고른다.**

| 후보 | 기대 | 남는 비용 |
|---|---|---|
| C0 현행 `(brand_id)` | 기준 | 100행 filesort |
| C1 `(brand_id, deleted_at, price, id DESC)` | 필터·정렬·limit을 인덱스로 처리, count 커버링 | 가장 넓은 인덱스, 가격 수정·삭제 때 갱신 |
| C2 `(brand_id, price, id DESC)` | 정렬을 인덱스로, 삭제 필터는 행에서 | `deleted_at`은 행을 읽어 걸러냄 |
| C3 `(brand_id, price)` | 가장 단순 | 동점 `id desc`는 filesort로 남을 수 있음 |
| C4 전역 `(deleted_at, price, id DESC)` | 브랜드 없는 `price_asc`의 전체 스캔 제거 | 인덱스 하나 추가. **효과가 클 때만 채택** |

선택 기준: 읽은 행·`filesort` 제거·지연 차이가 라운드 편차 밖일 것, 단순한 쪽 우선, 쓰기·공간 비용 허용 가능할 것.
**게이트**: `idx_products_brand_id`를 교체하면 브랜드 삭제의 `FOR UPDATE`와 상품 등록의 `FOR SHARE` 잠금 범위(week3 D-27)가 달라질 수 있다. `LockBehaviorCharacterizationTest` 등 잠금 테스트가 그대로 통과해야 교체한다.

### 4-B. `likes_desc` (브랜드 없음)

| 후보 | 아이디어 | 사전 근거 / 비용 |
|---|---|---|
| B0 현행 | `products left join likes group by` | `EXPLAIN ANALYZE` 19~23ms |
| B1 파생 테이블 선집계 | `likes`를 `product_id`별로 먼저 집계(PK 커버링 1회 스캔)한 뒤 `products`와 join | 사전 비교 약 16ms, 상위 20개 해시 동일. 쓰기·정합성 영향 없음 |
| B4a 카운터 컬럼 | `products.like_count` + 인덱스 `(deleted_at, like_count DESC, id DESC)`. 좋아요 등록·취소 때 같은 TX에서 실제로 바뀐 경우에만 ±1 | 읽기는 상위 20행만 읽을 것으로 예상(가설). 쓰기: 상품 행 X잠금(관리자 수정·주문 확정과 직렬화, 인기 상품 경합), 정합성 책임, D-12 변경 |
| B4b 별도 집계 테이블 | `product_like_counts(product_id, cnt)`를 같은 TX에서 갱신 | `products` 행 잠금은 피하나 테이블·join 추가. 인기 상품 행 경합은 남음 |
| 제외 | B2 상관 서브쿼리(B1과 같은 비용), B3 상위 N 집계 후 0개 상품 패딩(페이지 경계·삭제 필터 의미 위험) | |

**측정 프로토콜**(로컬 seed에 후보 DDL을 임시 적용 후 원복):
1. 읽기: 후보별 `EXPLAIN`·`EXPLAIN ANALYZE`, JDBC 지연 3라운드. 상위 20개와 페이지 정보가 B0와 같아야 한다.
2. 쓰기(카운터 후보만): 좋아요 등록·취소 TX 시간(유무 비교), **같은 상품에 대한 동시 좋아요**의 처리량·대기, 카운터와 `count(likes)`의 일치.
3. 결정: 읽기 개선이 쓰기 비용과 정합성 책임을 정당화하는지 수치로 비교한다. 읽기 차이가 라운드 편차 안이면 쓰기 영향이 없는 단순한 쪽을 택한다.
   **카운터가 이기면 D-12(관계에서 집계)를 바꾸는 정책 변경이므로 코드 전에 측정 결과와 영향을 보고하고 확인을 받는다.**
4. 카운터 채택 시 위험: `Product`는 변경 감지로 모든 컬럼을 쓰므로 `like_count`는 `insertable/updatable=false`로 두고 원자적 `UPDATE`로만 바꾼다. 기존 데이터는 backfill SQL로 맞추고, ④번 쿼리와 `likeCount`의 출처가 바뀐다.

### 4-C. 유지하는 것

- **요청당 SELECT 4개 유지**: N+1이 없고, 합치려면 domain 타입 밖의 projection이 필요해 계층(D-12)에 영향이 크다.
- 내 좋아요 목록은 `user_id` 조건에 인덱스가 없어 `products` 전체 스캔(약 7ms)이었고 `idx_likes_user_id`로 약 0.16ms가 됐다(반영 완료).

## 5. 검증

- 응답 체크섬 8개가 개선 전과 모두 같다. 다르면 JSON을 `diff`해 다른 행·필드를 찾는다.
- 기존 `ProductRepositoryTest`·`ProductFacadeTest`·`ProductApiE2ETest`(정렬·동점·삭제·브랜드·count·페이지), 스키마 테스트(인덱스 컬럼 순서), 잠금 테스트, `./gradlew :apps:commerce-api:check`.
- 전후 `EXPLAIN ANALYZE`(읽은 행·loops·정렬 단계)와 JDBC 지연 3라운드. 차이가 라운드 편차 안이면 채택하지 않는다.

## 6. 결정 기록

| 번호 | 결정 | 상태 |
|---|---|---|
| D-30 | 조회 계약과 OFFSET 페이지를 유지한다 | 확정 |
| D-31 | A의 products 인덱스는 C0~C3을 측정해 고르고, 새 인덱스가 접두로 대신하면 `idx_products_brand_id`를 교체한다 | 측정 후 |
| D-32 | 브랜드 없는 `price_asc`용 전역 인덱스(C4)는 효과가 클 때만 채택한다 | 측정 후 |
| D-33 | B는 B0·B1·B4a·B4b를 읽기·쓰기·동시성으로 측정해 고른다. 카운터가 이기면 D-12 변경 확인을 받는다 | 측정 후 |
| D-34 | 요청당 SELECT 4개를 유지한다 | 확정 |
| D-35 | `likes` 인덱스 정리(기본키 `(product_id, user_id)`, `idx_likes_product_id` 제거, `idx_likes_user_id` 추가)는 반영했다 | 확정 |

## 7. 미측정과 다음 단계

- 미측정: 깊은 페이지, 한 브랜드에 상품이 몰린 경우, HTTP 전체 지연·동시 요청, 인덱스 추가의 쓰기 비용(카운터 후보 외).
- 다음 단계: 캐시(키·TTL·무효화·장애 계약)는 이 개선 결과를 입력으로 별도 문서에서 설계한다.

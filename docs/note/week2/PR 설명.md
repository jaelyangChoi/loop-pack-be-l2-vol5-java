# week2: 커머스 기본 기능 (브랜드·상품·좋아요·포인트·주문)

## 요약
설계 문서(`docs/design/week2/commerce-design.md`)의 계약대로 고객·관리자 기본 기능을 TDD로 구현했습니다. `./gradlew :apps:commerce-api:check`(Checkstyle + 테스트 + ArchUnit)가 통과하며 전체 테스트는 549건, 실패 0입니다.

## 구현 범위
| 구분 | 내용 |
|---|---|
| 고객 | 브랜드 상세, 상품 목록·상세(정렬·브랜드 필터), 좋아요 등록·취소·내 목록, 포인트 충전·조회, 주문 생성·확정·목록·단건 조회 |
| 관리자 | 브랜드·상품 CRUD, 재고 변경, 주문 목록(`userId` 필터)·단건 조회 |
| 공통 | `X-USER-ID` 식별 필터와 `@Requester`, 관리자 경계(`ADMIN` 역할, 403 `ApiResponse`), 페이징 상수(`PageRequests`), 요청 본문 파싱 오류 위치 안내 |

## 주요 설계 결정 (상세는 6장 D-xx)
- 트랜잭션은 Facade, 도메인 규칙은 Entity·VO(`Money`, `OrderLines` 등)에 둡니다.
- 주문은 DRAFT로 만들고 확정에서 재고·포인트를 차감합니다. 생성 때도 재고를 확인해 부족하면 409 `OUT_OF_STOCK`입니다 (D-20, D-21).
- 품목에 주문 당시 상품명·단가를 복사해 저장합니다.
- 요청자 식별은 필터 한 곳, 파라미터는 `@Requester Long`으로 받습니다 (D-19).
- 주문 목록은 `default_batch_fetch_size`로 SQL 3개 고정이며 `OrderListQueryCountTest`가 보장합니다 (D-22).

## TDD 기록
태스크마다 테스트 → Red(컴파일 실패 또는 단언 실패 확인) → 구현 → Green → `check` 순서로 진행했습니다. 이미 통과하는 사례는 억지로 실패시키지 않고 "Red 없이 통과"로 기록했습니다. 변이 확인 예: `default_batch_fetch_size=1`로 덮으면 쿼리 수 테스트가 6개로 실패합니다.

## 리팩터링
- order 모듈 정리: 메시지 조립·null 허용 변환·조회 실패 통합.
- `ApiControllerAdvice`의 JSON 경로 조립을 `RequestBodyErrorPaths`로 분리.
- `RequesterGuard` 대신 `@Requester` + `HandlerMethodArgumentResolver`로 경계에서 한 번 검증 (Facade null 테스트는 `RequesterArgumentResolverTest`로 이동).
- 페이징 기본값·설명을 `PageRequests` 상수로 통합.

## 알려진 한계 (의도적으로 남김)
- **동시성은 week3**: 같은 주문의 동시 확정, 재고 경합, 포인트 차감 경쟁은 다루지 않았습니다 (D-21).
- 좋아요 동시 중복 등록은 기본키 충돌로 실패할 수 있습니다. 오류 계약은 week3에서 정합니다 (D-4).
- 브랜드 삭제와 상품 등록이 동시에 오면 "상품이 있는 브랜드는 삭제 불가" 규칙이 깨질 수 있습니다 (D-16).
- 주문 생성의 재고 확인은 안내용입니다. 확정 시점에 다시 검사합니다.
- 식별 필터는 `CoreException`·헤더 형식 오류만 `ApiResponse`로 응답합니다. DB 장애 같은 다른 예외는 컨테이너 기본 500으로 나갈 수 있습니다 (D-19).
- `X-USER-ID`는 위조할 수 있어 로컬 실습용입니다. 운영 인증은 범위 밖입니다.
- `@Requester` 검증은 HTTP 경계에서만 합니다. Facade를 직접 호출하면 보호되지 않습니다.
- 관리자 API는 실제 HTTP에서는 로그인 수단이 없어 항상 403입니다. 테스트에서는 `ADMIN` 역할로 검증합니다.

## 확인 방법
```
./gradlew :apps:commerce-api:check   # Docker 필요(Testcontainers MySQL)
```

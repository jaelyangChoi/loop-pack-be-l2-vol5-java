-- 상품별 좋아요 수 집계 테이블. ProductLikeCount 엔티티의 @Table 과 같은 결과이며, ddl-auto: none 환경(운영 등)에 수동으로 적용할 때 쓴다.
--
-- 좋아요 수의 단일 출처이고 노출 중인 상품만 행을 가진다. 상품 저장(생성) 때 행이 만들어지고 삭제 처리 때 지워지며,
-- 좋아요 등록·취소 때 ±1 된다. 브랜드 없는 좋아요순 목록이 (like_count DESC, product_id DESC) 인덱스 순서로 읽는다.
--
-- 적용 순서(앱 배포 전): 1) 테이블·인덱스 생성 2) backfill 3) 정합성 확인 4) 앱 배포.
-- backfill 과 앱 배포 사이에 일어난 좋아요·상품 변경은 집계에 반영되지 않으므로, 배포 직전에 3)의 확인 쿼리를 다시 돌려
-- 불일치가 있으면 backfill(아래 UPSERT)을 다시 실행한다.

CREATE TABLE product_like_counts (
  product_id bigint NOT NULL,
  like_count bigint NOT NULL,
  PRIMARY KEY (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE INDEX idx_product_like_counts_ranking ON product_like_counts (like_count DESC, product_id DESC);

-- backfill: 노출 상품의 좋아요 수를 좋아요 관계에서 센다. 다시 실행해도 안전하다.
INSERT INTO product_like_counts (product_id, like_count)
SELECT p.id, (SELECT COUNT(*) FROM likes l WHERE l.product_id = p.id)
FROM products p
WHERE p.deleted_at IS NULL
ON DUPLICATE KEY UPDATE like_count = VALUES(like_count);

-- 정합성 확인(둘 다 0 이어야 한다)
-- 1) 노출 상품인데 행이 없거나, 삭제 상품인데 행이 남은 개수
-- SELECT COUNT(*) FROM products p LEFT JOIN product_like_counts c ON c.product_id = p.id
--  WHERE (p.deleted_at IS NULL AND c.product_id IS NULL) OR (p.deleted_at IS NOT NULL AND c.product_id IS NOT NULL);
-- 2) 좋아요 관계 수와 다른 행의 개수
-- SELECT COUNT(*) FROM product_like_counts c
--  WHERE c.like_count <> (SELECT COUNT(*) FROM likes l WHERE l.product_id = c.product_id);

-- 되돌리기
-- DROP TABLE product_like_counts;

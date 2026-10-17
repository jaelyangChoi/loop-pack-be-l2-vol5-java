package com.loopers.infrastructure.like;

import com.loopers.domain.common.Money;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.ProductLikeCountRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LikeRepositoryTest {

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private ProductLikeCountRepository productLikeCountRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void saveLike(Long userId, Long productId) {
        likeRepository.save(Like.create(userId, productId));
    }

    // 좋아요 수 집계는 상품 행에 속하므로 수를 세는 테스트는 실제 상품이 필요하다.
    private Long saveProduct() {
        return productRepository.save(Product.create(1L, "상품", new Money(1_000), new Stock(1))).getId();
    }

    // 관계 저장은 집계를 바꾸지 않으므로(증감은 LikeService 의 책임) 읽기 테스트는 집계를 직접 올려 둔다.
    private void increase(Long productId, int times) {
        for (int i = 0; i < times; i++) {
            productLikeCountRepository.increase(productId);
        }
    }

    @DisplayName("좋아요를 저장하고 확인할 때,")
    @Nested
    class SaveAndExists {
        @DisplayName("저장한 관계가 있다고 확인된다.")
        @Test
        void returnsTrue_whenLikeIsSaved() {
            // arrange
            saveLike(1L, 10L);

            // act & assert
            assertThat(likeRepository.exists(1L, 10L)).isTrue();
        }

        @DisplayName("저장하지 않은 관계는 없다고 확인된다. 사용자나 상품이 다르면 다른 관계다.")
        @Test
        void returnsFalse_whenLikeIsNotSaved() {
            // arrange
            saveLike(1L, 10L);

            // act & assert
            assertThat(likeRepository.exists(2L, 10L)).isFalse();
            assertThat(likeRepository.exists(1L, 11L)).isFalse();
        }
    }

    @DisplayName("좋아요를 삭제할 때,")
    @Nested
    class Delete {
        @DisplayName("삭제한 행 수를 반환하고, 이후에는 관계가 없다. 이미 없으면 0을 반환한다.")
        @Test
        void returnsDeletedRowCount() {
            // arrange
            saveLike(1L, 10L);

            // act
            int first = likeRepository.delete(1L, 10L);
            int second = likeRepository.delete(1L, 10L);

            // assert
            assertThat(first).isEqualTo(1);
            assertThat(second).isZero();
            assertThat(likeRepository.exists(1L, 10L)).isFalse();
        }

        @DisplayName("다른 사용자나 다른 상품의 관계는 지우지 않는다.")
        @Test
        void keepsOtherLikes() {
            // arrange
            saveLike(1L, 10L);
            saveLike(2L, 10L);
            saveLike(1L, 11L);

            // act
            likeRepository.delete(1L, 10L);

            // assert
            assertThat(likeRepository.exists(2L, 10L)).isTrue();
            assertThat(likeRepository.exists(1L, 11L)).isTrue();
        }
    }

    @DisplayName("상품의 좋아요 수를 셀 때,")
    @Nested
    class CountByProductId {
        @DisplayName("상품의 집계 값을 돌려준다. 다른 상품의 값은 섞이지 않는다.")
        @Test
        void returnsAggregatedCountOfProduct() {
            // arrange
            Long product = saveProduct();
            Long other = saveProduct();
            increase(product, 2);
            increase(other, 1);

            // act & assert
            assertThat(likeRepository.countByProductId(product)).isEqualTo(2);
        }

        @DisplayName("관계가 없으면 0이다. 집계 행이 없는 상품도 0이다.")
        @Test
        void returnsZero_whenNoLikes() {
            assertThat(likeRepository.countByProductId(saveProduct())).isZero();
            assertThat(likeRepository.countByProductId(999_999L)).isZero();
        }
    }

    @DisplayName("여러 상품의 좋아요 수를 한 번에 셀 때,")
    @Nested
    class CountByProductIds {
        @DisplayName("상품별로 집계하고, 좋아요가 없는 상품은 결과에 없다.")
        @Test
        void countsPerProduct_withoutProductsHavingNoLikes() {
            // arrange
            Long first = saveProduct();
            Long second = saveProduct();
            Long noLikes = saveProduct();
            increase(first, 2);
            increase(second, 1);

            // act
            Map<Long, Long> counts = likeRepository.countByProductIds(List.of(first, second, noLikes));

            // assert
            assertThat(counts).containsOnly(Map.entry(first, 2L), Map.entry(second, 1L));
        }

        @DisplayName("요청하지 않은 상품은 집계하지 않는다.")
        @Test
        void countsOnlyRequestedProducts() {
            // arrange
            Long first = saveProduct();
            Long second = saveProduct();
            increase(first, 1);
            increase(second, 1);

            // act
            Map<Long, Long> counts = likeRepository.countByProductIds(List.of(first));

            // assert
            assertThat(counts).containsOnly(Map.entry(first, 1L));
        }

        @DisplayName("빈 목록이면 빈 결과를 반환한다.")
        @Test
        void returnsEmpty_whenIdsAreEmpty() {
            assertThat(likeRepository.countByProductIds(List.of())).isEmpty();
        }
    }

    @DisplayName("좋아요 테이블 스키마는,")
    @Nested
    class Schema {
        @DisplayName("기본키 컬럼 순서는 (product_id, user_id)이다.")
        @Test
        void hasPrimaryKeyOrderedByProductIdThenUserId() {
            // 상품별 좋아요 수 집계(product_id 조건)가 기본키의 왼쪽 접두로 인덱스를 타는 것이 이 순서에 달려 있다.
            // 그래서 product_id 보조 인덱스(idx_likes_product_id)는 두지 않는다. 이 단언이 그 인덱스가 하던 보장을 대신한다.
            // act & assert
            assertThat(indexColumns("PRIMARY")).containsExactly("product_id", "user_id");
        }

        @DisplayName("내 좋아요 목록을 위한 user_id 보조 인덱스를 가진다.")
        @Test
        void hasUserIdIndex() {
            // act & assert
            assertThat(indexColumns("idx_likes_user_id")).containsExactly("user_id");
        }

        @DisplayName("좋아요 수 집계 테이블은 (like_count DESC, product_id DESC) 순위 인덱스를 가진다.")
        @Test
        void hasLikeCountRankingIndex() {
            // 브랜드 없는 좋아요순 목록이 이 인덱스 순서로 페이지의 상품 id 만 읽는다. 정렬 방향이 어긋나면 filesort 가 생긴다.
            List<String> columns = jdbcTemplate.queryForList(
                "select concat(column_name, ':', collation) from information_schema.statistics "
                    + "where table_schema = database() and table_name = 'product_like_counts' "
                    + "and index_name = 'idx_product_like_counts_ranking' order by seq_in_index",
                String.class
            );

            assertThat(columns).containsExactly("like_count:D", "product_id:D");
        }

        @DisplayName("좋아요 수 집계 테이블의 기본키는 product_id 이다.")
        @Test
        void hasProductIdPrimaryKeyOnLikeCounts() {
            List<String> columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.statistics "
                    + "where table_schema = database() and table_name = 'product_like_counts' "
                    + "and index_name = 'PRIMARY' order by seq_in_index",
                String.class
            );

            assertThat(columns).containsExactly("product_id");
        }

        // 인덱스의 컬럼을 인덱스 안의 순서대로 돌려준다.
        private List<String> indexColumns(String indexName) {
            return jdbcTemplate.queryForList(
                "select column_name from information_schema.statistics "
                    + "where table_schema = database() and table_name = 'likes' and index_name = ? "
                    + "order by seq_in_index",
                String.class,
                indexName
            );
        }
    }
}

package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import com.loopers.domain.product.Stock;
import com.loopers.support.fixture.ExposedProducts;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상품 목록 캐시(cache-aside)의 miss·hit 결과와 SQL 수를 실제 MySQL·Redis 로 확인한다(week4 product-cache-design.md 4장).
 * miss: 상품 페이지 + 총 개수 + 브랜드 + 좋아요 수 = 4. hit: 좋아요 수(삭제 확인 겸) = 1.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ProductCacheQueryCountTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private ProductListCache productListCache;

    @Autowired
    private ExposedProducts exposedProducts;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private LikeService likeService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    // 브랜드 2개에 상품 productCount 개씩(가격·좋아요 수 다양)을 만든다. 반환값은 첫 브랜드 id.
    private Long prepare(int productCount) {
        Brand brand = brandRepository.save(Brand.create("루퍼스"));
        Brand other = brandRepository.save(Brand.create("다른 브랜드"));
        for (int i = 0; i < productCount; i++) {
            for (Brand target : List.of(brand, other)) {
                Product product = exposedProducts.save(
                    Product.create(target.getId(), target.getName() + i, new Money(1_000L + (i * 7 % 5) * 100), new Stock(10)));
                for (long userId = 1; userId <= i % 3; userId++) {
                    likeService.like(userId, product.getId());
                }
            }
        }
        return brand.getId();
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }

    // 관리자 삭제와 같은 DB 변경(상품 삭제 + 집계 행 삭제)을 하되, 캐시는 건드리지 않아 무효화 실패와 같은 상태를 만든다.
    private void deleteWithoutEviction(Long productId) {
        transactionTemplate.executeWithoutResult(status -> {
            productRepository.findActive(productId).orElseThrow().delete();
            likeService.removeCounts(List.of(productId));
        });
    }

    @DisplayName("miss 와 hit")
    @Nested
    class MissAndHit {

        @DisplayName("miss 는 SQL 4개로 DB 에서 읽어 저장하고, hit 는 SQL 1개로 같은 결과를 돌려준다.")
        @ParameterizedTest(name = "sort={0}, 브랜드 지정={1}")
        @CsvSource({
            "latest, false",
            "latest, true",
            "price_asc, false",
            "price_asc, true"
        })
        void returnsSameResult_withOneStatementOnHit(String sort, boolean withBrand) {
            // arrange
            Long firstBrandId = prepare(15);
            Long brandId = withBrand ? firstBrandId : null;

            // act
            Statistics missStatistics = statistics();
            Page<ProductListItemInfo> miss = productFacade.getList(brandId, sort, 0, 10);
            long missStatements = missStatistics.getPrepareStatementCount();

            Statistics hitStatistics = statistics();
            Page<ProductListItemInfo> hit = productFacade.getList(brandId, sort, 0, 10);
            long hitStatements = hitStatistics.getPrepareStatementCount();

            // assert
            assertThat(missStatements).as("miss SQL 수").isEqualTo(4);
            assertThat(hitStatements).as("hit SQL 수").isEqualTo(1);
            assertThat(miss.getContent()).hasSize(10);
            assertThat(hit.getContent()).isEqualTo(miss.getContent());
            assertThat(hit.getNumber()).isEqualTo(miss.getNumber());
            assertThat(hit.getSize()).isEqualTo(miss.getSize());
            assertThat(hit.getTotalElements()).isEqualTo(miss.getTotalElements());
            assertThat(hit.getTotalPages()).isEqualTo(miss.getTotalPages());
        }

        @DisplayName("캐시를 비운 뒤 다시 DB 에서 읽은 결과도 hit 결과와 같다.")
        @Test
        void hitEqualsFreshDatabaseResult() {
            Long brandId = prepare(15);
            productFacade.getList(brandId, "price_asc", 1, 5); // miss 로 캐시를 채운다
            Page<ProductListItemInfo> hit = productFacade.getList(brandId, "price_asc", 1, 5);

            productListCache.evictAll();
            Statistics statistics = statistics();
            Page<ProductListItemInfo> fresh = productFacade.getList(brandId, "price_asc", 1, 5);

            assertThat(statistics.getPrepareStatementCount()).as("비운 뒤 다시 miss").isEqualTo(4);
            assertThat(hit.getContent()).isEqualTo(fresh.getContent());
            assertThat(hit.getTotalElements()).isEqualTo(fresh.getTotalElements());
        }

        @DisplayName("likes_desc 는 캐시하지 않아 매번 DB 에서 읽는다.")
        @Test
        void doesNotCache_whenSortIsLikesDesc() {
            Long brandId = prepare(15);
            productFacade.getList(brandId, "likes_desc", 0, 10);

            Statistics statistics = statistics();
            productFacade.getList(brandId, "likes_desc", 0, 10);

            assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
        }
    }

    @DisplayName("hit 의 좋아요 수")
    @Nested
    class LikeCountOnHit {

        @DisplayName("좋아요 등록·취소는 캐시를 비우지 않아도 hit 결과에 바로 반영된다.")
        @Test
        void reflectsLikeChanges_withoutEviction() {
            Long brandId = prepare(3);
            Long target = productFacade.getList(brandId, "latest", 0, 10).getContent().get(0).id();
            long before = likeCountOf(brandId, target);

            likeService.like(100L, target);
            long afterLike = likeCountOf(brandId, target);
            likeService.unlike(100L, target);
            likeService.unlike(1L, target);
            long afterUnlike = likeCountOf(brandId, target);

            assertThat(afterLike).isEqualTo(before + 1);
            assertThat(afterUnlike).isEqualTo(before - 1);
        }

        // 첫 조회 뒤라 항상 hit 경로로 읽는다(SQL 1개로 확인).
        private long likeCountOf(Long brandId, Long productId) {
            Statistics statistics = statistics();
            Page<ProductListItemInfo> page = productFacade.getList(brandId, "latest", 0, 10);
            assertThat(statistics.getPrepareStatementCount()).as("hit SQL 수").isEqualTo(1);
            return page.getContent().stream().filter(info -> info.id().equals(productId))
                .findFirst().orElseThrow().likeCount();
        }
    }

    @DisplayName("캐시된 상품의 삭제")
    @Nested
    class DeletedProduct {

        @DisplayName("캐시를 비우지 못해도 삭제된 상품은 나가지 않고, DB 에서 다시 읽어 페이지를 채운다.")
        @Test
        void excludesDeletedProduct_andRefillsPage_whenCacheIsStale() {
            // arrange
            Long brandId = prepare(15);
            Page<ProductListItemInfo> cached = productFacade.getList(brandId, "latest", 0, 10);
            Long deleted = cached.getContent().get(3).id();
            deleteWithoutEviction(deleted);

            // act
            Statistics statistics = statistics();
            Page<ProductListItemInfo> page = productFacade.getList(brandId, "latest", 0, 10);

            // assert
            assertThat(statistics.getPrepareStatementCount()).as("카운터 확인 1 + DB 다시 읽기 4").isEqualTo(5);
            assertThat(page.getContent()).extracting(ProductListItemInfo::id).doesNotContain(deleted).hasSize(10);
            assertThat(page.getTotalElements()).isEqualTo(cached.getTotalElements() - 1);
        }

        @DisplayName("다시 읽은 결과를 저장하므로 다음 조회는 다시 hit 다.")
        @Test
        void hitsAgain_afterRefill() {
            Long brandId = prepare(15);
            Long deleted = productFacade.getList(brandId, "latest", 0, 10).getContent().get(0).id();
            deleteWithoutEviction(deleted);
            productFacade.getList(brandId, "latest", 0, 10);

            Statistics statistics = statistics();
            Page<ProductListItemInfo> page = productFacade.getList(brandId, "latest", 0, 10);

            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            assertThat(page.getContent()).extracting(ProductListItemInfo::id).doesNotContain(deleted);
        }
    }

    @DisplayName("캐시 키는 정렬을 구분한다: 같은 조건의 latest 와 price_asc 는 서로의 캐시를 쓰지 않는다.")
    @Test
    void separatesCacheBySort() {
        Long brandId = prepare(15);
        productFacade.getList(brandId, "latest", 0, 10);

        Statistics statistics = statistics();
        Page<ProductListItemInfo> priceAsc = productFacade.getList(brandId, "price_asc", 0, 10);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
        assertThat(priceAsc.getContent()).extracting(ProductListItemInfo::price).isSorted();
        assertThat(productListCache.find(new ProductListQuery(brandId, ProductSort.PRICE_ASC, 0, 10))).isPresent();
    }
}

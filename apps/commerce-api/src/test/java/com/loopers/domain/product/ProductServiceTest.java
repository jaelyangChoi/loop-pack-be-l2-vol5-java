package com.loopers.domain.product;

import com.loopers.domain.common.Money;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import java.util.Map;

@SpringBootTest
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Product saveProduct(Long brandId, String name) {
        return productRepository.save(Product.create(brandId, name, new Money(89_000), new Stock(10)));
    }

    private Product saveDeletedProduct(Long brandId, String name) {
        Product product = Product.create(brandId, name, new Money(89_000), new Stock(10));
        product.delete();
        return productRepository.save(product);
    }

    @DisplayName("상품을 조회할 때,")
    @Nested
    class Get {
        @DisplayName("존재하는 상품이면 해당 상품을 반환한다.")
        @Test
        void returnsProduct_whenProductExists() {
            // arrange
            Product saved = saveProduct(1L, "러닝화");

            // act
            Product found = productService.get(saved.getId());

            // assert
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getName()).isEqualTo("러닝화");
        }

        @DisplayName("존재하지 않는 상품이면 NOT_FOUND 로 거절한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> productService.get(999L));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(exception.getCustomMessage()).isEqualTo("존재하지 않는 상품입니다.");
        }

        @DisplayName("삭제된 상품이면 NOT_FOUND 로 거절한다.")
        @Test
        void throwsNotFound_whenProductIsDeleted() {
            // arrange
            Product deleted = saveDeletedProduct(1L, "삭제됨");

            // act
            CoreException exception = assertThrows(CoreException.class, () -> productService.get(deleted.getId()));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 등록할 때,")
    @Nested
    class Create {
        @DisplayName("저장하고, 저장된 상품을 반환한다.")
        @Test
        void savesProduct() {
            // act
            Product created = productService.create(3L, "  러닝화  ", new Money(89_000), new Stock(10));

            // assert
            Product found = productRepository.findActive(created.getId()).orElseThrow();
            assertThat(found.getBrandId()).isEqualTo(3L);
            assertThat(found.getName()).isEqualTo("러닝화");
            assertThat(found.getPrice()).isEqualTo(new Money(89_000));
            assertThat(found.getStock()).isEqualTo(new Stock(10));
        }

        @DisplayName("값이 유효하지 않으면 INVALID_INPUT 으로 거절하고, 저장하지 않는다.")
        @Test
        void rejectsWithInvalidInput_andSavesNothing_whenNameIsBlank() {
            // act
            CoreException exception = assertThrows(CoreException.class,
                () -> productService.create(1L, "   ", new Money(89_000), new Stock(10)));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(productRepository.findAllActive(PageRequest.of(0, 10)).getTotalElements()).isZero();
        }
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class GetList {
        @DisplayName("삭제되지 않은 상품을 최신순으로 반환한다.")
        @Test
        void returnsActiveProducts_newestFirst() {
            // arrange
            Product first = saveProduct(1L, "첫째");
            saveDeletedProduct(1L, "삭제됨");
            Product third = saveProduct(1L, "셋째");

            // act
            Page<Product> page = productService.getList(PageRequest.of(0, 10));

            // assert
            assertThat(page.getContent()).extracting(Product::getId)
                .containsExactly(third.getId(), first.getId());
        }
    }

    @DisplayName("사용자가 좋아요한 상품 목록을 조회할 때,")
    @Nested
    class GetLikedList {
        @DisplayName("삭제되지 않은 상품 중 내가 좋아요한 것만 반환한다.")
        @Test
        void returnsMyLikedActiveProducts() {
            // arrange
            Product liked = saveProduct(1L, "좋아요함");
            Product deleted = saveDeletedProduct(1L, "삭제됨");
            saveProduct(1L, "좋아요 안 함");
            likeRepository.save(Like.create(1L, liked.getId()));
            likeRepository.save(Like.create(1L, deleted.getId()));

            // act
            Page<Product> page = productService.getLikedList(1L, PageRequest.of(0, 10));

            // assert
            assertThat(page.getContent()).extracting(Product::getId).containsExactly(liked.getId());
        }
    }

    @DisplayName("정렬과 브랜드 조건으로 상품 목록을 검색할 때,")
    @Nested
    class Search {
        @DisplayName("브랜드 조건과 정렬을 적용해 삭제되지 않은 상품을 반환한다.")
        @Test
        void appliesBrandAndSort() {
            // arrange
            Product cheap = productRepository.save(Product.create(1L, "싼 것", new Money(1_000), new Stock(1)));
            Product expensive = productRepository.save(Product.create(1L, "비싼 것", new Money(9_000), new Stock(1)));
            productRepository.save(Product.create(2L, "다른 브랜드", new Money(500), new Stock(1)));

            // act
            Page<Product> page = productService.search(1L, ProductSort.PRICE_ASC, PageRequest.of(0, 10));

            // assert
            assertThat(page.getContent()).extracting(Product::getId).containsExactly(cheap.getId(), expensive.getId());
        }
    }

    @DisplayName("id 목록으로 상품을 한 번에 조회할 때,")
    @Nested
    class GetAllActiveByIds {
        @DisplayName("id 로 찾을 수 있게 돌려주고, 삭제됐거나 없는 상품은 담기지 않는다.")
        @Test
        void returnsMapOfActiveProducts() {
            // arrange
            Product active = saveProduct(1L, "러닝화");
            Product deleted = saveDeletedProduct(1L, "삭제됨");

            // act
            Map<Long, Product> products = productService.getAllActiveByIds(
                List.of(active.getId(), deleted.getId(), 999_999L)
            );

            // assert
            assertThat(products).containsOnlyKeys(active.getId());
            assertThat(products.get(active.getId()).getName()).isEqualTo("러닝화");
        }

        @DisplayName("id 목록이 비어 있으면 빈 맵을 돌려준다.")
        @Test
        void returnsEmptyMap_whenIdsAreEmpty() {
            assertThat(productService.getAllActiveByIds(List.of())).isEmpty();
        }
    }
}

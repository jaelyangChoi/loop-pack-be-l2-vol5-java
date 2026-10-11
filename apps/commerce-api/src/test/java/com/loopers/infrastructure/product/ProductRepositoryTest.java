package com.loopers.infrastructure.product;

import com.loopers.domain.common.Money;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Product saveProduct(Long brandId, String name, int stock) {
        return productRepository.save(Product.create(brandId, name, new Money(89_000), new Stock(stock)));
    }

    private Product saveDeletedProduct(Long brandId, String name) {
        Product product = Product.create(brandId, name, new Money(89_000), new Stock(1));
        product.delete();
        return productRepository.save(product);
    }

    @DisplayName("상품을 저장하고 조회할 때,")
    @Nested
    class SaveAndFind {
        @DisplayName("저장한 브랜드·이름·가격·재고가 그대로 조회되고, 생성·수정 시각이 채워진다.")
        @Test
        void returnsSavedProduct_whenProductIsSaved() {
            // arrange
            Product saved = saveProduct(3L, "러닝화", 10);

            // act
            Optional<Product> found = productRepository.findActive(saved.getId());

            // assert
            assertThat(found).isPresent();
            assertThat(found.get().getBrandId()).isEqualTo(3L);
            assertThat(found.get().getName()).isEqualTo("러닝화");
            assertThat(found.get().getPrice()).isEqualTo(new Money(89_000));
            assertThat(found.get().getStock()).isEqualTo(new Stock(10));
            assertThat(found.get().getCreatedAt()).isNotNull();
            assertThat(found.get().getUpdatedAt()).isNotNull();
        }

        @DisplayName("삭제된 상품은 조회되지 않는다.")
        @Test
        void returnsEmpty_whenProductIsDeleted() {
            // arrange
            Product deleted = saveDeletedProduct(1L, "삭제됨");

            // act
            Optional<Product> found = productRepository.findActive(deleted.getId());

            // assert
            assertThat(found).isEmpty();
        }

        @DisplayName("존재하지 않는 id 이면 빈 결과를 반환한다.")
        @Test
        void returnsEmpty_whenProductDoesNotExist() {
            // act
            Optional<Product> found = productRepository.findActive(999L);

            // assert
            assertThat(found).isEmpty();
        }
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class FindAllActive {
        @DisplayName("삭제된 상품은 빼고 id 내림차순으로 반환한다.")
        @Test
        void excludesDeleted_andOrdersByIdDesc() {
            // arrange
            Product first = saveProduct(1L, "첫째", 1);
            saveDeletedProduct(1L, "삭제됨");
            Product third = saveProduct(2L, "셋째", 1);

            // act
            Page<Product> page = productRepository.findAllActive(PageRequest.of(0, 10));

            // assert
            assertThat(page.getContent()).extracting(Product::getId)
                .containsExactly(third.getId(), first.getId());
            assertThat(page.getTotalElements()).isEqualTo(2);
        }

        @DisplayName("페이지 크기만큼만 반환하고, 전체 페이지 수를 계산한다.")
        @Test
        void returnsOnlyPageSize_andCalculatesTotalPages() {
            // arrange
            saveProduct(1L, "하나", 1);
            saveProduct(1L, "둘", 1);
            saveProduct(1L, "셋", 1);

            // act
            Page<Product> secondPage = productRepository.findAllActive(PageRequest.of(1, 2));

            // assert
            assertThat(secondPage.getContent()).hasSize(1);
            assertThat(secondPage.getTotalPages()).isEqualTo(2);
            assertThat(secondPage.getTotalElements()).isEqualTo(3);
        }
    }

    @DisplayName("브랜드에 삭제되지 않은 상품이 있는지 확인할 때,")
    @Nested
    class ExistsActiveByBrandId {
        @DisplayName("삭제되지 않은 상품이 있으면 true 이다.")
        @Test
        void returnsTrue_whenActiveProductExists() {
            // arrange
            saveProduct(1L, "러닝화", 10);

            // act & assert
            assertThat(productRepository.existsActiveByBrandId(1L)).isTrue();
        }

        @DisplayName("재고가 0인 상품도 삭제되지 않았다면 true 이다.")
        @Test
        void returnsTrue_whenActiveProductHasNoStock() {
            // arrange
            saveProduct(1L, "품절", 0);

            // act & assert
            assertThat(productRepository.existsActiveByBrandId(1L)).isTrue();
        }

        @DisplayName("삭제된 상품만 있으면 false 이다.")
        @Test
        void returnsFalse_whenOnlyDeletedProductsExist() {
            // arrange
            saveDeletedProduct(1L, "삭제됨");

            // act & assert
            assertThat(productRepository.existsActiveByBrandId(1L)).isFalse();
        }

        @DisplayName("다른 브랜드의 상품은 영향을 주지 않는다.")
        @Test
        void returnsFalse_whenOnlyOtherBrandProductsExist() {
            // arrange
            saveProduct(2L, "다른 브랜드", 10);

            // act & assert
            assertThat(productRepository.existsActiveByBrandId(1L)).isFalse();
        }
    }
}

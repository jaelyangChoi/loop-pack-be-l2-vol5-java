package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class AdminProductFacadeTest {

    @Autowired
    private AdminProductFacade adminProductFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Brand saveBrand(String name) {
        return brandRepository.save(Brand.create(name));
    }

    private Brand saveDeletedBrand(String name) {
        Brand brand = Brand.create(name);
        brand.delete();
        return brandRepository.save(brand);
    }

    private Product saveProduct(Long brandId, String name) {
        return productRepository.save(Product.create(brandId, name, new Money(89_000), new Stock(10)));
    }

    private Product saveDeletedProduct(Long brandId, String name) {
        Product product = Product.create(brandId, name, new Money(89_000), new Stock(10));
        product.delete();
        return productRepository.save(product);
    }

    private Product stored(Long productId) {
        return productRepository.findActive(productId).orElseThrow();
    }

    private long storedCount() {
        return productRepository.findAllActive(PageRequest.of(0, 10)).getTotalElements();
    }

    private ErrorType errorTypeOf(Runnable action) {
        return assertThrows(CoreException.class, action::run).getErrorType();
    }

    @DisplayName("상품을 등록할 때,")
    @Nested
    class Create {
        @DisplayName("공백을 제거한 이름으로 저장하고, 저장된 상품 정보를 반환한다.")
        @Test
        void returnsSavedInfo_andPersists() {
            // arrange
            Brand brand = saveBrand("루퍼스");

            // act
            ProductInfo info = adminProductFacade.create(brand.getId(), "  러닝화  ", 89_000, 10);

            // assert
            assertThat(info.brandId()).isEqualTo(brand.getId());
            assertThat(info.name()).isEqualTo("러닝화");
            assertThat(info.price()).isEqualTo(89_000);
            assertThat(info.stock()).isEqualTo(10);
            assertThat(info.createdAt()).isNotNull();
            assertThat(info.updatedAt()).isNotNull();
            assertThat(stored(info.id()).getName()).isEqualTo("러닝화");
            assertThat(stored(info.id()).getStock()).isEqualTo(new Stock(10));
        }

        @DisplayName("없거나 삭제된 브랜드이면 NOT_FOUND 로 거절하고, 저장하지 않는다.")
        @Test
        void throwsNotFound_andSavesNothing_whenBrandDoesNotExistOrIsDeleted() {
            // arrange
            Brand deleted = saveDeletedBrand("삭제됨");

            // act & assert
            assertThat(errorTypeOf(() -> adminProductFacade.create(999L, "러닝화", 89_000, 10)))
                .isEqualTo(ErrorType.NOT_FOUND);
            assertThat(errorTypeOf(() -> adminProductFacade.create(deleted.getId(), "러닝화", 89_000, 10)))
                .isEqualTo(ErrorType.NOT_FOUND);
            assertThat(storedCount()).isZero();
        }

        @DisplayName("이름·가격·재고가 유효하지 않으면 INVALID_INPUT 으로 거절하고, 저장하지 않는다.")
        @Test
        void rejectsWithInvalidInput_andSavesNothing() {
            // arrange
            Brand brand = saveBrand("루퍼스");
            Long brandId = brand.getId();

            // act & assert
            assertThat(errorTypeOf(() -> adminProductFacade.create(brandId, "   ", 89_000, 10)))
                .isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(errorTypeOf(() -> adminProductFacade.create(brandId, "러닝화", 0, 10)))
                .isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(errorTypeOf(() -> adminProductFacade.create(brandId, "러닝화", 1_000_000_001L, 10)))
                .isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(errorTypeOf(() -> adminProductFacade.create(brandId, "러닝화", 89_000, -1)))
                .isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(storedCount()).isZero();
        }
    }

    @DisplayName("상품을 수정할 때,")
    @Nested
    class Update {
        @DisplayName("이름과 가격을 바꾸고, 브랜드와 재고는 그대로 저장된다.")
        @Test
        void changesNameAndPrice_andPersists() {
            // arrange
            Product product = saveProduct(1L, "러닝화");

            // act
            ProductInfo info = adminProductFacade.update(product.getId(), "  새 러닝화 ", 99_000);

            // assert
            assertThat(info.name()).isEqualTo("새 러닝화");
            assertThat(info.price()).isEqualTo(99_000);
            Product found = stored(product.getId());
            assertThat(found.getName()).isEqualTo("새 러닝화");
            assertThat(found.getPrice()).isEqualTo(new Money(99_000));
            assertThat(found.getBrandId()).isEqualTo(1L);
            assertThat(found.getStock()).isEqualTo(new Stock(10));
        }

        @DisplayName("이름이나 가격이 유효하지 않으면 INVALID_INPUT 으로 거절하고, 저장된 값은 그대로다.")
        @Test
        void rejectsWithInvalidInput_andKeepsStoredValues() {
            // arrange
            Product product = saveProduct(1L, "러닝화");
            Long id = product.getId();

            // act & assert
            assertThat(errorTypeOf(() -> adminProductFacade.update(id, "   ", 99_000)))
                .isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(errorTypeOf(() -> adminProductFacade.update(id, "새 러닝화", 0)))
                .isEqualTo(ErrorType.INVALID_INPUT);
            Product found = stored(id);
            assertThat(found.getName()).isEqualTo("러닝화");
            assertThat(found.getPrice()).isEqualTo(new Money(89_000));
        }

        @DisplayName("없거나 삭제된 상품이면 NOT_FOUND 로 거절한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExistOrIsDeleted() {
            // arrange
            Product deleted = saveDeletedProduct(1L, "삭제됨");

            // act & assert
            assertThat(errorTypeOf(() -> adminProductFacade.update(999L, "이름", 1_000)))
                .isEqualTo(ErrorType.NOT_FOUND);
            assertThat(errorTypeOf(() -> adminProductFacade.update(deleted.getId(), "이름", 1_000)))
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("재고를 변경할 때,")
    @Nested
    class ChangeStock {
        @DisplayName("최종 수량으로 바꾸고, 바뀐 재고가 저장된다. 0도 허용한다.")
        @Test
        void changesStock_andPersists() {
            // arrange
            Product product = saveProduct(1L, "러닝화");

            // act
            ProductInfo info = adminProductFacade.changeStock(product.getId(), 0);

            // assert
            assertThat(info.stock()).isZero();
            assertThat(stored(product.getId()).getStock()).isEqualTo(new Stock(0));
        }

        @DisplayName("음수이면 INVALID_INPUT 으로 거절하고, 저장된 재고는 그대로다.")
        @Test
        void rejectsWithInvalidInput_andKeepsStock_whenQuantityIsNegative() {
            // arrange
            Product product = saveProduct(1L, "러닝화");

            // act & assert
            assertThat(errorTypeOf(() -> adminProductFacade.changeStock(product.getId(), -1)))
                .isEqualTo(ErrorType.INVALID_INPUT);
            assertThat(stored(product.getId()).getStock()).isEqualTo(new Stock(10));
        }

        @DisplayName("없거나 삭제된 상품이면 NOT_FOUND 로 거절한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExistOrIsDeleted() {
            // arrange
            Product deleted = saveDeletedProduct(1L, "삭제됨");

            // act & assert
            assertThat(errorTypeOf(() -> adminProductFacade.changeStock(999L, 5)))
                .isEqualTo(ErrorType.NOT_FOUND);
            assertThat(errorTypeOf(() -> adminProductFacade.changeStock(deleted.getId(), 5)))
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class Delete {
        @DisplayName("삭제 후에는 조회되지 않는다.")
        @Test
        void hidesProduct_afterDelete() {
            // arrange
            Product product = saveProduct(1L, "러닝화");

            // act
            adminProductFacade.delete(product.getId());

            // assert
            assertThat(productRepository.findActive(product.getId())).isEmpty();
        }

        @DisplayName("이미 삭제됐거나 없는 상품이면 NOT_FOUND 로 거절한다.")
        @Test
        void throwsNotFound_whenProductIsAlreadyDeletedOrMissing() {
            // arrange
            Product product = saveProduct(1L, "러닝화");
            adminProductFacade.delete(product.getId());

            // act & assert
            assertThat(errorTypeOf(() -> adminProductFacade.delete(product.getId())))
                .isEqualTo(ErrorType.NOT_FOUND);
            assertThat(errorTypeOf(() -> adminProductFacade.delete(999L)))
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 상세를 조회할 때,")
    @Nested
    class Get {
        @DisplayName("브랜드·이름·가격·재고와 생성·수정 시각을 반환한다.")
        @Test
        void returnsInfo() {
            // arrange
            Product product = saveProduct(3L, "러닝화");

            // act
            ProductInfo info = adminProductFacade.get(product.getId());

            // assert
            assertThat(info.id()).isEqualTo(product.getId());
            assertThat(info.brandId()).isEqualTo(3L);
            assertThat(info.name()).isEqualTo("러닝화");
            assertThat(info.price()).isEqualTo(89_000);
            assertThat(info.stock()).isEqualTo(10);
            assertThat(info.createdAt()).isNotNull();
            assertThat(info.updatedAt()).isNotNull();
        }

        @DisplayName("없거나 삭제된 상품이면 NOT_FOUND 로 거절한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExistOrIsDeleted() {
            // arrange
            Product deleted = saveDeletedProduct(1L, "삭제됨");

            // act & assert
            assertThat(errorTypeOf(() -> adminProductFacade.get(999L))).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(errorTypeOf(() -> adminProductFacade.get(deleted.getId()))).isEqualTo(ErrorType.NOT_FOUND);
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
            Product third = saveProduct(2L, "셋째");

            // act
            Page<ProductInfo> page = adminProductFacade.getList(0, 20);

            // assert
            assertThat(page.getContent()).extracting(ProductInfo::id)
                .containsExactly(third.getId(), first.getId());
            assertThat(page.getTotalElements()).isEqualTo(2);
        }

        @DisplayName("size 의 경계값(1, 100)은 허용한다.")
        @ParameterizedTest
        @CsvSource({"0, 1", "0, 100"})
        void allowsBoundaryValues(int page, int size) {
            // act
            Page<ProductInfo> result = adminProductFacade.getList(page, size);

            // assert
            assertThat(result.getSize()).isEqualTo(size);
        }

        @DisplayName("page 가 음수이거나 size 가 범위를 벗어나면 INVALID_INPUT 으로 거절한다.")
        @ParameterizedTest
        @CsvSource({"-1, 20", "0, 0", "0, 101", "0, -5"})
        void rejectsWithInvalidInput_whenPageOrSizeIsOutOfRange(int page, int size) {
            assertThat(errorTypeOf(() -> adminProductFacade.getList(page, size)))
                .isEqualTo(ErrorType.INVALID_INPUT);
        }
    }
}

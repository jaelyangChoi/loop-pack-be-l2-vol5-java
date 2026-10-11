package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
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
class BrandRepositoryTest {

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

    @DisplayName("브랜드를 저장하고 조회할 때,")
    @Nested
    class SaveAndFind {
        @DisplayName("저장한 이름이 그대로 조회되고, 생성·수정 시각이 채워진다.")
        @Test
        void returnsSavedBrand_whenBrandIsSaved() {
            // arrange
            Brand saved = saveBrand("루퍼스");

            // act
            Optional<Brand> found = brandRepository.findActive(saved.getId());

            // assert
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("루퍼스");
            assertThat(found.get().getCreatedAt()).isNotNull();
            assertThat(found.get().getUpdatedAt()).isNotNull();
        }

        @DisplayName("삭제된 브랜드는 조회되지 않는다.")
        @Test
        void returnsEmpty_whenBrandIsDeleted() {
            // arrange
            Brand deleted = saveDeletedBrand("삭제됨");

            // act
            Optional<Brand> found = brandRepository.findActive(deleted.getId());

            // assert
            assertThat(found).isEmpty();
        }

        @DisplayName("존재하지 않는 id 이면 빈 결과를 반환한다.")
        @Test
        void returnsEmpty_whenBrandDoesNotExist() {
            // act
            Optional<Brand> found = brandRepository.findActive(999L);

            // assert
            assertThat(found).isEmpty();
        }
    }

    @DisplayName("브랜드 목록을 조회할 때,")
    @Nested
    class FindAllActive {
        @DisplayName("삭제된 브랜드는 빼고 id 내림차순으로 반환한다.")
        @Test
        void excludesDeleted_andOrdersByIdDesc() {
            // arrange
            Brand first = saveBrand("첫째");
            saveDeletedBrand("삭제됨");
            Brand third = saveBrand("셋째");

            // act
            Page<Brand> page = brandRepository.findAllActive(PageRequest.of(0, 10));

            // assert
            assertThat(page.getContent()).extracting(Brand::getId)
                .containsExactly(third.getId(), first.getId());
            assertThat(page.getTotalElements()).isEqualTo(2);
        }

        @DisplayName("페이지 크기만큼만 반환하고, 전체 페이지 수를 계산한다.")
        @Test
        void returnsOnlyPageSize_andCalculatesTotalPages() {
            // arrange
            saveBrand("하나");
            saveBrand("둘");
            saveBrand("셋");

            // act
            Page<Brand> secondPage = brandRepository.findAllActive(PageRequest.of(1, 2));

            // assert
            assertThat(secondPage.getContent()).hasSize(1);
            assertThat(secondPage.getTotalPages()).isEqualTo(2);
            assertThat(secondPage.getTotalElements()).isEqualTo(3);
        }
    }
}

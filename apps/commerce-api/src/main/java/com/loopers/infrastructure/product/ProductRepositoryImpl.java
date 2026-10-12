package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<Product> findActive(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<Product> findAllActiveByIds(Collection<Long> ids) {
        // 빈 목록으로 IN 조회를 하면 DB 에 따라 문법 오류가 나므로 조회하지 않는다.
        if (ids.isEmpty()) {
            return List.of();
        }
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public Page<Product> findAllActive(Pageable pageable) {
        return productJpaRepository.findAllByDeletedAtIsNullOrderByIdDesc(pageable);
    }

    @Override
    public Page<Product> findAllActive(Long brandId, ProductSort sort, Pageable pageable) {
        return switch (sort) {
            case LATEST -> productJpaRepository.findAllActiveOrderByLatest(brandId, pageable);
            case PRICE_ASC -> productJpaRepository.findAllActiveOrderByPriceAsc(brandId, pageable);
            case LIKES_DESC -> productJpaRepository.findAllActiveOrderByLikesDesc(brandId, pageable);
        };
    }

    @Override
    public Page<Product> findAllActiveLikedBy(Long userId, Pageable pageable) {
        return productJpaRepository.findAllActiveLikedBy(userId, pageable);
    }

    @Override
    public boolean existsActiveByBrandId(Long brandId) {
        return productJpaRepository.existsByBrandIdAndDeletedAtIsNull(brandId);
    }
}

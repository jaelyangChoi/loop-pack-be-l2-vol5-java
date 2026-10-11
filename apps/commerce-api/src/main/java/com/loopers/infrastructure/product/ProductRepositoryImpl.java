package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

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
    public Page<Product> findAllActive(Pageable pageable) {
        return productJpaRepository.findAllByDeletedAtIsNullOrderByIdDesc(pageable);
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

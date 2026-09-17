package com.loopers.domain.product;

import com.loopers.domain.common.PageCondition;
import com.loopers.domain.error.DomainErrorType;
import com.loopers.domain.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        return productRepository.findActive(id)
            .orElseThrow(() -> new DomainException(DomainErrorType.NOT_FOUND, "[productId = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public void verifyActive(Long id) {
        getProduct(id);
    }

    @Transactional(readOnly = true)
    public List<ProductWithLikeCount> searchProducts(ProductSearchCondition condition) {
        return productRepository.search(condition);
    }

    @Transactional(readOnly = true)
    public long countProducts(ProductSearchCondition condition) {
        return productRepository.count(condition);
    }

    @Transactional(readOnly = true)
    public List<Product> getLikedProducts(Long userId, PageCondition page) {
        return productRepository.findActiveLikedBy(userId, page);
    }

    @Transactional(readOnly = true)
    public long countLikedProducts(Long userId) {
        return productRepository.countActiveLikedBy(userId);
    }
}

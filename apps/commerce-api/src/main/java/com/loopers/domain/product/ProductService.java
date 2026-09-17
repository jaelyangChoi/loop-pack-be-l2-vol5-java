package com.loopers.domain.product;

import com.loopers.domain.common.PageCondition;
import com.loopers.domain.error.DomainErrorType;
import com.loopers.domain.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
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

    // 주문 생성·확정용: 삭제되지 않은 상품만 조회한다
    @Transactional(readOnly = true)
    public List<Product> getActiveProducts(Collection<Long> ids) {
        return productRepository.findAllActive(ids);
    }

    // 주문 내역 조회용: 삭제된 상품도 포함한다(DEL-003)
    @Transactional(readOnly = true)
    public List<Product> getProductsIncludingDeleted(Collection<Long> ids) {
        return productRepository.findAll(ids);
    }
}

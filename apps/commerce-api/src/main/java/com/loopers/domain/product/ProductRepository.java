package com.loopers.domain.product;

import com.loopers.domain.common.PageCondition;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Optional<Product> findActive(Long id);

    List<ProductWithLikeCount> search(ProductSearchCondition condition);

    long count(ProductSearchCondition condition);

    List<Product> findActiveLikedBy(Long userId, PageCondition page);

    long countActiveLikedBy(Long userId);
}

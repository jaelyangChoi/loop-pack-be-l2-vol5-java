package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);

    /** 삭제되지 않은 상품만 조회한다. */
    Optional<Product> findActive(Long id);

    /** 삭제되지 않은 상품을 id 내림차순으로 조회한다. 정렬은 Pageable 이 아니라 구현이 고정한다. */
    Page<Product> findAllActive(Pageable pageable);

    /** 브랜드에 삭제되지 않은 상품이 하나라도 있는지 확인한다. 재고가 0이어도 삭제되지 않았다면 있는 것이다. */
    boolean existsActiveByBrandId(Long brandId);
}

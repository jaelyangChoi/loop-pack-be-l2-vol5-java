package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);

    /** 삭제되지 않은 상품만 조회한다. */
    Optional<Product> findActive(Long id);

    /** id 목록에 해당하는 삭제되지 않은 상품을 한 번에 조회한다. 삭제됐거나 없는 id 는 결과에 없다. */
    List<Product> findAllActiveByIds(Collection<Long> ids);

    /** 삭제되지 않은 상품을 id 내림차순으로 조회한다. 정렬은 Pageable 이 아니라 구현이 고정한다. */
    Page<Product> findAllActive(Pageable pageable);

    /**
     * 고객 상품 목록. 삭제되지 않은 상품을 정렬 기준에 따라 조회하고, 브랜드를 지정하면 그 브랜드의 상품만 조회한다.
     * 존재하지 않는 브랜드이면 빈 결과다. 정렬은 Pageable 이 아니라 {@code sort} 가 정하며 동률은 id 내림차순이다.
     */
    Page<Product> findAllActive(Long brandId, ProductSort sort, Pageable pageable);

    /** 사용자가 좋아요한 삭제되지 않은 상품을, 최근에 좋아요한 순으로 조회한다. 정렬은 Pageable 이 아니라 구현이 고정한다. */
    Page<Product> findAllActiveLikedBy(Long userId, Pageable pageable);

    /** 브랜드에 삭제되지 않은 상품이 하나라도 있는지 확인한다. 재고가 0이어도 삭제되지 않았다면 있는 것이다. */
    boolean existsActiveByBrandId(Long brandId);
}

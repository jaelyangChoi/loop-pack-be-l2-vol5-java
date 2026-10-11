package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface BrandRepository {
    Brand save(Brand brand);

    /** 삭제되지 않은 브랜드만 조회한다. */
    Optional<Brand> findActive(Long id);

    /** 삭제되지 않은 브랜드를 id 내림차순으로 조회한다. 정렬은 Pageable 이 아니라 구현이 고정한다. */
    Page<Brand> findAllActive(Pageable pageable);
}

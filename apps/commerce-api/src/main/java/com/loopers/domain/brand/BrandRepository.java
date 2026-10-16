package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    Brand save(Brand brand);

    /** 삭제되지 않은 브랜드만 조회한다. */
    Optional<Brand> findActive(Long id);

    /**
     * 삭제되지 않은 브랜드를 배타 잠금(FOR UPDATE)으로 조회한다. 이 브랜드를 바꾸는 요청은 트랜잭션이 끝날 때까지 기다린다.
     * 반드시 Facade 의 트랜잭션 안에서, 이 브랜드를 처음 읽는 조회로 호출한다.
     */
    Optional<Brand> findActiveForUpdate(Long id);

    /** 삭제되지 않은 브랜드를 id 내림차순으로 조회한다. 정렬은 Pageable 이 아니라 구현이 고정한다. */
    Page<Brand> findAllActive(Pageable pageable);

    /** id 목록에 해당하는 삭제되지 않은 브랜드를 한 번에 조회한다. 삭제됐거나 없는 id 는 결과에 없다. */
    List<Brand> findAllActiveByIds(Collection<Long> ids);
}

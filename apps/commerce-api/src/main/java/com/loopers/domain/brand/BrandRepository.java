package com.loopers.domain.brand;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    Optional<Brand> findActive(Long id);

    List<Brand> findAll(Collection<Long> ids);
}

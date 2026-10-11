package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class BrandRepositoryImpl implements BrandRepository {
    private final BrandJpaRepository brandJpaRepository;

    @Override
    public Brand save(Brand brand) {
        return brandJpaRepository.save(brand);
    }

    @Override
    public Optional<Brand> findActive(Long id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Page<Brand> findAllActive(Pageable pageable) {
        return brandJpaRepository.findAllByDeletedAtIsNullOrderByIdDesc(pageable);
    }

    @Override
    public List<Brand> findAllActiveByIds(Collection<Long> ids) {
        // 빈 목록으로 IN 조회를 하면 DB 에 따라 문법 오류가 나므로 조회하지 않는다.
        if (ids.isEmpty()) {
            return List.of();
        }
        return brandJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }
}

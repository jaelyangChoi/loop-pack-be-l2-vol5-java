package com.loopers.domain.brand;

import com.loopers.domain.error.DomainErrorType;
import com.loopers.domain.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public Brand getBrand(Long id) {
        return brandRepository.findActive(id)
            .orElseThrow(() -> new DomainException(DomainErrorType.NOT_FOUND, "[brandId = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Brand> getBrands(Collection<Long> ids) {
        return brandRepository.findAll(ids);
    }
}

package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    public Brand get(Long brandId) {
        return brandRepository.findActive(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));
    }

    public Brand create(String name) {
        return brandRepository.save(Brand.create(name));
    }

    public Page<Brand> getList(Pageable pageable) {
        return brandRepository.findAllActive(pageable);
    }
}

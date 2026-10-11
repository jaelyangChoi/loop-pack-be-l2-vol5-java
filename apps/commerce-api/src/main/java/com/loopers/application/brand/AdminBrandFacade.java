package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자용 브랜드 유스케이스.
 */
@RequiredArgsConstructor
@Component
public class AdminBrandFacade {

    private static final int MAX_PAGE_SIZE = 100;

    private final BrandService brandService;

    @Transactional
    public BrandInfo create(String name) {
        return BrandInfo.from(brandService.create(name));
    }

    @Transactional
    public BrandInfo update(Long brandId, String name) {
        Brand brand = brandService.get(brandId);
        brand.update(name);
        return BrandInfo.from(brand);
    }

    @Transactional
    public void delete(Long brandId) {
        brandService.get(brandId).delete();
    }

    @Transactional(readOnly = true)
    public BrandInfo get(Long brandId) {
        return BrandInfo.from(brandService.get(brandId));
    }

    @Transactional(readOnly = true)
    public Page<BrandInfo> getList(int page, int size) {
        if (page < 0) {
            throw new CoreException(ErrorType.INVALID_INPUT, "page 는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new CoreException(ErrorType.INVALID_INPUT, "size 는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }
        return brandService.getList(PageRequest.of(page, size)).map(BrandInfo::from);
    }
}

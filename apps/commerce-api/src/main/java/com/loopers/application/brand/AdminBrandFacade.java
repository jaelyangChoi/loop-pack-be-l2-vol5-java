package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.paging.PageRequests;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자용 브랜드 유스케이스.
 */
@RequiredArgsConstructor
@Component
public class AdminBrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

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
        Brand brand = brandService.get(brandId);
        // 상품은 다른 Aggregate 라 연결 여부를 여기서 확인한다. 재고가 0이어도 삭제되지 않은 상품은 연결된 것이다.
        if (productService.hasActiveProducts(brandId)) {
            throw new CoreException(ErrorType.CONFLICT, "연결된 상품이 있어 브랜드를 삭제할 수 없습니다. 상품을 먼저 삭제해 주세요.");
        }
        brand.delete();
    }

    @Transactional(readOnly = true)
    public BrandInfo get(Long brandId) {
        return BrandInfo.from(brandService.get(brandId));
    }

    @Transactional(readOnly = true)
    public Page<BrandInfo> getList(int page, int size) {
        return brandService.getList(PageRequests.of(page, size)).map(BrandInfo::from);
    }
}

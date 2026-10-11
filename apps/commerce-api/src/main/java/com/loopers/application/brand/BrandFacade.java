package com.loopers.application.brand;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고객용 브랜드 유스케이스. 고객 API 는 요청자 식별이 필수라 사용자 존재를 먼저 확인한다.
 */
@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final UserService userService;
    private final BrandService brandService;

    @Transactional(readOnly = true)
    public BrandInfo get(Long userId, Long brandId) {
        userService.get(userId);
        return BrandInfo.from(brandService.get(brandId));
    }
}

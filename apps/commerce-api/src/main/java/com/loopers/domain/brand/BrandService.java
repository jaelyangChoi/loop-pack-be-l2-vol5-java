package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    public Brand get(Long brandId) {
        return brandRepository.findActive(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));
    }

    /** 삭제되지 않은 브랜드를 잠그고 조회한다. 브랜드를 바꾸는 유스케이스의 첫 조회로 쓴다. */
    public Brand getForUpdate(Long brandId) {
        return brandRepository.findActiveForUpdate(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));
    }

    /**
     * 삭제되지 않은 브랜드를 공유 잠금으로 조회한다. 브랜드를 읽기만 하고 그 아래에 상품을 만드는 유스케이스의 첫 브랜드
     * 조회로 쓴다. 브랜드 삭제와는 차례로 처리돼, 삭제된 브랜드 아래에 상품이 만들어지지 않는다.
     */
    public Brand getForShare(Long brandId) {
        return brandRepository.findActiveForShare(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));
    }

    public Brand create(String name) {
        return brandRepository.save(Brand.create(name));
    }

    /** 여러 브랜드를 한 번에 조회해 id 로 찾을 수 있게 돌려준다. 삭제됐거나 없는 브랜드는 담기지 않는다. */
    public Map<Long, Brand> getAllByIds(Collection<Long> ids) {
        return brandRepository.findAllActiveByIds(ids).stream()
            .collect(Collectors.toMap(Brand::getId, Function.identity()));
    }

    public Page<Brand> getList(Pageable pageable) {
        return brandRepository.findAllActive(pageable);
    }
}

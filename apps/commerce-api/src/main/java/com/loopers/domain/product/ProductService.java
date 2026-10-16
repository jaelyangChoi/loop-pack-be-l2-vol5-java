package com.loopers.domain.product;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    public Product get(Long productId) {
        return productRepository.findActive(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
    }

    /** 여러 상품을 한 번에 조회해 id 로 찾을 수 있게 돌려준다. 삭제됐거나 없는 상품은 담기지 않는다. */
    public Map<Long, Product> getAllActiveByIds(Collection<Long> ids) {
        return productRepository.findAllActiveByIds(ids).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    public Product create(Long brandId, String name, Money price, Stock stock) {
        return productRepository.save(Product.create(brandId, name, price, stock));
    }

    public Page<Product> getList(Pageable pageable) {
        return productRepository.findAllActive(pageable);
    }

    public Page<Product> search(Long brandId, ProductSort sort, Pageable pageable) {
        return productRepository.findAllActive(brandId, sort, pageable);
    }

    public Page<Product> getLikedList(Long userId, Pageable pageable) {
        return productRepository.findAllActiveLikedBy(userId, pageable);
    }

    /** 브랜드의 삭제되지 않은 상품을 모두 잠그고 조회한다(id 오름차순, 재고 0 포함). 브랜드 삭제의 첫 상품 조회로 쓴다. */
    public List<Product> getAllActiveByBrandForUpdate(Long brandId) {
        return productRepository.findAllActiveByBrandIdForUpdate(brandId);
    }
}

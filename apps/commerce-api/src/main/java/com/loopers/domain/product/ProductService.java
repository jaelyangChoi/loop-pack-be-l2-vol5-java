package com.loopers.domain.product;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    public Product get(Long productId) {
        return productRepository.findActive(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
    }

    public Product create(Long brandId, String name, Money price, Stock stock) {
        return productRepository.save(Product.create(brandId, name, price, stock));
    }

    public Page<Product> getList(Pageable pageable) {
        return productRepository.findAllActive(pageable);
    }

    public Page<Product> getLikedList(Long userId, Pageable pageable) {
        return productRepository.findAllActiveLikedBy(userId, pageable);
    }

    public boolean hasActiveProducts(Long brandId) {
        return productRepository.existsActiveByBrandId(brandId);
    }
}

package com.loopers.infrastructure.like;

import com.loopers.domain.like.ProductLikeCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductLikeCountRepositoryImpl implements ProductLikeCountRepository {
    private final ProductLikeCountJpaRepository productLikeCountJpaRepository;

    @Override
    public int increase(Long productId) {
        return productLikeCountJpaRepository.increase(productId);
    }

    @Override
    public int decrease(Long productId) {
        return productLikeCountJpaRepository.decrease(productId);
    }
}

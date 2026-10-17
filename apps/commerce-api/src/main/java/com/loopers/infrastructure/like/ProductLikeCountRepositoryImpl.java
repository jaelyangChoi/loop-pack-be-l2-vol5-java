package com.loopers.infrastructure.like;

import com.loopers.domain.like.ProductLikeCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;

@RequiredArgsConstructor
@Component
public class ProductLikeCountRepositoryImpl implements ProductLikeCountRepository {
    private final ProductLikeCountJpaRepository productLikeCountJpaRepository;

    @Override
    public void create(Long productId) {
        productLikeCountJpaRepository.insertZero(productId);
    }

    @Override
    public void deleteAll(Collection<Long> productIds) {
        // 빈 목록으로 IN 조건을 쓰면 DB 에 따라 문법 오류가 나므로 실행하지 않는다.
        if (productIds.isEmpty()) {
            return;
        }
        productLikeCountJpaRepository.deleteAllByProductIdIn(productIds);
    }

    @Override
    public int increase(Long productId) {
        return productLikeCountJpaRepository.increase(productId);
    }

    @Override
    public int decrease(Long productId) {
        return productLikeCountJpaRepository.decrease(productId);
    }
}

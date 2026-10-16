package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeId;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 좋아요 관계를 저장하고, 같은 트랜잭션에서 상품별 좋아요 수 집계({@code product_like_counts})를 함께 맞춘다.
 * 집계는 좋아요 수의 단일 출처라 관계가 실제로 생기거나 지워졌을 때만 ±1 한다.
 */
@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {
    private final LikeJpaRepository likeJpaRepository;
    private final ProductLikeCountJpaRepository productLikeCountJpaRepository;

    @Override
    public Like save(Like like) {
        Long productId = like.getId().productId();
        // 이미 있는 관계를 다시 저장해도 수가 늘지 않게 한다. 동시에 같은 관계를 만들면 기본키 충돌로 한쪽 TX 가 rollback 된다.
        if (likeJpaRepository.existsById(like.getId())) {
            return like;
        }
        Like saved = likeJpaRepository.save(like);
        productLikeCountJpaRepository.increase(productId);
        return saved;
    }

    @Override
    public boolean exists(Long userId, Long productId) {
        return likeJpaRepository.existsById(new LikeId(productId, userId));
    }

    @Override
    public int delete(Long userId, Long productId) {
        int deleted = likeJpaRepository.deleteByUserIdAndProductId(userId, productId);
        if (deleted > 0) {
            productLikeCountJpaRepository.decrease(productId);
        }
        return deleted;
    }

    @Override
    public long countByProductId(Long productId) {
        return productLikeCountJpaRepository.findLikeCount(productId).orElse(0L);
    }

    @Override
    public Map<Long, Long> countByProductIds(Collection<Long> productIds) {
        // 빈 목록으로 IN 조회를 하면 DB 에 따라 문법 오류가 나므로 조회하지 않는다.
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : productLikeCountJpaRepository.findLikeCounts(productIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }
}

package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeId;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {
    private final LikeJpaRepository likeJpaRepository;

    @Override
    public Like save(Like like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public boolean exists(Long userId, Long productId) {
        return likeJpaRepository.existsById(new LikeId(productId, userId));
    }

    @Override
    public int delete(Long userId, Long productId) {
        return likeJpaRepository.deleteByUserIdAndProductId(userId, productId);
    }

    @Override
    public long countByProductId(Long productId) {
        return likeJpaRepository.countByIdProductId(productId);
    }

    @Override
    public Map<Long, Long> countByProductIds(Collection<Long> productIds) {
        // 빈 목록으로 IN 조회를 하면 DB 에 따라 문법 오류가 나므로 조회하지 않는다.
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : likeJpaRepository.countGroupByProductId(productIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }
}

package com.loopers.domain.like;

import java.util.Collection;
import java.util.Map;

public interface LikeRepository {
    Like save(Like like);

    boolean exists(Long userId, Long productId);

    /** 관계를 지우고 삭제된 행 수를 반환한다. 관계가 없으면 0이다. */
    int delete(Long userId, Long productId);

    long countByProductId(Long productId);

    /** 여러 상품의 좋아요 수를 한 번에 센다. 좋아요가 없는 상품은 결과에 없다. */
    Map<Long, Long> countByProductIds(Collection<Long> productIds);
}

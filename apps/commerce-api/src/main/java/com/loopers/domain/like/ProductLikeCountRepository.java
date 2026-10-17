package com.loopers.domain.like;

import java.util.Collection;
import java.util.Map;

/**
 * 상품별 좋아요 수 집계({@link ProductLikeCount}). 좋아요 수의 단일 출처이며 노출 중인 상품만 행을 가진다. 행은 상품을
 * 만들 때 0으로 만들고 상품을 삭제할 때 지운다. 증감은 원자적 {@code UPDATE} 로만 하며 행을 새로 만들지 않으므로,
 * 행이 없는 상품(삭제된 상품)은 바뀌지 않고 영향 행 수 0을 반환한다.
 */
public interface ProductLikeCountRepository {

    /** 새 상품의 행을 좋아요 0개로 만든다. */
    void create(Long productId);

    /** 삭제된 상품들의 행을 지운다. */
    void deleteAll(Collection<Long> productIds);

    /** 좋아요 수를 1 올리고 바뀐 행 수를 반환한다. */
    int increase(Long productId);

    /** 좋아요 수를 1 내리고 바뀐 행 수를 반환한다. 0 아래로는 내리지 않는다. */
    int decrease(Long productId);

    /** 상품의 좋아요 수. 행이 없으면 0이다. */
    long findCount(Long productId);

    /** 여러 상품의 좋아요 수를 한 번에 읽는다. 좋아요가 없는(행이 없거나 0인) 상품은 결과에 없다. */
    Map<Long, Long> findCounts(Collection<Long> productIds);
}

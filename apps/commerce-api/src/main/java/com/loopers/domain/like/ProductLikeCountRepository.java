package com.loopers.domain.like;

/**
 * 상품별 좋아요 수 집계({@link ProductLikeCount})의 증감. 원자적 {@code UPDATE} 로만 바꾸며 행을 새로 만들지 않는다.
 * 행이 없는 상품(삭제된 상품)은 바뀌지 않고 영향 행 수 0을 반환한다.
 */
public interface ProductLikeCountRepository {

    /** 좋아요 수를 1 올리고 바뀐 행 수를 반환한다. */
    int increase(Long productId);

    /** 좋아요 수를 1 내리고 바뀐 행 수를 반환한다. 0 아래로는 내리지 않는다. */
    int decrease(Long productId);
}

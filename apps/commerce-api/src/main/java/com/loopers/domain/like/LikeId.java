package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;

/**
 * 좋아요의 복합 식별자. 사용자와 상품의 조합이 곧 유일성이다.
 *
 * <p>JPA 복합키 규격에 따라 {@link Serializable}이며, record 라 동등성({@code equals}/{@code hashCode})이 값으로 정의된다.
 */
@Embeddable
public record LikeId(
    @Column(name = "user_id", nullable = false) Long userId,
    @Column(name = "product_id", nullable = false) Long productId
) implements Serializable {

    public LikeId {
        if (userId == null) {
            throw new CoreException(ErrorType.INVALID_INPUT, "사용자는 필수입니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.INVALID_INPUT, "상품은 필수입니다.");
        }
    }
}

package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 고객이 요청한 주문 품목 한 줄. 어떤 상품을 몇 개 주문하는지만 담는다.
 *
 * <p>상품을 조회하기 전에 판단할 수 있는 값 규칙(상품 지정, 1 이상의 수량)만 지킨다.
 * 상품명과 단가가 붙어 저장되는 품목은 {@link OrderItem} 이다.
 */
public record OrderLine(Long productId, int quantity) {

    public OrderLine {
        if (productId == null) {
            throw new CoreException(ErrorType.INVALID_INPUT, "주문할 상품은 필수입니다.");
        }
        if (quantity <= 0) {
            throw new CoreException(ErrorType.INVALID_INPUT, "주문 수량은 1 이상이어야 합니다.");
        }
    }
}

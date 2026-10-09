package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 상품의 재고 수량을 나타내는 불변 값 객체.
 *
 * <p>재고는 항상 0 이상이다. 차감은 자신을 바꾸지 않고 새 {@code Stock}을 반환하므로,
 * 거절된 경우에도 기존 재고는 유지된다.
 */
public record Stock(int remaining) {

    public Stock {
        if (remaining < 0) {
            throw new CoreException(ErrorType.INVALID_INPUT, "재고는 0 이상이어야 합니다.");
        }
    }

    /**
     * 재고를 차감한 새 {@code Stock}을 반환한다.
     *
     * <p>입력 자체가 잘못된 경우(0 이하 수량)를 먼저 거절하고, 그다음 보유량 부족을 거절한다.
     * 재고가 0일 때도 0 이하 수량은 재고 부족이 아니라 잘못된 입력으로 구분하기 위해서다.
     */
    public Stock decrease(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.INVALID_INPUT, "차감 수량은 1 이상이어야 합니다.");
        }
        if (quantity > remaining) {
            throw new CoreException(ErrorType.OUT_OF_STOCK);
        }
        return new Stock(remaining - quantity);
    }
}

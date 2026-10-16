package com.loopers.domain.user;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

/**
 * 사용자의 포인트 잔액을 나타내는 불변 값 객체. 1포인트는 1원이다.
 *
 * <p>{@code charge}와 {@code pay}는 자신을 바꾸지 않고 새 {@code PointBalance}를 반환한다.
 * 거절된 경우에도 기존 잔액은 유지되며, 반환값으로 필드를 교체하는 일은
 * 이 값을 가진 {@code User}가 맡는다.
 *
 * <p>0원은 유효한 금액이지만 충전·결제 행동에는 맞지 않으므로 여기서 거절한다.
 */
@Embeddable
public record PointBalance(Money balance) {

    public PointBalance charge(Money amount) {
        if (amount.isZero()) {
            throw new CoreException(ErrorType.INVALID_INPUT, "충전액은 0보다 커야 합니다.");
        }
        return new PointBalance(balance.plus(amount));
    }

    public PointBalance pay(Money amount) {
        if (amount.isZero()) {
            throw new CoreException(ErrorType.INVALID_INPUT, "결제액은 0보다 커야 합니다.");
        }
        if (balance.isLessThan(amount)) {
            throw new CoreException(ErrorType.INSUFFICIENT_POINT);
        }
        return new PointBalance(balance.minus(amount));
    }
}

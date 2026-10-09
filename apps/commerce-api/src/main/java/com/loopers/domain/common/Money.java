package com.loopers.domain.common;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.function.LongSupplier;

/**
 * 원 단위 금액을 나타내는 불변 값 객체. 1포인트는 1원이다.
 *
 * <p>금액은 항상 0 이상이다. 연산은 자신을 바꾸지 않고 새 {@code Money}를 반환하며,
 * 결과가 음수이거나 {@code long} 범위를 넘으면 거절한다.
 * 충전액이 양수여야 한다거나 잔액을 넘는 결제를 막는 것처럼 행동에 따른 조건은
 * 이 값을 사용하는 객체가 판단한다.
 */
public record Money(long amount) {

    public Money {
        if (amount < 0) {
            throw new CoreException(ErrorType.INVALID_INPUT, "금액은 0 이상이어야 합니다.");
        }
    }

    public Money plus(Money other) {
        return new Money(exact(() -> Math.addExact(amount, other.amount)));
    }

    public Money minus(Money other) {
        return new Money(exact(() -> Math.subtractExact(amount, other.amount)));
    }

    public Money multiply(int quantity) {
        return new Money(exact(() -> Math.multiplyExact(amount, (long) quantity)));
    }

    // long 범위를 넘는 계산은 ArithmeticException 대신 도메인 예외로 바꾼다.
    private static long exact(LongSupplier calculation) {
        try {
            return calculation.getAsLong();
        } catch (ArithmeticException e) {
            throw new CoreException(ErrorType.INVALID_INPUT, "금액이 표현 가능한 범위를 넘었습니다.");
        }
    }
}

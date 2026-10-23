package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentAttempt;
import com.loopers.domain.payment.PaymentAttemptStatus;

/**
 * 결제 시도의 현재 상태. 주문 상태와 함께 돌려준다.
 *
 * <p>{@link #settled()} 가 true 이면 PG 결과로 종결한 시도다. 아니면 처리 중이거나 결과 미확인이다. 미확인이어도 새 결제를
 * 허용한다는 뜻이 아니다.
 */
public record PaymentAttemptInfo(
    Long orderId,
    OrderStatus orderStatus,
    PaymentAttemptStatus attemptStatus,
    long amount
) {
    public static PaymentAttemptInfo of(Order order, PaymentAttempt attempt) {
        return new PaymentAttemptInfo(
            order.getId(), order.getStatus(), attempt.getStatus(), attempt.getAmount().amount()
        );
    }

    public boolean settled() {
        return attemptStatus.isTerminal();
    }
}

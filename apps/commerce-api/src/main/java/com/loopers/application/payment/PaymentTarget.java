package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentAttempt;
import com.loopers.domain.payment.PaymentAttemptStatus;

/**
 * 결과를 확인할 시도의 읽기 전용 스냅샷. PG 를 조회하기 전에 무엇을 조회할지 정하는 데 쓰며, 트랜잭션 밖에서 들고 다녀도
 * 엔티티가 아니라 값이므로 안전하다. 반영할 때는 주문을 잠그고 다시 읽는다.
 */
public record PaymentTarget(
    Long orderId,
    OrderStatus orderStatus,
    PaymentAttemptStatus attemptStatus,
    String providerOrderId,
    String transactionKey,
    long amount
) {
    public static PaymentTarget of(Order order, PaymentAttempt attempt) {
        return new PaymentTarget(
            order.getId(),
            order.getStatus(),
            attempt.getStatus(),
            attempt.getProviderOrderId(),
            attempt.getTransactionKey(),
            attempt.getAmount().amount()
        );
    }

    public boolean settled() {
        return attemptStatus.isTerminal();
    }

    public PaymentAttemptInfo info() {
        return new PaymentAttemptInfo(orderId, orderStatus, attemptStatus, amount);
    }
}

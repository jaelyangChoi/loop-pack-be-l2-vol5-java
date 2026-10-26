package com.loopers.application.order.dto;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentAttempt;
import com.loopers.domain.payment.PaymentAttemptStatus;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 주문 조회 결과. 품목은 지연 로딩이라 트랜잭션 안에서 변환해야 한다.
 * 결제 결과({@code payment})는 확정된 주문에만 있고 확정 전(DRAFT)에는 {@code null} 이다. 결제 시도가 있으면
 * {@code paymentMethod} 는 PG 이고 {@code paymentStatus} 는 시도의 상태다. 시도 없이 확정된 이전 주문은 POINT 이며
 * 시도 상태가 없다.
 * {@code orderedAt} 은 주문을 만든 시각이다.
 */
public record OrderInfo(
    Long id,
    Long userId,
    OrderStatus status,
    List<OrderItemInfo> items,
    long totalAmount,
    PaymentInfo payment,
    PaymentMethod paymentMethod,
    PaymentAttemptStatus paymentStatus,
    ZonedDateTime orderedAt
) {
    /** {@code attempt} 는 이 주문의 결제 시도이고, 없으면 {@code null} 이다. */
    public static OrderInfo from(Order order, PaymentAttempt attempt) {
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getStatus(),
            order.getItems().stream().map(OrderItemInfo::from).toList(),
            order.getTotalAmount().amount(),
            PaymentInfo.from(order.getPaymentResult()),
            paymentMethodOf(order, attempt),
            attempt == null ? null : attempt.getStatus(),
            order.getCreatedAt()
        );
    }

    private static PaymentMethod paymentMethodOf(Order order, PaymentAttempt attempt) {
        if (attempt != null) {
            return PaymentMethod.PG;
        }
        return order.getPaymentResult() == null ? null : PaymentMethod.POINT;
    }
}

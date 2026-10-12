package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;

import java.util.List;

/**
 * 주문 조회 결과. 품목은 지연 로딩이라 트랜잭션 안에서 변환해야 한다.
 */
public record OrderInfo(
    Long id,
    Long userId,
    OrderStatus status,
    List<OrderItemInfo> items,
    long totalAmount
) {
    public static OrderInfo from(Order order) {
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getStatus(),
            order.getItems().stream().map(OrderItemInfo::from).toList(),
            order.getTotalAmount().amount()
        );
    }
}

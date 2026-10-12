package com.loopers.interfaces.api.admin.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemInfo;
import com.loopers.application.order.OrderSummaryInfo;
import com.loopers.application.order.PaymentInfo;

import java.time.ZonedDateTime;
import java.util.List;

public class AdminOrderDto {

    // 구매자 id(userId)를 포함한다. payment 는 확정된 주문에만 있고, 확정 전(DRAFT)에는 null 이다.
    public record OrderResponse(
        Long id,
        Long userId,
        String status,
        List<ItemResponse> items,
        long totalAmount,
        PaymentResponse payment,
        ZonedDateTime orderedAt
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.id(),
                info.userId(),
                info.status().name(),
                info.items().stream().map(ItemResponse::from).toList(),
                info.totalAmount(),
                info.payment() == null ? null : PaymentResponse.from(info.payment()),
                info.orderedAt()
            );
        }
    }

    // 목록의 항목. 품목은 담지 않고 개수만 담는다. 품목은 상세에서 본다.
    public record OrderSummaryResponse(
        Long id,
        Long userId,
        String status,
        long totalAmount,
        int itemCount,
        PaymentResponse payment,
        ZonedDateTime orderedAt
    ) {
        public static OrderSummaryResponse from(OrderSummaryInfo info) {
            return new OrderSummaryResponse(
                info.id(),
                info.userId(),
                info.status().name(),
                info.totalAmount(),
                info.itemCount(),
                info.payment() == null ? null : PaymentResponse.from(info.payment()),
                info.orderedAt()
            );
        }
    }

    // 상품명과 단가는 주문 당시 값이다.
    public record ItemResponse(Long productId, String productName, int quantity, long unitPrice, long amount) {
        public static ItemResponse from(OrderItemInfo info) {
            return new ItemResponse(
                info.productId(), info.productName(), info.quantity(), info.unitPrice(), info.amount()
            );
        }
    }

    public record PaymentResponse(long paidAmount, long balanceAfter) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(info.paidAmount(), info.balanceAfter());
        }
    }
}

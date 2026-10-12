package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.order.OrderItemInfo;
import com.loopers.application.order.OrderSummaryInfo;
import com.loopers.application.order.PaymentInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderDto {

    // 필수 누락(@NotNull)은 요청 형식 오류라 여기서 거절한다. 빈 목록·수량 범위·중복 같은 값 규칙은 도메인(OrderLines)이 판단한다.
    public record CreateRequest(@NotNull List<@NotNull @Valid ItemRequest> items) {
        public List<OrderItemCommand> toCommands() {
            return items.stream().map(ItemRequest::toCommand).toList();
        }
    }

    public record ItemRequest(@NotNull Long productId, @NotNull Integer quantity) {
        OrderItemCommand toCommand() {
            return new OrderItemCommand(productId, quantity);
        }
    }

    // payment 는 확정된 주문에만 있고, 확정 전(DRAFT)에는 null 이다. orderedAt 은 주문을 만든 시각이다.
    public record OrderResponse(
        Long id,
        String status,
        List<ItemResponse> items,
        long totalAmount,
        PaymentResponse payment,
        ZonedDateTime orderedAt
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.id(),
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
        Long id, String status, long totalAmount, int itemCount, PaymentResponse payment, ZonedDateTime orderedAt
    ) {
        public static OrderSummaryResponse from(OrderSummaryInfo info) {
            return new OrderSummaryResponse(
                info.id(),
                info.status().name(),
                info.totalAmount(),
                info.itemCount(),
                info.payment() == null ? null : PaymentResponse.from(info.payment()),
                info.orderedAt()
            );
        }
    }

    // 결제한 금액과 결제 뒤 남은 포인트 잔액.
    public record PaymentResponse(long paidAmount, long balanceAfter) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(info.paidAmount(), info.balanceAfter());
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
}

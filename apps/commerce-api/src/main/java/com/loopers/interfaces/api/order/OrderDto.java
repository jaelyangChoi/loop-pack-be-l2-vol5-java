package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.order.OrderItemInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

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

    public record OrderResponse(Long id, String status, List<ItemResponse> items, long totalAmount) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.id(),
                info.status().name(),
                info.items().stream().map(ItemResponse::from).toList(),
                info.totalAmount()
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
}

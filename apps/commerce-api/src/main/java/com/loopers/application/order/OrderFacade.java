package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderLines;
import com.loopers.domain.order.OrderProduct;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 주문 유스케이스.
 *
 * <p>요청자 id 는 앞단의 식별 필터({@code CustomerIdentityFilter})를 거친, 존재하는 사용자의 id 여야 한다. 신뢰 경계는
 * 그 필터 한 곳이라 사용자를 다시 조회하지 않는다. 식별이 필요한 경로로 등록하지 않으면 {@code null} 이 들어오므로
 * 서버 설정 실수로 거절한다 ({@code LikeFacade} 와 같다).
 */
@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final ProductService productService;
    private final OrderService orderService;

    /**
     * DRAFT 주문을 만든다. 재고와 포인트는 차감하지 않는다.
     *
     * <p>검사 순서: 값 규칙(빈 목록·수량·중복, 400) → 상품 존재(404) → 합계 범위(400). 값 규칙은 상품을 조회하기 전에
     * 판단하므로, 잘못된 요청은 DB 를 조회하지 않는다.
     */
    @Transactional
    public OrderInfo create(Long userId, List<OrderItemCommand> items) {
        requireRequester(userId);
        OrderLines lines = toLines(items);

        Map<Long, Product> found = productService.getAllActiveByIds(lines.productIds());
        requireAllFound(lines, found);

        Map<Long, OrderProduct> products = found.values().stream()
            .collect(Collectors.toMap(Product::getId, product -> new OrderProduct(product.getName(), product.getPrice())));
        Order order = orderService.create(userId, lines, products);
        return OrderInfo.from(order);
    }

    private OrderLines toLines(List<OrderItemCommand> items) {
        if (items == null) {
            return new OrderLines(null);
        }
        if (items.stream().anyMatch(item -> item == null)) {
            throw new CoreException(ErrorType.INVALID_INPUT, "주문 품목은 비어 있을 수 없습니다.");
        }
        return new OrderLines(items.stream().map(item -> new OrderLine(item.productId(), item.quantity())).toList());
    }

    // 없거나 삭제된 상품을 요청 순서대로 모두 알려, 클라이언트가 한 번에 빼고 다시 요청할 수 있게 한다.
    private void requireAllFound(OrderLines lines, Map<Long, Product> found) {
        List<Long> missing = lines.productIds().stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            String ids = missing.stream().map(String::valueOf).collect(Collectors.joining(", "));
            throw new CoreException(
                ErrorType.NOT_FOUND, "존재하지 않는 상품이 포함되어 있습니다. (상품 ID: " + ids + ")"
            );
        }
    }

    private void requireRequester(Long requesterId) {
        if (requesterId == null) {
            throw new CoreException(
                ErrorType.INTERNAL_ERROR,
                "요청자 식별 정보가 없습니다. 식별이 필요한 경로로 등록됐는지 확인하세요."
            );
        }
    }
}

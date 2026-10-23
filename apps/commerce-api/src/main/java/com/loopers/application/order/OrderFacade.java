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
import com.loopers.support.paging.PageRequests;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 주문 유스케이스.
 *
 * <p>요청자 id 는 앞단의 식별 필터({@code CustomerIdentityFilter})를 거친, 존재하는 사용자의 id 이며 컨트롤러의
 * {@code @Requester} 파라미터가 {@code null} 이 아님을 보장한다 ({@code LikeFacade} 와 같다). 결제(확정)는 PG 호출이
 * 트랜잭션 밖에 있어야 해서 이 Facade가 아니라 {@code PaymentFacade} 가 맡는다 (D-51).
 */
@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final ProductService productService;
    private final OrderService orderService;

    /**
     * DRAFT 주문을 만든다. 재고를 확보하지 않고 포인트도 차감하지 않는다. 재고는 결제를 시작할 때 확보한다.
     *
     * <p>검사 순서: 값 규칙(빈 목록·수량·중복, 400) → 상품 존재(404) → 재고(409) → 합계 범위(400). 값 규칙은 상품을
     * 조회하기 전에 판단하므로, 잘못된 요청은 DB 를 조회하지 않는다. 재고는 차감하지 않고 확인만 해서, 처음부터 살 수
     * 없는 주문이 결제 단계까지 가지 않게 한다. 그 뒤 재고가 줄 수 있으므로 확정에서 차감할 때 다시 확인한다.
     */
    @Transactional
    public OrderInfo create(Long userId, List<OrderItemCommand> items) {
        OrderLines lines = toLines(items);

        Map<Long, Product> found = productService.getAllActiveByIds(lines.productIds());
        requireAllFound(lines, found);
        requireEnoughStock(lines, found);

        Order order = orderService.create(userId, lines, toOrderProducts(found));
        return OrderInfo.from(order);
    }

    /**
     * 본인의 주문 하나를 조회한다. 없는 주문과 다른 사용자의 주문은 구분하지 않고 NOT_FOUND 다.
     */
    @Transactional(readOnly = true)
    public OrderInfo getMine(Long userId, Long orderId) {
        return OrderInfo.from(orderService.getMine(orderId, userId));
    }

    /**
     * 내 주문 목록을 최신순으로 요약해서 조회한다. 품목은 상세에서 본다.
     */
    @Transactional(readOnly = true)
    public Page<OrderSummaryInfo> getMyList(Long userId, int page, int size) {
        return orderService.getList(userId, PageRequests.of(page, size)).map(OrderSummaryInfo::from);
    }

    // null 목록과 null 품목은 그대로 OrderLines 에 넘겨, 값 규칙을 한 곳에서 거절한다.
    private OrderLines toLines(List<OrderItemCommand> items) {
        if (items == null) {
            return new OrderLines(null);
        }
        return new OrderLines(
                items.stream().map(item -> item == null ? null : new OrderLine(item.productId(), item.quantity())).toList()
        );
    }

    private Map<Long, OrderProduct> toOrderProducts(Map<Long, Product> products) {
        return products.values().stream().collect(
                Collectors.toMap(Product::getId, product -> new OrderProduct(product.getName(), product.getPrice()))
        );
    }

    // 클라이언트가 어느 상품이 문제인지 알 수 있게 기본 문구 뒤에 상품명을 덧붙인다.
    private CoreException withNames(ErrorType errorType, List<String> names) {
        return new CoreException(errorType, errorType.getMessage() + " (" + String.join(", ", names) + ")");
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

    // 재고가 부족한 상품을 요청 순서대로 모두 알려, 클라이언트가 한 번에 수량을 줄이거나 빼고 다시 요청할 수 있게 한다.
    private void requireEnoughStock(OrderLines lines, Map<Long, Product> found) {
        List<String> shortNames = lines.values().stream()
                .filter(line -> !found.get(line.productId()).hasEnoughStock(line.quantity()))
                .map(line -> found.get(line.productId()).getName())
                .toList();
        if (!shortNames.isEmpty()) {
            throw withNames(ErrorType.OUT_OF_STOCK, shortNames);
        }
    }
}

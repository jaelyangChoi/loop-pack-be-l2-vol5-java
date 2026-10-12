package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderLines;
import com.loopers.domain.order.OrderProduct;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.PaymentResult;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
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
 * <p>요청자 id 는 앞단의 식별 필터({@code CustomerIdentityFilter})를 거친, 존재하는 사용자의 id 여야 한다. 신뢰 경계는
 * 그 필터 한 곳이라 사용자를 다시 조회하지 않는다. 식별이 필요한 경로로 등록하지 않으면 {@code null} 이 들어오므로
 * 서버 설정 실수로 거절한다 ({@code LikeFacade} 와 같다). 확정은 포인트를 차감할 {@code User} 엔티티가 필요해 조회하지만
 * 존재 확인은 필터가 먼저 한다 ({@code PointFacade} 와 같다).
 */
@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final ProductService productService;
    private final OrderService orderService;
    private final UserService userService;

    /**
     * DRAFT 주문을 만든다. 재고와 포인트는 차감하지 않는다.
     *
     * <p>검사 순서: 값 규칙(빈 목록·수량·중복, 400) → 상품 존재(404) → 재고(409) → 합계 범위(400). 값 규칙은 상품을
     * 조회하기 전에 판단하므로, 잘못된 요청은 DB 를 조회하지 않는다. 재고는 차감하지 않고 확인만 해서, 처음부터 살 수
     * 없는 주문이 결제 단계까지 가지 않게 한다. 그 뒤 재고가 줄 수 있으므로 확정에서 차감할 때 다시 확인한다.
     */
    @Transactional
    public OrderInfo create(Long userId, List<OrderItemCommand> items) {
        requireRequester(userId);
        OrderLines lines = toLines(items);

        Map<Long, Product> found = productService.getAllActiveByIds(lines.productIds());
        requireAllFound(lines, found);
        requireEnoughStock(lines, found);

        Map<Long, OrderProduct> products = found.values().stream()
            .collect(Collectors.toMap(Product::getId, product -> new OrderProduct(product.getName(), product.getPrice())));
        Order order = orderService.create(userId, lines, products);
        return OrderInfo.from(order);
    }

    /**
     * 본인의 DRAFT 주문을 확정한다. 재고를 차감하고 주문 합계를 포인트로 결제하며, 결제 결과를 저장한다.
     * 하나의 트랜잭션이라 어느 단계에서 실패해도 앞 단계의 변경은 모두 취소된다 (D-10).
     *
     * <p>오류 우선순위는 단계 순서다: 주문 조회(404) → 확정 가능 여부(409) → 상품 삭제(409) → 재고(409) → 잔액(409).
     * 확정 가능 여부를 먼저 보는 이유는 이미 확정된 주문이 재고 부족 오류로 보이지 않게 하기 위해서다.
     * 결제액은 확정 시점의 현재 가격이 아니라 주문 당시 합계다. 저장은 변경 감지에 맡긴다.
     */
    @Transactional
    public OrderInfo confirm(Long userId, Long orderId) {
        requireRequester(userId);
        Order order = orderService.getMine(orderId, userId);
        User user = userService.get(userId);
        order.ensureConfirmable(); // 이미 확정된 주문이 재고·잔액 오류로 보이지 않게 먼저 거절한다

        deductStock(order);
        user.payPoint(order.getTotalAmount());
        order.confirm(new PaymentResult(order.getTotalAmount(), user.getPointBalance().balance()));
        return OrderInfo.from(order);
    }

    // 주문한 상품이 모두 살아 있는지 확인한 뒤 품목마다 재고를 차감한다.
    private void deductStock(Order order) {
        Map<Long, Product> products = productService.getAllActiveByIds(order.productIds());
        requireAllAvailable(order, products);
        order.getItems().forEach(item -> products.get(item.getProductId()).decreaseStock(item.getQuantity()));
    }

    // 삭제됐거나 없는 상품을 품목 순서대로 모두 주문 당시 이름으로 알린다.
    private void requireAllAvailable(Order order, Map<Long, Product> products) {
        List<String> unavailableNames = order.getItems().stream()
            .filter(item -> !products.containsKey(item.getProductId()))
            .map(OrderItem::getProductName)
            .toList();
        if (!unavailableNames.isEmpty()) {
            throw new CoreException(
                ErrorType.PRODUCT_UNAVAILABLE,
                ErrorType.PRODUCT_UNAVAILABLE.getMessage() + " (" + String.join(", ", unavailableNames) + ")"
            );
        }
    }

    /** 본인의 주문 하나를 조회한다. 없는 주문과 다른 사용자의 주문은 구분하지 않고 NOT_FOUND 다. */
    @Transactional(readOnly = true)
    public OrderInfo getMine(Long userId, Long orderId) {
        requireRequester(userId);
        return OrderInfo.from(orderService.getMine(orderId, userId));
    }

    /** 내 주문 목록을 최신순으로 요약해서 조회한다. 품목은 상세에서 본다. */
    @Transactional(readOnly = true)
    public Page<OrderSummaryInfo> getMyList(Long userId, int page, int size) {
        requireRequester(userId);
        return orderService.getList(userId, PageRequests.of(page, size)).map(OrderSummaryInfo::from);
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

    // 재고가 부족한 상품을 요청 순서대로 모두 알려, 클라이언트가 한 번에 수량을 줄이거나 빼고 다시 요청할 수 있게 한다.
    private void requireEnoughStock(OrderLines lines, Map<Long, Product> found) {
        List<String> shortNames = lines.values().stream()
            .filter(line -> !found.get(line.productId()).hasEnoughStock(line.quantity()))
            .map(line -> found.get(line.productId()).getName())
            .toList();
        if (!shortNames.isEmpty()) {
            throw new CoreException(
                ErrorType.OUT_OF_STOCK,
                ErrorType.OUT_OF_STOCK.getMessage() + " (" + String.join(", ", shortNames) + ")"
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

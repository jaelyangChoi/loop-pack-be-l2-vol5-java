package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderLine;
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
 * <p>요청자 id 는 앞단의 식별 필터({@code CustomerIdentityFilter})를 거친, 존재하는 사용자의 id 이며 컨트롤러의
 * {@code @Requester} 파라미터가 {@code null} 이 아님을 보장한다 ({@code LikeFacade} 와 같다). 확정은 포인트를 차감할
 * {@code User} 엔티티가 필요해 조회하지만 존재 확인은 필터가 먼저 한다 ({@code PointFacade} 와 같다).
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
        OrderLines lines = toLines(items);

        Map<Long, Product> found = productService.getAllActiveByIds(lines.productIds());
        requireAllFound(lines, found);
        requireEnoughStock(lines, found);

        Order order = orderService.create(userId, lines, toOrderProducts(found));
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
        // 잠금 순서는 주문 → 상품(id 오름차순) → 사용자다 (D-27). 각 행은 트랜잭션에서 처음 읽을 때 잠가서 낡은 값으로
        // 판단하지 않는다. 사용자 조회는 필터가 존재를 보장해 실패하지 않으므로 상품 뒤로 옮겨도 오류 우선순위는 같다.
        Order order = orderService.getMineForUpdate(orderId, userId);
        order.ensureConfirmable(); // 이미 확정된 주문이 재고·잔액 오류로 보이지 않게 먼저 거절한다

        deductStock(order);
        User user = userService.getForUpdate(userId);
        user.payPoint(order.getTotalAmount());
        order.confirm(new PaymentResult(order.getTotalAmount(), user.getPointBalance().balance()));
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

    // 주문한 상품이 모두 살아 있는지 확인한 뒤 품목마다 재고를 차감한다.
    private void deductStock(Order order) {
        Map<Long, Product> products = productService.getAllActiveByIdsForUpdate(order.productIds());
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
            throw withNames(ErrorType.PRODUCT_UNAVAILABLE, unavailableNames);
        }
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

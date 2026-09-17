package com.loopers.domain.order;

import com.loopers.domain.common.PageCondition;
import com.loopers.domain.error.DomainErrorType;
import com.loopers.domain.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public Order createOrder(Long userId, List<OrderItem> items) {
        return orderRepository.save(new Order(userId, items));
    }

    // 다른 사용자의 주문은 없는 주문과 같은 메시지로 거절해 존재를 드러내지 않는다(P-10)
    @Transactional(readOnly = true)
    public Order getOrderOf(Long userId, Long orderId) {
        return orderRepository.find(orderId)
            .filter(order -> order.isOwnedBy(userId))
            .orElseThrow(() -> new DomainException(DomainErrorType.NOT_FOUND, "[orderId = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Order> getOrders(Long userId, PageCondition page) {
        return orderRepository.findByUserId(userId, page);
    }

    @Transactional(readOnly = true)
    public long countOrders(Long userId) {
        return orderRepository.countByUserId(userId);
    }
}

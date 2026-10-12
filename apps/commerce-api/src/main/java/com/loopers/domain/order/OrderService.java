package com.loopers.domain.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;

    /** DRAFT 주문을 만들어 저장한다. 재고와 포인트는 건드리지 않는다. */
    public Order create(Long userId, OrderLines lines, Map<Long, OrderProduct> products) {
        return orderRepository.save(Order.create(userId, lines, products));
    }
}

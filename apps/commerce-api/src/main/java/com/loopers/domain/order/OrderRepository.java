package com.loopers.domain.order;

import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);

    /** 주문자가 {@code userId} 인 주문만 조회한다. 다른 사용자의 주문은 없는 주문과 구분하지 않는다. */
    Optional<Order> findByIdAndUserId(Long id, Long userId);
}

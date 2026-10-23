package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentAttempt;
import com.loopers.domain.payment.PaymentAttemptService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결과를 확인할 시도를 읽는다. PG 를 조회하기 전의 짧은 읽기 전용 트랜잭션이며 잠금을 잡지 않는다. 이 값으로 PG 를 조회하는
 * 동안 DB 연결을 쥐지 않는다 (D-51).
 */
@RequiredArgsConstructor
@Component
public class PaymentQuery {

    private final OrderService orderService;
    private final PaymentAttemptService paymentAttemptService;

    /**
     * 본인 주문의 시도를 읽는다. 없는 주문과 다른 사용자의 주문은 구분하지 않고 {@code NOT_FOUND} 이고, 결제를 시작하지 않은
     * 주문은 확인할 시도가 없으므로 {@code CONFLICT} 다.
     */
    @Transactional(readOnly = true)
    public PaymentTarget findMine(Long userId, Long orderId) {
        Order order = orderService.getMine(orderId, userId);
        PaymentAttempt attempt = paymentAttemptService.findByOrderId(order.getId()).orElseThrow(
            () -> new CoreException(ErrorType.CONFLICT, "결제를 시작한 주문이 아닙니다.")
        );
        return PaymentTarget.of(order, attempt);
    }

    /** PG 알림이 가리키는 시도를 읽는다. 알림은 요청자가 없으므로 소유권 확인 없이 PG 용 주문 식별자로 찾는다. */
    @Transactional(readOnly = true)
    public PaymentTarget findByProviderOrderId(String providerOrderId) {
        PaymentAttempt attempt = paymentAttemptService.findByProviderOrderId(providerOrderId).orElseThrow(
            () -> new CoreException(ErrorType.NOT_FOUND, "결제 시도를 찾을 수 없습니다.")
        );
        return PaymentTarget.of(orderService.get(attempt.getOrderId()), attempt);
    }
}

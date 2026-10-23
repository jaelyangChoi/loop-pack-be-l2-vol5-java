package com.loopers.application.payment;

import com.loopers.application.payment.PaymentGateway.PaymentRequest;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStockService;
import com.loopers.domain.payment.PaymentAttempt;
import com.loopers.domain.payment.PaymentAttemptService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 결제 시작의 로컬 트랜잭션 A. PG 를 호출하기 <b>전에</b> 주문·재고·시도를 한 번에 확보하고 commit 한다.
 *
 * <p>PG 호출은 이 트랜잭션 밖에서 {@link PaymentFacade} 가 한다. 트랜잭션을 Facade 가 아니라 이 컴포넌트에 둔 이유는
 * HTTP 호출 동안 DB 연결과 재고 잠금을 쥐지 않기 위해서다 (D-51). 잠금 순서는 주문 → 상품(id 오름차순)이다 (D-27).
 *
 * <p>오류 우선순위: 주문 조회(404) → 시도 키·기존 시도(409) → 확정 여부(409) → 상품 삭제(409) → 재고(409). 재고 부족·삭제·
 * 잘못된 입력이면 전체 rollback 되고 PG 는 호출하지 않는다.
 */
@RequiredArgsConstructor
@Component
public class PaymentStarter {

    private final OrderService orderService;
    private final PaymentAttemptService paymentAttemptService;
    private final OrderStockService orderStockService;

    /**
     * 발송 권한은 시도를 <b>새로 저장한</b> 실행만 얻는다. 이미 시도가 있는 주문에 같은 키로 다시 오면 저장된 상태만 돌려주고
     * 발송하지 않는다. 다른 키이거나, 다른 주문에 쓰인 키이면 {@code PAYMENT_ATTEMPT_CONFLICT} 다.
     */
    @Transactional
    public StartOutcome start(PaymentStartCommand command) {
        // 주문 행 잠금이 같은 주문의 동시 요청을 줄 세운다. 나중 요청은 먼저 요청의 commit 을 보고 저장된 시도를 만난다.
        Order order = orderService.getMineForUpdate(command.orderId(), command.userId());

        Optional<PaymentAttempt> existing = paymentAttemptService.findByOrderId(order.getId());
        if (existing.isPresent()) {
            return returnExisting(order, existing.get(), command);
        }
        // 이 주문에는 시도가 없으므로, 같은 키가 이미 있다면 다른 주문의 것이다.
        if (paymentAttemptService.findByAttemptKey(command.attemptKey()).isPresent()) {
            throw attemptConflict();
        }

        order.markPaymentPending(); // 이미 확정된 주문은 ORDER_ALREADY_CONFIRMED
        orderStockService.deduct(order);
        PaymentAttempt attempt = saveAttempt(order, command);
        return new StartOutcome(
            PaymentAttemptInfo.of(order, attempt),
            new PaymentRequest(
                attempt.getProviderOrderId(), command.cardType(), command.cardNo(), attempt.getAmount().amount()
            )
        );
    }

    // 이미 시도가 있는 주문의 재요청이다. 같은 키면 저장된 상태만 돌려주고 PG 에는 보내지 않는다. 다른 키면 충돌이며,
    // 새 키를 보내도 두 번째 시도는 만들지 않는다.
    private StartOutcome returnExisting(Order order, PaymentAttempt attempt, PaymentStartCommand command) {
        if (!attempt.getAttemptKey().equals(command.attemptKey())) {
            throw attemptConflict();
        }
        return new StartOutcome(PaymentAttemptInfo.of(order, attempt), null);
    }

    // 서로 다른 주문이 같은 키를 동시에 쓰면 둘 다 위의 확인을 통과할 수 있다. 유일 제약이 마지막 방어선이며, 졌을 때는
    // 앞서 차감한 재고까지 이 트랜잭션과 함께 되돌아간다.
    private PaymentAttempt saveAttempt(Order order, PaymentStartCommand command) {
        try {
            return paymentAttemptService.request(order.getId(), command.attemptKey(), order.getTotalAmount());
        } catch (DataIntegrityViolationException e) {
            throw attemptConflict();
        }
    }

    private CoreException attemptConflict() {
        return new CoreException(ErrorType.PAYMENT_ATTEMPT_CONFLICT);
    }

    /**
     * 시작의 결과. {@code request} 는 발송 권한을 얻은 실행에만 있고, 없으면 저장된 상태를 돌려주는 것이라 PG 를 호출하지
     * 않는다.
     */
    public record StartOutcome(PaymentAttemptInfo info, PaymentRequest request) {
        public boolean shouldSend() {
            return request != null;
        }
    }
}

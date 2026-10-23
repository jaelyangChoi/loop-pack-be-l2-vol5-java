package com.loopers.application.payment;

import com.loopers.application.payment.PaymentGateway.CreateResult;
import com.loopers.application.payment.PaymentGateway.PgTransaction;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentAnomaly;
import com.loopers.domain.payment.PaymentAttempt;
import com.loopers.domain.payment.PaymentAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * PG 결과를 시도·주문·재고에 반영하는 로컬 트랜잭션 B. 외부 호출은 이 트랜잭션 밖에서 끝난 뒤에 부른다 (D-51).
 *
 * <p>항상 주문 행을 먼저 잠그고(D-27) 시도를 읽는다. 시도에 대한 쓰기는 이 잠금 아래에서만 일어나므로 같은 주문의 여러 입구가
 * 줄을 서고, 상태 전이가 중복 효과를 막는다. 이미 나아간 상태는 늦게 도착한 응답이 되돌리지 못한다 (PENDING 을 UNKNOWN 으로,
 * 종결을 미종결로). 상충하는 응답은 상태를 덮어쓰지 않고 이상 징후로만 남긴다.
 */
@RequiredArgsConstructor
@Component
public class PaymentResultApplier {

    private final OrderService orderService;
    private final PaymentAttemptService paymentAttemptService;
    private final OrderStockHandler orderStockHandler;

    /** 결제 생성 요청의 결과를 반영한다. */
    @Transactional
    public PaymentAttemptInfo applyCreateResult(Long orderId, CreateResult result) {
        Order order = orderService.getForUpdate(orderId);
        PaymentAttempt attempt = paymentAttemptService.getByOrderId(orderId);
        switch (result) {
            case CreateResult.Accepted accepted -> applyAccepted(attempt, accepted.transaction());
            case CreateResult.Unknown unknown -> applyUnknown(attempt);
            case CreateResult.RejectedNoEffect rejected -> applyRejected(order, attempt);
        }
        return PaymentAttemptInfo.of(order, attempt);
    }

    // PG 가 거래를 만들었다. 거래 키를 기록한다. 이미 나아간 상태는 되돌리지 않고, 다른 거래라면 이상 징후로 남긴다.
    private void applyAccepted(PaymentAttempt attempt, PgTransaction transaction) {
        String key = transaction.transactionKey();
        switch (attempt.getStatus()) {
            case REQUESTED, UNKNOWN -> attempt.markPending(key);
            case PENDING, SUCCEEDED -> {
                if (!key.equals(attempt.getTransactionKey())) {
                    attempt.recordAnomaly(PaymentAnomaly.MISMATCH);
                }
            }
            case FAILED -> {
                if (!key.equals(attempt.getTransactionKey())) {
                    attempt.recordAnomaly(PaymentAnomaly.CONFLICTING_RESULT);
                }
            }
        }
    }

    // 거래 생성 여부를 모른다. 처음 발송한 직후에만 결과 미확인으로 남기고, 이미 나아간 상태는 되돌리지 않는다.
    private void applyUnknown(PaymentAttempt attempt) {
        switch (attempt.getStatus()) {
            case REQUESTED -> attempt.markUnknown();
            case UNKNOWN, PENDING, SUCCEEDED, FAILED -> { }
        }
    }

    // 거래가 없다는 확정 거절. 확보했던 재고를 한 번만 돌려준다. 같은 거절을 다시 적용해도 효과가 없고, 거래를 만들었다는
    // 다른 결과와 충돌하면 상태를 덮어쓰지 않고 이상 징후로만 남긴다.
    private void applyRejected(Order order, PaymentAttempt attempt) {
        switch (attempt.getStatus()) {
            case REQUESTED, UNKNOWN -> {
                attempt.markFailed(null);
                order.failPayment();
                orderStockHandler.restore(order);
            }
            case FAILED -> { }
            case PENDING, SUCCEEDED -> attempt.recordAnomaly(PaymentAnomaly.CONFLICTING_RESULT);
        }
    }
}

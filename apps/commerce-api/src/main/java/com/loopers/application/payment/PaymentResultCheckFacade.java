package com.loopers.application.payment;

import com.loopers.application.payment.PaymentGateway.PgPaymentStatusCheckResult;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentAttempt;
import com.loopers.domain.payment.PaymentAttemptService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 결제 결과 확인 유스케이스의 조합. <b>메소드에 트랜잭션을 열지 않는다.</b> PG 를 조회하기 전에는 짧은 읽기 전용 트랜잭션으로
 * 확인할 시도를 읽고, 조회한 사실은 별도 트랜잭션으로 반영한다. 한 메소드에 {@code @Transactional} 을 달면 HTTP 호출 동안
 * 연결과 재고 잠금을 쥐게 된다 (D-51).
 *
 * <p>결제를 시작하는 확정 요청은 주문의 유스케이스라 {@code OrderFacade.confirm} 이 맡는다.
 */
@Component
public class PaymentResultCheckFacade {

    private final PaymentGateway paymentGateway;
    private final PaymentResultApplier paymentResultApplier;
    private final OrderService orderService;
    private final PaymentAttemptService paymentAttemptService;
    private final TransactionTemplate readOnlyTransaction;
    private final TransactionTemplate writeTransaction;

    public PaymentResultCheckFacade(
        PaymentGateway paymentGateway,
        PaymentResultApplier paymentResultApplier,
        OrderService orderService,
        PaymentAttemptService paymentAttemptService,
        PlatformTransactionManager transactionManager
    ) {
        this.paymentGateway = paymentGateway;
        this.paymentResultApplier = paymentResultApplier;
        this.orderService = orderService;
        this.paymentAttemptService = paymentAttemptService;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 본인 주문의 결제 결과를 PG 에 다시 물어 반영한다. 알림이 오지 않았거나 응답을 잃었을 때의 기본 복구 경로다. 이미 종결된
     * 시도는 PG 를 부르지 않고 저장된 결과를 돌려준다. 거래 키를 알면 그 거래를, 모르면 PG 용 주문 식별자로 조회한다.
     * <b>create 는 다시 보내지 않는다.</b> 거래가 보이지 않아도 새 결제를 만들지 않는다.
     */
    public PaymentCheckInfo checkMyPaymentResult(Long userId, Long orderId) {
        PaymentAttemptInfo target = readOnlyTransaction.execute(status -> findMine(userId, orderId));
        return refresh(target, () -> target.transactionKey() != null
            ? paymentGateway.checkPaymentStatusByTransactionKey(target.transactionKey())
            : paymentGateway.checkPaymentStatusByOrder(target.providerOrderId()));
    }

    /**
     * PG 의 결과 알림을 받는다. 알림은 확인 요청의 신호일 뿐이다. 본문의 상태 문자열은 쓰지 않고, 알림이 가리키는 시도를
     * 찾은 뒤 알림의 거래 키로 PG 를 조회해 확인한 사실만 반영한다. 시도를 찾지 못하면 {@code NOT_FOUND} 이며 아무 효과도 없다.
     */
    public PaymentCheckInfo checkPaymentResultOnPgCallback(String providerOrderId, String transactionKey) {
        PaymentAttemptInfo target = readOnlyTransaction.execute(status -> findByProviderOrderId(providerOrderId));
        return refresh(target, () -> paymentGateway.checkPaymentStatusByTransactionKey(transactionKey));
    }

    // 이미 종결된 시도는 PG 를 다시 부르지 않는다. 묻지 못하면 저장된 상태를 그대로 돌려주며 아무것도 정하지 않는다.
    private PaymentCheckInfo refresh(PaymentAttemptInfo target, Supplier<PgPaymentStatusCheckResult> askPg) {
        if (target.settled()) {
            return new PaymentCheckInfo(target, false);
        }
        PgPaymentStatusCheckResult checkResult = askPg.get();
        if (checkResult.outcome() == PgPaymentStatusCheckResult.Outcome.COULD_NOT_CHECK) {
            return new PaymentCheckInfo(target, true);
        }
        PaymentAttemptInfo info = writeTransaction.execute(
            status -> paymentResultApplier.applyPaymentStatusCheckResult(target.orderId(), checkResult)
        );
        return new PaymentCheckInfo(info, false);
    }

    // 본인 주문의 시도를 읽는다. 없는 주문과 다른 사용자의 주문은 구분하지 않고 NOT_FOUND 이고, 결제를 시작하지 않은
    // 주문은 확인할 시도가 없으므로 CONFLICT 다.
    private PaymentAttemptInfo findMine(Long userId, Long orderId) {
        Order order = orderService.getMine(orderId, userId);
        PaymentAttempt attempt = paymentAttemptService.findByOrderId(order.getId()).orElseThrow(
            () -> new CoreException(ErrorType.CONFLICT, "결제를 시작한 주문이 아닙니다.")
        );
        return PaymentAttemptInfo.of(order, attempt);
    }

    // PG 알림이 가리키는 시도를 읽는다. 알림은 요청자가 없으므로 소유권 확인 없이 PG 용 주문 식별자로 찾는다.
    private PaymentAttemptInfo findByProviderOrderId(String providerOrderId) {
        PaymentAttempt attempt = paymentAttemptService.findByProviderOrderId(providerOrderId).orElseThrow(
            () -> new CoreException(ErrorType.NOT_FOUND, "결제 시도를 찾을 수 없습니다.")
        );
        return PaymentAttemptInfo.of(orderService.get(attempt.getOrderId()), attempt);
    }
}

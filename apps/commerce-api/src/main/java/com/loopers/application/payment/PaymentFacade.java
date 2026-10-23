package com.loopers.application.payment;

import com.loopers.application.payment.PaymentGateway.CreateResult;
import com.loopers.application.payment.PaymentGateway.OrderLookup;
import com.loopers.application.payment.PaymentGateway.TransactionLookup;
import com.loopers.application.payment.PaymentStarter.StartOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 결제 유스케이스의 조합. <b>트랜잭션을 열지 않는다.</b> 트랜잭션 A 를 끝내고 commit 한 뒤에 PG 를 호출하고, 그 결과를
 * 트랜잭션 B 로 반영한다. 한 메소드에 {@code @Transactional} 을 달면 HTTP 호출 동안 연결과 재고 잠금을 쥐게 된다 (D-51).
 *
 * <p>발송 기록(REQUESTED)을 commit 한 뒤에는 자동으로 create 를 다시 보내지 않는다. PG 호출 뒤 반영이 실패해 시도가
 * REQUESTED 로 남아도 같은 요청을 재발송하지 않고, 기존 거래를 조회로 확인한다 (D-48).
 */
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final PaymentStarter paymentStarter;
    private final PaymentGateway paymentGateway;
    private final PaymentResultApplier paymentResultApplier;
    private final PaymentQuery paymentQuery;

    /**
     * 본인의 DRAFT 주문의 PG 결제를 시작한다. 발송 권한을 얻은 실행만 PG 에 요청하고, 이미 시도가 있는 주문의 같은 키 요청은
     * 저장된 상태만 돌려준다. 결과 미확인이면 새 결제를 만들지 않고 미확인 상태를 돌려준다. 호출하는 쪽이 조회·확인 경로로
     * 이어 간다.
     */
    public PaymentAttemptInfo start(PaymentStartCommand command) {
        StartOutcome outcome = paymentStarter.start(command);
        if (!outcome.shouldSend()) {
            return outcome.info();
        }
        CreateResult result = paymentGateway.requestPayment(outcome.request());
        return paymentResultApplier.applyCreateResult(outcome.info().orderId(), result);
    }

    /**
     * 본인 주문의 결제 결과를 PG 에 다시 물어 반영한다. 알림이 오지 않았거나 응답을 잃었을 때의 기본 복구 경로다. 이미 종결된
     * 시도는 PG 를 부르지 않고 저장된 결과를 돌려준다. 거래 키를 알면 그 거래를, 모르면 PG 용 주문 식별자로 조회한다.
     * <b>create 는 다시 보내지 않는다.</b> 거래가 보이지 않아도 새 결제를 만들지 않는다.
     */
    public PaymentCheckResult reconcile(Long userId, Long orderId) {
        PaymentTarget target = paymentQuery.findMine(userId, orderId);
        return refresh(target, () -> target.transactionKey() != null
            ? observeByKey(target.transactionKey())
            : observeByOrder(target.providerOrderId()));
    }

    /**
     * PG 의 결과 알림을 받는다. 알림은 확인 요청의 신호일 뿐이다. 본문의 상태 문자열은 쓰지 않고, 알림이 가리키는 시도를
     * 찾은 뒤 알림의 거래 키로 PG 를 조회해 확인한 사실만 반영한다. 시도를 찾지 못하면 {@code NOT_FOUND} 이며 아무 효과도 없다.
     */
    public PaymentCheckResult handleCallback(String providerOrderId, String transactionKey) {
        PaymentTarget target = paymentQuery.findByProviderOrderId(providerOrderId);
        return refresh(target, () -> observeByKey(transactionKey));
    }

    // 이미 종결된 시도는 PG 를 다시 부르지 않는다. 조회하지 못하면 저장된 상태를 그대로 돌려주며 아무것도 정하지 않는다.
    private PaymentCheckResult refresh(PaymentTarget target, Supplier<PaymentObservation> observe) {
        if (target.settled()) {
            return new PaymentCheckResult(target.info(), false);
        }
        PaymentObservation observation = observe.get();
        if (observation instanceof PaymentObservation.Unavailable) {
            return new PaymentCheckResult(target.info(), true);
        }
        return new PaymentCheckResult(paymentResultApplier.apply(target.orderId(), observation), false);
    }

    private PaymentObservation observeByKey(String transactionKey) {
        return switch (paymentGateway.findTransaction(transactionKey)) {
            case TransactionLookup.Found found -> new PaymentObservation.Transaction(found.transaction());
            case TransactionLookup.NotFound notFound -> new PaymentObservation.TransactionNotFound();
            case TransactionLookup.Unavailable unavailable -> new PaymentObservation.Unavailable();
        };
    }

    private PaymentObservation observeByOrder(String providerOrderId) {
        return switch (paymentGateway.findTransactions(providerOrderId)) {
            case OrderLookup.Found found -> switch (found.transactions().size()) {
                case 0 -> new PaymentObservation.NoTransaction();
                case 1 -> new PaymentObservation.Transaction(found.transactions().get(0));
                default -> new PaymentObservation.MultipleTransactions(found.transactions().size());
            };
            case OrderLookup.Unavailable unavailable -> new PaymentObservation.Unavailable();
        };
    }
}

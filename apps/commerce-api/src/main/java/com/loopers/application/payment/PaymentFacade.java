package com.loopers.application.payment;

import com.loopers.application.payment.PaymentGateway.CreateResult;
import com.loopers.application.payment.PaymentStarter.StartOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
}

package com.loopers.infrastructure.payment;

import com.loopers.application.payment.PaymentGateway;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link PaymentGateway} 의 구현. {@link TestPgClient} 의 HTTP 결과를 포트의 결과 타입으로 옮긴다. 조회하지 못한 실패
 * ({@link PgUnavailableException}, {@link CallNotPermittedException})는 {@code COULD_NOT_CHECK} 로 바꾼다. 거래가 없다는
 * 뜻이 아니므로 {@code KEY_NOT_FOUND}·빈 목록과 섞지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentGatewayImpl implements PaymentGateway {
    private final TestPgClient client;

    @Override
    public PgPaymentRequestResult requestPayment(PaymentRequest request) {
        return client.create(request);
    }

    @Override
    public PgPaymentStatusCheckResult checkPaymentStatusByTransactionKey(String transactionKey) {
        try {
            return client.getTransaction(transactionKey)
                .map(transaction -> PgPaymentStatusCheckResult.answered(List.of(transaction)))
                .orElseGet(PgPaymentStatusCheckResult::keyNotFound);
        } catch (PgUnavailableException | CallNotPermittedException e) {
            log.warn("PG 거래 조회 불가 (transactionKey={}): {}", transactionKey, e.getMessage());
            return PgPaymentStatusCheckResult.couldNotCheck();
        }
    }

    @Override
    public PgPaymentStatusCheckResult checkPaymentStatusByOrder(String providerOrderId) {
        try {
            return PgPaymentStatusCheckResult.answered(client.getTransactions(providerOrderId));
        } catch (PgUnavailableException | CallNotPermittedException e) {
            log.warn("PG 주문별 거래 조회 불가 (providerOrderId={}): {}", providerOrderId, e.getMessage());
            return PgPaymentStatusCheckResult.couldNotCheck();
        }
    }
}

package com.loopers.infrastructure.payment;

import com.loopers.application.payment.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link PaymentGateway} 의 구현. {@link TestPgClient} 의 HTTP 결과를 포트의 결과 타입으로 옮긴다.
 *
 * <p>조회를 하지 못한 실패({@link PgUnavailableException})는 {@code Unavailable} 로 바꾼다. 거래가 없다는 뜻이 아니므로
 * {@code NotFound}·빈 목록과 섞지 않는다. 로그에는 카드 값을 남기지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentGatewayImpl implements PaymentGateway {
    private final TestPgClient client;

    @Override
    public CreateResult requestPayment(PaymentRequest request) {
        return client.create(request);
    }

    @Override
    public TransactionLookup findTransaction(String transactionKey) {
        try {
            return client.getTransaction(transactionKey)
                .<TransactionLookup>map(TransactionLookup.Found::new)
                .orElseGet(TransactionLookup.NotFound::new);
        } catch (PgUnavailableException e) {
            log.warn("PG 거래 조회 불가 (transactionKey={}): {}", transactionKey, e.getMessage());
            return new TransactionLookup.Unavailable();
        }
    }

    @Override
    public OrderLookup findTransactions(String providerOrderId) {
        try {
            return new OrderLookup.Found(client.getTransactions(providerOrderId));
        } catch (PgUnavailableException e) {
            log.warn("PG 주문별 거래 조회 불가 (providerOrderId={}): {}", providerOrderId, e.getMessage());
            return new OrderLookup.Unavailable();
        }
    }
}

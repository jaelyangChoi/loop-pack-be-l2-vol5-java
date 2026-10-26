package com.loopers.infrastructure.payment;

import com.loopers.application.payment.PaymentGateway;
import com.loopers.application.payment.pg.PgPaymentRequestCommand;
import com.loopers.application.payment.pg.PgPaymentRequestResult;
import com.loopers.application.payment.pg.PgPaymentStatusLookupResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/** {@link PaymentGateway} 의 구현. {@link PgHttpClient} 의 결과를 포트의 결과 타입으로 옮기고, 조회 실패는 {@code LOOKUP_UNAVAILABLE} 로 바꾼다. */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentGatewayImpl implements PaymentGateway {
    private final PgHttpClient client;

    @Override
    public PgPaymentRequestResult requestPayment(PgPaymentRequestCommand request) {
        return client.requestPayment(request);
    }

    @Override
    public PgPaymentStatusLookupResult lookupPaymentStatusByTransactionKey(String transactionKey) {
        return lookupOrUnavailable("transactionKey=" + transactionKey, () -> client.getTransactionByKey(transactionKey)
            .map(transaction -> PgPaymentStatusLookupResult.answered(List.of(transaction)))
            .orElseGet(PgPaymentStatusLookupResult::keyNotFound));
    }

    @Override
    public PgPaymentStatusLookupResult lookupPaymentStatusByOrder(String providerOrderId) {
        return lookupOrUnavailable("providerOrderId=" + providerOrderId,
            () -> PgPaymentStatusLookupResult.answered(client.getTransactionsByOrder(providerOrderId)));
    }

    private PgPaymentStatusLookupResult lookupOrUnavailable(String target, Supplier<PgPaymentStatusLookupResult> lookup) {
        try {
            return lookup.get();
        } catch (PgStatusLookupFailedException | CallNotPermittedException e) {
            log.warn("PG 결제 상태 조회 불가 ({}): {}", target, e.getMessage());
            return PgPaymentStatusLookupResult.lookupUnavailable();
        }
    }
}

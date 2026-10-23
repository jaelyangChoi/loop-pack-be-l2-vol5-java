package com.loopers.application.payment;

import com.loopers.application.payment.PaymentGateway.PgTransaction;

/**
 * PG 를 조회해서 알게 된 사실. 결과를 반영하는 곳({@link PaymentResultApplier#apply})은 입구가 무엇이든(고객의 확인,
 * PG 의 알림) 이 타입만 받는다. 알림 본문의 상태 문자열은 여기에 들어오지 못한다. 조회로 확인한 것만 들어온다.
 */
public sealed interface PaymentObservation {

    /** 거래 한 건을 확인했다. 시도와 식별·금액이 맞는지는 반영하는 쪽이 확인한다. */
    record Transaction(PgTransaction transaction) implements PaymentObservation {}

    /** 주문으로 조회했더니 거래가 없다. 전파 지연일 수 있어 거래가 없다는 증거가 아니다. */
    record NoTransaction() implements PaymentObservation {}

    /** 알고 있는 거래 키로 조회했는데 PG 가 모른다고 답했다. */
    record TransactionNotFound() implements PaymentObservation {}

    /** 같은 주문의 거래가 둘 이상이다. 하나를 임의로 고르지 않는다. */
    record MultipleTransactions(int count) implements PaymentObservation {}

    /** 조회하지 못했다(timeout, 5xx, 회로 차단 등). 아무것도 알게 되지 않았다. */
    record Unavailable() implements PaymentObservation {}
}

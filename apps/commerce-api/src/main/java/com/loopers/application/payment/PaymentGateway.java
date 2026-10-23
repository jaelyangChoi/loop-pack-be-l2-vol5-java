package com.loopers.application.payment;

import java.util.List;

/**
 * 외부 결제 시스템(PG)과의 경계. HTTP 상태·JSON·timeout 같은 전송 지식은 구현({@code infrastructure.payment})이 숨기고,
 * 이 인터페이스는 application 의 타입으로만 입출력한다.
 *
 * <p>결과 타입은 "무엇을 알게 됐는가"를 구분한다 (설계 2-3). {@link CreateResult.Unknown} 은 요청을 보냈지만 거래 생성 여부를
 * 모른다는 뜻이고, {@link CreateResult.RejectedNoEffect} 만 거래가 없다는 증거다. 조회를 못 한 것
 * ({@code Unavailable})과 거래가 없는 것({@code NotFound}, 빈 목록)도 서로 다른 사실이다.
 */
public interface PaymentGateway {

    /**
     * 결제 생성을 요청한다. 자동으로 재시도하지 않는다. 테스트 PG 는 멱등 키를 받지 않아 다시 보내면 거래가 또 생길 수 있다.
     * 예외를 던지지 않고 모든 결과를 {@link CreateResult} 로 돌려준다.
     */
    CreateResult requestPayment(PaymentRequest request);

    /** 거래 한 건을 조회한다. */
    TransactionLookup findTransaction(String transactionKey);

    /** 같은 주문의 거래 목록을 조회한다. 거래가 없으면 빈 목록이다. */
    OrderLookup findTransactions(String providerOrderId);

    /**
     * 결제 생성 요청. 금액은 저장된 주문 합계다. 카드 값은 합성 값만 쓰며 {@code toString} 에 드러내지 않는다.
     * 콜백 주소는 요청이 아니라 로컬 설정으로 고정한다.
     */
    record PaymentRequest(String providerOrderId, String cardType, String cardNo, long amount) {
        @Override
        public String toString() {
            return "PaymentRequest[providerOrderId=" + providerOrderId + ", cardType=" + cardType
                + ", cardNo=****, amount=" + amount + "]";
        }
    }

    /** PG 가 알려 주는 외부 거래 상태. */
    enum PgStatus {
        PENDING,
        SUCCESS,
        FAILED
    }

    /** PG 의 거래 한 건. {@code providerOrderId} 는 우리가 요청에 실어 보낸 주문 식별자다. */
    record PgTransaction(String transactionKey, String providerOrderId, long amount, PgStatus status) {}

    sealed interface CreateResult {
        /** 거래를 만들었다는 202 응답. 요청과 식별·금액이 일치하고 상태가 PENDING 일 때만 이 결과다. */
        record Accepted(PgTransaction transaction) implements CreateResult {}

        /** 거래를 저장하기 전에 확정 거절했다. 지금도 앞으로도 이 요청의 거래는 없다. */
        record RejectedNoEffect() implements CreateResult {}

        /** 그 밖의 모든 결과(timeout, 연결 끊김, 5xx, 해석할 수 없는 응답 등). 거래가 생겼는지 모른다. */
        record Unknown(String reason) implements CreateResult {}
    }

    sealed interface TransactionLookup {
        record Found(PgTransaction transaction) implements TransactionLookup {}

        /** PG 가 해당 거래 키를 모른다고 답했다. */
        record NotFound() implements TransactionLookup {}

        /** 조회하지 못했다(timeout, 5xx, 해석할 수 없는 응답 등). 거래의 유무에 대한 증거가 아니다. */
        record Unavailable() implements TransactionLookup {}
    }

    sealed interface OrderLookup {
        record Found(List<PgTransaction> transactions) implements OrderLookup {}

        /** 조회하지 못했다. 빈 목록(거래 없음)과 다르다. */
        record Unavailable() implements OrderLookup {}
    }
}

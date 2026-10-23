package com.loopers.application.payment;

import java.util.List;

/**
 * 외부 결제 시스템(PG)과의 경계. HTTP·JSON·timeout 같은 전송 지식은 구현({@code infrastructure.payment})이 숨기고,
 * application 의 타입으로만 입출력한다 (설계 2-3).
 */
public interface PaymentGateway {

    /** 결제를 요청한다. 자동으로 재시도하지 않고, 예외 없이 모든 결과를 {@link PgPaymentRequestResult} 로 돌려준다. */
    PgPaymentRequestResult requestPayment(PaymentRequest request);

    /** 거래 키로 PG 에 결제 상태를 묻는다. */
    PgPaymentStatusCheckResult checkPaymentStatusByTransactionKey(String transactionKey);

    /** 주문 식별자로 PG 에 결제 상태를 묻는다. 같은 주문의 거래가 0건 이상일 수 있다. */
    PgPaymentStatusCheckResult checkPaymentStatusByOrder(String providerOrderId);

    /** 결제 요청. 금액은 저장된 주문 합계다. 카드 값은 합성 값만 쓰며 {@code toString} 에 드러내지 않는다. */
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

    /** 결제 요청의 결과. {@code transaction} 은 {@code ACCEPTED} 일 때만 있다. */
    record PgPaymentRequestResult(Outcome outcome, PgTransaction transaction) {

        public enum Outcome {
            /** 거래를 만들었다(202). 요청과 식별·금액이 일치하고 PENDING 일 때만 이 결과다. */
            ACCEPTED,
            /** 거래를 저장하기 전에 확정 거절했다. 이 요청의 거래는 지금도 앞으로도 없다. */
            REJECTED_WITHOUT_TRANSACTION,
            /** 그 밖의 모든 결과(timeout, 연결 끊김, 5xx, 해석할 수 없는 응답). 거래가 생겼는지 모른다. */
            UNKNOWN
        }

        public static PgPaymentRequestResult accepted(PgTransaction transaction) {
            return new PgPaymentRequestResult(Outcome.ACCEPTED, transaction);
        }

        public static PgPaymentRequestResult rejectedWithoutTransaction() {
            return new PgPaymentRequestResult(Outcome.REJECTED_WITHOUT_TRANSACTION, null);
        }

        public static PgPaymentRequestResult unknown() {
            return new PgPaymentRequestResult(Outcome.UNKNOWN, null);
        }
    }

    /** PG 에 결제 상태를 물어본 결과. {@code transactions} 는 {@code ANSWERED} 일 때만 의미가 있다. */
    record PgPaymentStatusCheckResult(Outcome outcome, List<PgTransaction> transactions) {

        public enum Outcome {
            /** PG 가 답했다. 거래가 0건이어도 답이다(아직 안 보이는 것일 수 있어 거래가 없다는 증거는 아니다). */
            ANSWERED,
            /** 알고 있는 거래 키를 PG 가 모른다고 답했다. */
            KEY_NOT_FOUND,
            /** 묻지 못했다(timeout, 5xx, 회로 차단 등). 아무것도 알게 되지 않았다. */
            COULD_NOT_CHECK
        }

        public static PgPaymentStatusCheckResult answered(List<PgTransaction> transactions) {
            return new PgPaymentStatusCheckResult(Outcome.ANSWERED, List.copyOf(transactions));
        }

        public static PgPaymentStatusCheckResult keyNotFound() {
            return new PgPaymentStatusCheckResult(Outcome.KEY_NOT_FOUND, List.of());
        }

        public static PgPaymentStatusCheckResult couldNotCheck() {
            return new PgPaymentStatusCheckResult(Outcome.COULD_NOT_CHECK, List.of());
        }
    }
}

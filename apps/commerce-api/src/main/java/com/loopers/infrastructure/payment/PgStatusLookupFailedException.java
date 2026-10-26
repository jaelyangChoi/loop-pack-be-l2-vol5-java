package com.loopers.infrastructure.payment;

/**
 * PG 에 결제 상태를 묻지 못했다는 신호(timeout, 5xx, 해석할 수 없는 응답 등). 거래의 유무에 대한 증거가 아니다.
 * 재시도·회로 차단 대상이며, {@link PaymentGatewayImpl} 이 {@code LOOKUP_UNAVAILABLE} 결과로 바꿔서 돌려준다.
 */
public class PgStatusLookupFailedException extends RuntimeException {

    public PgStatusLookupFailedException(String message) {
        super(message);
    }

    public PgStatusLookupFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

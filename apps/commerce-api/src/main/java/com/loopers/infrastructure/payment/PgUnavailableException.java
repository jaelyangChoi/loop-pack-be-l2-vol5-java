package com.loopers.infrastructure.payment;

/**
 * PG 조회를 하지 못했다는 신호(timeout, 5xx, 해석할 수 없는 응답 등). 거래의 유무에 대한 증거가 아니다.
 *
 * <p>조회의 재시도·회로 차단 대상이 되는 실패이며, {@link PaymentGatewayImpl} 이 {@code Unavailable} 결과로 바꿔서 돌려준다.
 */
public class PgUnavailableException extends RuntimeException {

    public PgUnavailableException(String message) {
        super(message);
    }

    public PgUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

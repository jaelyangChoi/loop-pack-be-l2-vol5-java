package com.loopers.application.order.dto;

/** 주문을 결제한 수단. 이전에 포인트로 확정한 주문은 결제 시도가 없어 {@code POINT} 로 구분한다. */
public enum PaymentMethod {
    PG,
    POINT
}

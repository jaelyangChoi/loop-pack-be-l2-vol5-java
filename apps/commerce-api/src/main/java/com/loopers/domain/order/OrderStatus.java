package com.loopers.domain.order;

/** 주문 상태. 주문은 DRAFT 로 만들어지고, 결제를 마치면 CONFIRMED 가 된다. */
public enum OrderStatus {
    /** 확정 전. 재고와 포인트를 차감하지 않은 상태 */
    DRAFT,
    /** 결제를 마친 상태 */
    CONFIRMED
}

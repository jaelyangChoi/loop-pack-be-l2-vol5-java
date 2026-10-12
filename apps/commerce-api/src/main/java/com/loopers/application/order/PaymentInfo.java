package com.loopers.application.order;

import com.loopers.domain.order.PaymentResult;

/** 주문 확정 때의 결제 결과. 결제한 금액과 결제 뒤 남은 잔액이다. */
public record PaymentInfo(long paidAmount, long balanceAfter) {
    public static PaymentInfo from(PaymentResult result) {
        return new PaymentInfo(result.paidAmount().amount(), result.balanceAfter().amount());
    }
}

package com.loopers.application.payment;

import com.loopers.application.payment.PaymentGateway.PaymentRequest;

/**
 * 결제 준비(트랜잭션 A)의 결과. {@code request} 는 발송 권한을 얻은 실행에만 있고, 없으면 저장된 상태를 돌려주는 것이라
 * PG 를 호출하지 않는다.
 */
public record PaymentPreparation(PaymentAttemptInfo info, PaymentRequest request) {
    public boolean shouldSend() {
        return request != null;
    }
}

package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import jakarta.persistence.Embeddable;

/**
 * 주문 확정 때의 결제 결과. 결제한 금액과 결제 뒤 남은 포인트 잔액이다.
 *
 * <p>확정 시점에 함께 생기고 함께 읽히는 값이라 하나로 묶는다. DRAFT 주문에는 없다 (D-10).
 */
@Embeddable
public record PaymentResult(Money paidAmount, Money balanceAfter) {}

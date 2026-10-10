package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;

/**
 * 요청자(고객)를 나타내는 최소 Entity. 현재는 포인트 잔액만 가진다.
 *
 * <p>{@link PointBalance}는 불변이라 충전·결제가 새 값을 반환한다. 바깥에서는 값을 직접 다루지 않고
 * {@code chargePoint}, {@code payPoint}만 호출하며, 반환된 새 값으로 필드를 교체하는 책임은 이 클래스가 진다.
 * 거절되면 예외가 먼저 발생해 필드는 교체되지 않으므로 기존 잔액이 유지된다.
 */
public class User extends BaseEntity {

    private PointBalance pointBalance;

    public User() {
        this.pointBalance = new PointBalance(new Money(0));
    }

    public void chargePoint(Money amount) {
        this.pointBalance = this.pointBalance.charge(amount);
    }

    public void payPoint(Money amount) {
        this.pointBalance = this.pointBalance.pay(amount);
    }

    public PointBalance getPointBalance() {
        return pointBalance;
    }
}

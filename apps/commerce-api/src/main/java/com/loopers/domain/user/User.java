package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 요청자(고객)를 나타내는 최소 Entity. 현재는 포인트 잔액만 가진다.
 *
 * <p>{@link PointBalance}는 불변이라 충전·결제가 새 값을 반환한다. 바깥에서는 값을 직접 다루지 않고
 * {@code chargePoint}, {@code payPoint}만 호출하며, 반환된 새 값으로 필드를 교체하는 책임은 이 클래스가 진다.
 * 거절되면 예외가 먼저 발생해 필드는 교체되지 않으므로 기존 잔액이 유지된다.
 */
@Getter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Embedded
    @AttributeOverride(name = "balance.amount", column = @Column(name = "point_balance", nullable = false))
    private PointBalance pointBalance;

    // JPA 가 사용하는 기본 생성자. 새 사용자는 create() 로 만든다.
    protected User() {}

    public static User create() {
        User user = new User();
        user.pointBalance = new PointBalance(new Money(0));
        return user;
    }

    public void chargePoint(Money amount) {
        this.pointBalance = this.pointBalance.charge(amount);
    }

    public void payPoint(Money amount) {
        this.pointBalance = this.pointBalance.pay(amount);
    }
}

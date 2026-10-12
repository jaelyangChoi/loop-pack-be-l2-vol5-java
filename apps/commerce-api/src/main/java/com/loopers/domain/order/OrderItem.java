package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 주문에 담긴 품목 한 줄. 주문 당시의 상품명과 단가를 복사해 저장한 스냅샷이다.
 *
 * <p>상품이 나중에 수정되거나 삭제돼도 주문 내역은 바뀌지 않는다. 상품은 id 로만 참조하고,
 * {@link Order} 를 통해서만 만들고 저장된다.
 */
@Getter
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "unit_price", nullable = false))
    private Money unitPrice;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false))
    private Money amount;

    // JPA 가 사용하는 기본 생성자.
    protected OrderItem() {}

    OrderItem(Long productId, String productName, int quantity, Money unitPrice) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.amount = unitPrice.multiply(quantity);
    }
}

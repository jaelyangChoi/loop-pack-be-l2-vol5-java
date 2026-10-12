package com.loopers.domain.order;

import com.loopers.domain.common.Money;

/**
 * 주문 품목에 복사해 둘 상품의 값(이름·가격). 주문 당시의 스냅샷 원본이다.
 *
 * <p>{@link Order} 가 다른 Aggregate 인 상품 엔티티를 알지 못하도록, 주문을 만드는 쪽(application)이
 * 조회한 상품에서 필요한 값만 뽑아 넘긴다. 이름과 가격의 규칙은 상품이 이미 지키므로 여기서 다시 검증하지 않는다.
 */
public record OrderProduct(String name, Money price) {}

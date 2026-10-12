package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    private static final long MAX_PRICE = 1_000_000_000L;

    private OrderProduct product(String name, long price) {
        return new OrderProduct(name, new Money(price));
    }

    private OrderLines lines(OrderLine... lines) {
        return new OrderLines(List.of(lines));
    }

    private void assertErrorType(ErrorType expected, Runnable action) {
        CoreException exception = assertThrows(CoreException.class, action::run);
        assertThat(exception.getErrorType()).isEqualTo(expected);
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class Create {
        @DisplayName("DRAFT 상태이고, 품목마다 주문 당시 상품명·단가와 금액(단가 × 수량)을 가지며, 합계는 품목 금액의 합이다.")
        @Test
        void createsDraft_withSnapshotItemsAndTotal() {
            // arrange: 4-1 시나리오. 3,000 × 1 + 2,000 × 2 = 7,000
            Map<Long, OrderProduct> products = Map.of(
                10L, product("티셔츠", 3_000),
                20L, product("양말", 2_000)
            );

            // act
            Order order = Order.create(7L, lines(new OrderLine(10L, 1), new OrderLine(20L, 2)), products);

            // assert
            assertThat(order.getUserId()).isEqualTo(7L);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
            assertThat(order.getTotalAmount()).isEqualTo(new Money(7_000));
            assertThat(order.getItems()).hasSize(2);

            OrderItem first = order.getItems().get(0);
            assertThat(first.getProductId()).isEqualTo(10L);
            assertThat(first.getProductName()).isEqualTo("티셔츠");
            assertThat(first.getQuantity()).isEqualTo(1);
            assertThat(first.getUnitPrice()).isEqualTo(new Money(3_000));
            assertThat(first.getAmount()).isEqualTo(new Money(3_000));

            OrderItem second = order.getItems().get(1);
            assertThat(second.getProductId()).isEqualTo(20L);
            assertThat(second.getProductName()).isEqualTo("양말");
            assertThat(second.getQuantity()).isEqualTo(2);
            assertThat(second.getUnitPrice()).isEqualTo(new Money(2_000));
            assertThat(second.getAmount()).isEqualTo(new Money(4_000));
        }

        @DisplayName("요청자가 없으면 INVALID_INPUT 으로 거절한다.")
        @Test
        void throwsInvalidInput_whenUserIdIsNull() {
            assertErrorType(ErrorType.INVALID_INPUT, () ->
                Order.create(null, lines(new OrderLine(10L, 1)), Map.of(10L, product("티셔츠", 3_000))));
        }

        @DisplayName("품목에 해당하는 상품이 주어지지 않으면 호출 오류이므로 INTERNAL_ERROR 로 거절한다.")
        @Test
        void throwsInternalError_whenProductIsMissing() {
            assertErrorType(ErrorType.INTERNAL_ERROR, () ->
                Order.create(7L, lines(new OrderLine(10L, 1), new OrderLine(20L, 1)), Map.of(10L, product("티셔츠", 3_000))));
        }

        @DisplayName("합계가 금액 표현 범위를 넘으면 INVALID_INPUT 으로 거절한다.")
        @Test
        void throwsInvalidInput_whenTotalOverflows() {
            // arrange: 품목 하나는 최대 단가 × Integer.MAX_VALUE ≈ 2.1e18 로 long 범위 안이지만, 다섯 개를 더하면 9.2e18 을 넘는다.
            Map<Long, OrderProduct> products = Map.of(
                1L, product("가", MAX_PRICE),
                2L, product("나", MAX_PRICE),
                3L, product("다", MAX_PRICE),
                4L, product("라", MAX_PRICE),
                5L, product("마", MAX_PRICE)
            );
            OrderLines lines = lines(
                new OrderLine(1L, Integer.MAX_VALUE),
                new OrderLine(2L, Integer.MAX_VALUE),
                new OrderLine(3L, Integer.MAX_VALUE),
                new OrderLine(4L, Integer.MAX_VALUE),
                new OrderLine(5L, Integer.MAX_VALUE)
            );

            // act & assert
            assertErrorType(ErrorType.INVALID_INPUT, () -> Order.create(7L, lines, products));
        }
    }
}
